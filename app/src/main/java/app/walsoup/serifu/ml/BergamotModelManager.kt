package app.walsoup.serifu.ml

import android.content.Context
import android.util.Log
import app.serifu.core.Tuning
import app.serifu.core.model.ModelVariant
import app.serifu.core.translate.BergamotCatalog
import app.serifu.core.translate.Catalog
import app.serifu.core.translate.ModelFile
import app.serifu.core.translate.ModelFiles
import app.serifu.core.translate.ModelRoute
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class DownloadStage {
    CATALOG,
    DOWNLOADING,
    VERIFYING,
}

data class BergamotDownloadProgress(
    val key: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val stage: DownloadStage,
)

data class BergamotInstalledModel(
    val from: String,
    val to: String,
    val architecture: String,
    val version: String,
    val bytes: Long,
) {
    val pairCode: String get() = "$from-$to"
    val sizeMb: Int get() = ((bytes + 512L * 1024L) / (1024L * 1024L)).toInt().coerceAtLeast(1)
}

/**
 * Downloads Mozilla Firefox Translations models into app filesDir and tracks
 * which pairs are ready. The Remote Settings catalog is kept on disk so a
 * cold start can translate downloaded models with no network. Attachments
 * are zstd; compressed and decompressed bytes are checked against SHA-256.
 */
class BergamotModelManager(
    context: Context,
    private val fetchCatalogBody: (() -> String)? = null,
    private val fetchFileBytes: (suspend (String) -> ByteArray)? = null,
) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "bergamot")
    private val catalogFile = File(rootDir, "catalog.json")
    private val catalogMutex = Mutex()
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var cache: CatalogCache? = null

    private val _ready = MutableStateFlow<Set<String>>(emptySet())
    val readyKeys: StateFlow<Set<String>> = _ready.asStateFlow()

    private val _inProgress = MutableStateFlow<Set<String>>(emptySet())
    val inProgressKeys: StateFlow<Set<String>> = _inProgress.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, BergamotDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, BergamotDownloadProgress>> = _downloadProgress.asStateFlow()

    private val _downloadErrors = MutableStateFlow<Map<String, DownloadError>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, DownloadError>> = _downloadErrors.asStateFlow()

    private val activeRouteJobs = ConcurrentHashMap<String, Job>()

    private val _installed = MutableStateFlow<List<BergamotInstalledModel>>(emptyList())
    val installed: StateFlow<List<BergamotInstalledModel>> = _installed.asStateFlow()

    init {
        rootDir.mkdirs()
        refreshInstalled()
    }

    fun isOnWifi(): Boolean = onWifi()

    fun routeStorageKeys(route: ModelRoute): List<String> = when (route) {
        is ModelRoute.Direct -> listOf(storageKey(route.model.from, route.model.to, route.model.architecture))
        is ModelRoute.Pivot -> listOf(
            storageKey(route.first.from, route.first.to, route.first.architecture),
            storageKey(route.second.from, route.second.to, route.second.architecture),
        )
    }

    fun clearRouteErrors(route: ModelRoute) {
        val keys = routeStorageKeys(route).toSet()
        _downloadErrors.update { it - keys }
    }

    fun startDownloadRoute(scope: CoroutineScope, route: ModelRoute, wifiOnly: Boolean): Job {
        val routeId = routeStorageKeys(route).joinToString("+")
        activeRouteJobs[routeId]?.cancel()
        val job = scope.launch {
            try {
                downloadRoute(route, wifiOnly)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Errors are recorded in downloadErrors for the UI.
            }
        }
        activeRouteJobs[routeId] = job
        job.invokeOnCompletion { activeRouteJobs.remove(routeId, job) }
        return job
    }

    fun cancelRouteDownload(route: ModelRoute) {
        val routeId = routeStorageKeys(route).joinToString("+")
        activeRouteJobs.remove(routeId)?.cancel()
    }

    fun storageKey(from: String, to: String, architecture: String): String =
        "${BergamotCatalog.catalogLanguage(from)}-${BergamotCatalog.catalogLanguage(to)}|$architecture"

    fun modelDir(from: String, to: String, architecture: String): File {
        val safePair = "${BergamotCatalog.catalogLanguage(from)}-${BergamotCatalog.catalogLanguage(to)}"
        val safeArch = sanitizeSegment(architecture)
        val dir = File(rootDir, "$safePair/$safeArch")
        check(dir.canonicalPath.startsWith(rootDir.canonicalPath + File.separator)) {
            "Path traversal detected in modelDir: $safeArch"
        }
        return dir
    }

    private fun sanitizeSegment(segment: String): String {
        val trimmed = segment.trim()
        require(!trimmed.contains("..") && !trimmed.contains("/") && !trimmed.contains("\\")) {
            "Invalid path segment: $segment"
        }
        return trimmed
    }

    fun isReady(model: ModelFiles): Boolean {
        val dir = modelDir(model.from, model.to, model.architecture)
        if (!dir.isDirectory) return false
        return model.files.all { File(dir, it.name).isFile }
    }

    fun isRouteReady(route: ModelRoute): Boolean = when (route) {
        is ModelRoute.Direct -> isReady(route.model)
        is ModelRoute.Pivot -> isReady(route.first) && isReady(route.second)
    }

    fun hasCatalogCache(): Boolean = cache != null || catalogFile.isFile

    /**
     * @param allowFetch when false, never opens a socket. Translation uses this
     * so an offline phone can still run models that are already on disk.
     */
    suspend fun catalog(forceRefresh: Boolean = false, allowFetch: Boolean = true): Catalog {
        if (!forceRefresh) {
            freshCatalog()?.let { return it }
            if (cache == null) {
                withContext(Dispatchers.IO) { readDiskCatalog() }
                freshCatalog()?.let { return it }
            }
        }
        if (!allowFetch) {
            cache?.catalog?.let { return it }
            return withContext(Dispatchers.IO) { readDiskCatalog() }
                ?: throw IOException("Bergamot catalog is not cached")
        }
        return catalogMutex.withLock {
            if (!forceRefresh) {
                freshCatalog()?.let { return@withLock it }
            }
            try {
                val body = withContext(Dispatchers.IO) {
                    withTimeout(Tuning.BERGAMOT_DOWNLOAD_TIMEOUT_MS) {
                        fetchCatalogBody?.invoke() ?: fetchCatalogFromNetwork()
                    }
                }
                val parsed = BergamotCatalog.parse(body)
                try {
                    withContext(Dispatchers.IO) { persistCatalog(body) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to persist Bergamot catalog", t)
                }
                cache = CatalogCache(parsed, System.currentTimeMillis())
                parsed
            } catch (t: Throwable) {
                if (t is CancellationException && t !is TimeoutCancellationException) throw t
                val fallback = cache?.catalog ?: withContext(Dispatchers.IO) { readDiskCatalog() }
                if (fallback != null) {
                    Log.w(TAG, "Using cached Bergamot catalog", t)
                    fallback
                } else {
                    throw t
                }
            }
        }
    }

    suspend fun resolveRoute(
        fromTag: String,
        toTag: String,
        variant: ModelVariant,
        forceRefresh: Boolean = false,
        allowFetch: Boolean = true,
    ): ModelRoute? {
        var catalogError: Throwable? = null
        val fromCatalog = try {
            BergamotCatalog.resolveRoute(
                catalog(forceRefresh, allowFetch),
                fromTag,
                toTag,
                variant,
            )
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            Log.w(TAG, "Bergamot catalog unavailable", cancelled)
            catalogError = cancelled
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Bergamot catalog unavailable", t)
            catalogError = t
            null
        }
        if (fromCatalog != null && isRouteReady(fromCatalog)) return fromCatalog
        val local = localRoute(fromTag, toTag, variant)
        if (local != null && isRouteReady(local)) return local
        if (fromCatalog == null && local == null && catalogError != null) {
            // Translation must not block on or fail closed because the catalog
            // host is unreachable. Callers that passed allowFetch still surface it.
            if (!allowFetch) return null
            if (catalogError is TimeoutCancellationException) {
                throw IOException("Bergamot catalog timed out", catalogError)
            }
            throw catalogError
        }
        return fromCatalog ?: local
    }

    suspend fun downloadRoute(route: ModelRoute, wifiOnly: Boolean) {
        clearRouteErrors(route)
        when (route) {
            is ModelRoute.Direct -> downloadModel(route.model, wifiOnly)
            is ModelRoute.Pivot -> {
                downloadModel(route.first, wifiOnly)
                downloadModel(route.second, wifiOnly)
            }
        }
    }

    suspend fun downloadModel(model: ModelFiles, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        val key = storageKey(model.from, model.to, model.architecture)
        _downloadErrors.update { it - key }
        if (wifiOnly && !onWifi()) {
            val err = DownloadError(DownloadErrorKind.WIFI_REQUIRED, "Wi-Fi required for Bergamot downloads")
            _downloadErrors.update { it + (key to err) }
            throw IllegalStateException("Wi-Fi required for Bergamot downloads")
        }
        val lock = downloadLocks.computeIfAbsent(key) { Mutex() }
        lock.withLock {
            _inProgress.update { it + key }
            val expectedSizes = model.files.map { file -> file.transferBytes.coerceAtLeast(1L) }
            val totalBytes = expectedSizes.sum().coerceAtLeast(1L)
            var completedBytes = 0L
            try {
                val dir = modelDir(model.from, model.to, model.architecture)
                dir.mkdirs()
                model.files.forEachIndexed { index, file ->
                    coroutineContext.ensureActive()
                    val fileSize = expectedSizes[index]
                    val fileIndex = index + 1
                    val totalFiles = model.files.size
                    _downloadProgress.update {
                        it + (
                            key to BergamotDownloadProgress(
                                key = key,
                                downloadedBytes = completedBytes,
                                totalBytes = totalBytes,
                                currentFileIndex = fileIndex,
                                totalFiles = totalFiles,
                                stage = DownloadStage.DOWNLOADING,
                            )
                        )
                    }
                    ensureFile(
                        dir = dir,
                        file = file,
                        onBytes = { written ->
                            val currentTotal = (completedBytes + written.coerceAtMost(fileSize)).coerceAtMost(totalBytes)
                            _downloadProgress.update {
                                it + (
                                    key to BergamotDownloadProgress(
                                        key = key,
                                        downloadedBytes = currentTotal,
                                        totalBytes = totalBytes,
                                        currentFileIndex = fileIndex,
                                        totalFiles = totalFiles,
                                        stage = DownloadStage.DOWNLOADING,
                                    )
                                )
                            }
                        },
                        onVerifying = {
                            _downloadProgress.update {
                                it + (
                                    key to BergamotDownloadProgress(
                                        key = key,
                                        downloadedBytes = (completedBytes + fileSize).coerceAtMost(totalBytes),
                                        totalBytes = totalBytes,
                                        currentFileIndex = fileIndex,
                                        totalFiles = totalFiles,
                                        stage = DownloadStage.VERIFYING,
                                    )
                                )
                            }
                        },
                    )
                    completedBytes += fileSize
                }
                File(dir, "version.txt").writeText(model.version)
            } catch (timedOut: TimeoutCancellationException) {
                val err = DownloadError(DownloadErrorKind.TIMEOUT, "Download timed out")
                _downloadErrors.update { it + (key to err) }
                throw timedOut
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (timedOut: SocketTimeoutException) {
                val err = DownloadError(DownloadErrorKind.TIMEOUT, "Download timed out")
                _downloadErrors.update { it + (key to err) }
                throw timedOut
            } catch (verify: ModelVerificationException) {
                Log.w(TAG, "Verification failed for ${verify.fileName}")
                val err = DownloadError(DownloadErrorKind.VERIFICATION_FAILED, "Verification failed")
                _downloadErrors.update { it + (key to err) }
                throw verify
            } catch (t: Throwable) {
                val err = DownloadError(DownloadErrorKind.NETWORK, "Download failed")
                _downloadErrors.update { it + (key to err) }
                throw t
            } finally {
                _downloadProgress.update { it - key }
                _inProgress.update { it - key }
                refreshInstalled()
            }
        }
    }

    fun deletePair(from: String, to: String, architecture: String? = null) {
        val pairDir = File(rootDir, "${BergamotCatalog.catalogLanguage(from)}-${BergamotCatalog.catalogLanguage(to)}")
        if (architecture == null) {
            pairDir.deleteRecursively()
        } else {
            File(pairDir, architecture).deleteRecursively()
            if (pairDir.isDirectory && pairDir.list().isNullOrEmpty()) {
                pairDir.delete()
            }
        }
        refreshInstalled()
    }

    fun refreshInstalled() {
        if (!rootDir.isDirectory) {
            _ready.value = emptySet()
            _installed.value = emptyList()
            return
        }
        val installed = ArrayList<BergamotInstalledModel>()
        val ready = HashSet<String>()
        rootDir.listFiles()?.forEach { pairDir ->
            if (!pairDir.isDirectory) return@forEach
            val pair = parsePairFolder(pairDir.name) ?: return@forEach
            val (from, to) = pair
            pairDir.listFiles()?.forEach { archDir ->
                if (!archDir.isDirectory) return@forEach
                val files = archDir.listFiles()?.filter { it.isFile && it.name != "version.txt" }.orEmpty()
                if (files.none { fileTypeFromName(it.name) == "model" } ||
                    files.none { fileTypeFromName(it.name) == "lex" }) return@forEach
                val hasVocab = files.any { fileTypeFromName(it.name) == "vocab" } ||
                    (files.any { fileTypeFromName(it.name) == "srcvocab" } && files.any { fileTypeFromName(it.name) == "trgvocab" })
                if (!hasVocab) return@forEach
                val version = File(archDir, "version.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
                val bytes = files.sumOf { it.length() }
                installed += BergamotInstalledModel(from, to, archDir.name, version, bytes)
                ready += storageKey(from, to, archDir.name)
            }
        }
        _installed.value = installed.sortedWith(compareBy({ it.from }, { it.to }, { it.architecture }))
        _ready.value = ready
    }

    fun localPaths(model: ModelFiles): LocalModelPaths {
        val dir = modelDir(model.from, model.to, model.architecture)
        val byType = model.files.associateBy { it.fileType }
        val modelPath = File(dir, byType.getValue("model").name).absolutePath
        val shortlistPath = File(dir, byType.getValue("lex").name).absolutePath
        val shared = byType["vocab"]
        val src = byType["srcvocab"]
        val trg = byType["trgvocab"]
        val srcVocab = when {
            shared != null -> File(dir, shared.name).absolutePath
            src != null -> File(dir, src.name).absolutePath
            else -> error("missing vocab for ${model.pairCode}")
        }
        val trgVocab = when {
            shared != null -> File(dir, shared.name).absolutePath
            trg != null -> File(dir, trg.name).absolutePath
            else -> error("missing trg vocab for ${model.pairCode}")
        }
        return LocalModelPaths(modelPath, shortlistPath, srcVocab, trgVocab)
    }

    fun buildConfigYaml(paths: LocalModelPaths): String = buildString {
        appendLine("bergamot-mode: native")
        appendLine("models:")
        appendLine("  - ${paths.modelPath}")
        appendLine("vocabs:")
        appendLine("  - ${paths.srcVocabPath}")
        appendLine("  - ${paths.trgVocabPath}")
        appendLine("shortlist:")
        appendLine("  - ${paths.shortlistPath}")
        appendLine("  - false")
        appendLine("beam-size: ${Tuning.BERGAMOT_BEAM_SIZE}")
        appendLine("normalize: 1.0")
        appendLine("word-penalty: 0")
        appendLine("max-length-break: ${Tuning.BERGAMOT_MAX_LENGTH_BREAK}")
        appendLine("mini-batch-words: ${Tuning.BERGAMOT_MINI_BATCH_WORDS}")
        appendLine("workspace: ${Tuning.BERGAMOT_WORKSPACE_MB}")
        appendLine("max-length-factor: ${Tuning.BERGAMOT_MAX_LENGTH_FACTOR}")
        appendLine("skip-cost: true")
        appendLine("cpu-threads: 0")
        appendLine("quiet: true")
        appendLine("quiet-translation: true")
        appendLine("gemm-precision: ${Tuning.BERGAMOT_GEMM_PRECISION}")
        appendLine("alignment: soft")
    }

    private fun freshCatalog(): Catalog? {
        val cached = cache ?: return null
        val age = System.currentTimeMillis() - cached.fetchedAt
        return if (age < Tuning.BERGAMOT_CATALOG_CACHE_MS) cached.catalog else null
    }

    private fun readDiskCatalog(): Catalog? {
        if (!catalogFile.isFile) return null
        return try {
            val parsed = BergamotCatalog.parse(catalogFile.readText())
            cache = CatalogCache(parsed, catalogFile.lastModified())
            parsed
        } catch (t: Throwable) {
            Log.w(TAG, "Ignoring unreadable Bergamot catalog cache", t)
            null
        }
    }

    private fun persistCatalog(body: String) {
        rootDir.mkdirs()
        val tmp = File(rootDir, "catalog.json.tmp")
        tmp.writeText(body)
        if (catalogFile.exists() && !catalogFile.delete()) {
            tmp.delete()
            throw IOException("Failed to replace Bergamot catalog cache")
        }
        if (!tmp.renameTo(catalogFile)) {
            tmp.copyTo(catalogFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun fetchCatalogFromNetwork(): String {
        val connection = (URL(BergamotCatalog.RECORDS_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = Tuning.BERGAMOT_DOWNLOAD_TIMEOUT_MS.toInt()
            instanceFollowRedirects = true
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException(
                    "Failed to fetch Bergamot catalog: HTTP $responseCode ${connection.responseMessage.orEmpty()}".trim(),
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Rebuilds a route from files already in filesDir. File roles come from
     * Mozilla's names (`model*`, `lex*`, `vocab*`, `srcvocab*`, `trgvocab*`),
     * so translation does not need a catalog round-trip.
     */
    fun localRoute(fromTag: String, toTag: String, variant: ModelVariant): ModelRoute? {
        val from = BergamotCatalog.catalogLanguage(fromTag)
        val to = BergamotCatalog.catalogLanguage(toTag)
        if (from == to) return null
        directLocal(from, to, variant)?.let { return ModelRoute.Direct(it) }
        val pivot = Tuning.BERGAMOT_PIVOT_LANGUAGE
        if (from == pivot || to == pivot) return null
        val first = directLocal(from, pivot, variant) ?: return null
        val second = directLocal(pivot, to, variant) ?: return null
        return ModelRoute.Pivot(first, second)
    }

    private fun directLocal(from: String, to: String, variant: ModelVariant): ModelFiles? {
        val pairDir = File(rootDir, "$from-$to")
        if (!pairDir.isDirectory) return null
        val architectures = pairDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        val architecture = BergamotCatalog.resolveArchitecture(variant, architectures, allowFallback = false) ?: return null
        val dir = File(pairDir, architecture)
        val files = dir.listFiles()?.mapNotNull { file ->
            if (!file.isFile || file.name == "version.txt") return@mapNotNull null
            if (file.name.endsWith(".tmp") || file.name.endsWith(".download")) return@mapNotNull null
            val type = fileTypeFromName(file.name) ?: return@mapNotNull null
            ModelFile(
                name = file.name,
                fileType = type,
                location = "",
                compressedSize = 0L,
                compressedHash = "",
                decompressedSize = file.length(),
                decompressedHash = "",
            )
        }.orEmpty()
        if (files.none { it.fileType == "model" } || files.none { it.fileType == "lex" }) return null
        val hasVocab = files.any { it.fileType == "vocab" } ||
            (files.any { it.fileType == "srcvocab" } && files.any { it.fileType == "trgvocab" })
        if (!hasVocab) return null
        val version = File(dir, "version.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return ModelFiles(
            from = from,
            to = to,
            architecture = architecture,
            version = version,
            files = files,
            totalBytes = files.sumOf { it.transferBytes },
        )
    }

    /**
     * Pair folders are `from-to`. Tags such as `zh-Hans` already contain a
     * hyphen, so a single split would record Chinese as `zh` → `Hans-en`.
     */
    private fun parsePairFolder(name: String): Pair<String, String>? {
        val hyphenated = listOf("zh-Hans", "zh-Hant")
        for (tag in hyphenated) {
            val prefix = "$tag-"
            if (name.startsWith(prefix)) {
                val target = name.substring(prefix.length)
                if (target.isNotEmpty()) return tag to target
            }
            val suffix = "-$tag"
            if (name.endsWith(suffix)) {
                val from = name.substring(0, name.length - suffix.length)
                if (from.isNotEmpty()) return from to tag
            }
        }
        val parts = name.split("-", limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
        return parts[0] to parts[1]
    }

    private fun fileTypeFromName(name: String): String? = when {
        name.startsWith("srcvocab") -> "srcvocab"
        name.startsWith("trgvocab") -> "trgvocab"
        name.startsWith("vocab") -> "vocab"
        name.startsWith("model") -> "model"
        name.startsWith("lex") -> "lex"
        else -> null
    }

    private suspend fun ensureFile(
        dir: File,
        file: ModelFile,
        onBytes: (Long) -> Unit = {},
        onVerifying: () -> Unit = {},
    ) {
        val safeName = sanitizeSegment(file.name)
        val dest = File(dir, safeName)
        check(dest.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            "Path traversal detected in file name: ${file.name}"
        }
        if (dest.isFile && sha256(dest).equals(file.decompressedHash, ignoreCase = true)) {
            return
        }
        val tmp = File(dir, "$safeName.tmp")
        val compressed = File(dir, "$safeName.zst.download")
        try {
            downloadTo(file.url, compressed, onBytes)
            onVerifying()
            if (file.compressedHash.isNotBlank()) {
                val compressedDigest = sha256(compressed)
                if (!compressedDigest.equals(file.compressedHash, ignoreCase = true)) {
                    Log.w(
                        TAG,
                        "SHA-256 mismatch for ${file.name}.zst: expected ${file.compressedHash}, got $compressedDigest",
                    )
                    throw ModelVerificationException(file.name)
                }
            }
            decompressZstd(compressed, tmp, file.decompressedSize)
            val digest = sha256(tmp)
            if (!digest.equals(file.decompressedHash, ignoreCase = true)) {
                Log.w(TAG, "SHA-256 mismatch for ${file.name}: expected ${file.decompressedHash}, got $digest")
                throw ModelVerificationException(file.name)
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            compressed.delete()
            if (tmp.exists() && tmp != dest) tmp.delete()
        }
    }

    private suspend fun downloadTo(url: String, dest: File, onBytes: (Long) -> Unit = {}) {
        val injected = fetchFileBytes
        if (injected != null) {
            val bytes = injected(url)
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            onBytes(bytes.size.toLong())
            return
        }
        dest.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = Tuning.BERGAMOT_DOWNLOAD_TIMEOUT_MS.toInt()
            instanceFollowRedirects = true
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException(
                    "Failed to download $url: HTTP $responseCode ${connection.responseMessage.orEmpty()}".trim(),
                )
            }
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    var lastEmitAt = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= 100L) {
                            lastEmitAt = now
                            onBytes(total)
                        }
                    }
                    onBytes(total)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun decompressZstd(input: File, output: File, maxBytes: Long = 0L) {
        val maxAllowed = if (maxBytes > 0) (maxBytes * 1.1 + 4096).toLong() else Long.MAX_VALUE
        ZstdInputStream(FileInputStream(input)).use { zstd ->
            FileOutputStream(output).use { out ->
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = zstd.read(buffer)
                    if (read <= 0) break
                    totalRead += read
                    if (totalRead > maxAllowed) {
                        throw IllegalStateException(
                            "Decompressed size of ${output.name} exceeded expected limit ($totalRead > $maxAllowed)",
                        )
                    }
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun onWifi(): Boolean = NetworkStatus.isUnmetered(appContext)

    /**
     * Download progress for a route. Pivot routes add finished legs so the bar
     * does not jump back to zero when the second model starts.
     */
    fun routeProgress(
        route: ModelRoute,
        progress: Map<String, BergamotDownloadProgress>,
        readyKeys: Set<String>,
    ): BergamotDownloadProgress? {
        val legs = when (route) {
            is ModelRoute.Direct -> listOf(route.model)
            is ModelRoute.Pivot -> listOf(route.first, route.second)
        }
        val keys = routeStorageKeys(route)
        if (keys.size != legs.size || keys.none { progress[it] != null }) return null
        var downloaded = 0L
        var total = 0L
        var filesBefore = 0
        var totalFiles = 0
        var currentFile = 1
        var stage = DownloadStage.DOWNLOADING
        var activeKey = keys.first()
        legs.forEachIndexed { index, model ->
            val key = keys[index]
            val legBytes = model.transferBytes.coerceAtLeast(1L)
            val legFiles = model.files.size.coerceAtLeast(1)
            total += legBytes
            totalFiles += legFiles
            val current = progress[key]
            when {
                current != null -> {
                    downloaded += current.downloadedBytes.coerceAtMost(legBytes)
                    currentFile = (filesBefore + current.currentFileIndex).coerceAtLeast(1)
                    stage = current.stage
                    activeKey = key
                }
                key in readyKeys -> {
                    downloaded += legBytes
                    filesBefore += legFiles
                }
            }
        }
        val totalBytes = total.coerceAtLeast(1L)
        val files = totalFiles.coerceAtLeast(1)
        return BergamotDownloadProgress(
            key = activeKey,
            downloadedBytes = downloaded.coerceAtMost(totalBytes),
            totalBytes = totalBytes,
            currentFileIndex = currentFile.coerceIn(1, files),
            totalFiles = files,
            stage = stage,
        )
    }

    private class ModelVerificationException(val fileName: String) : Exception()

    private class CatalogCache(val catalog: Catalog, val fetchedAt: Long)

    private companion object {
        const val TAG = "BergamotModels"
    }

    data class LocalModelPaths(
        val modelPath: String,
        val shortlistPath: String,
        val srcVocabPath: String,
        val trgVocabPath: String,
    )
}
