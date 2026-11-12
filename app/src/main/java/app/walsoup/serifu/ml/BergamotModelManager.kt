package app.walsoup.serifu.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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

    private val _installed = MutableStateFlow<List<BergamotInstalledModel>>(emptyList())
    val installed: StateFlow<List<BergamotInstalledModel>> = _installed.asStateFlow()

    init {
        rootDir.mkdirs()
        refreshInstalled()
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
        when (route) {
            is ModelRoute.Direct -> downloadModel(route.model, wifiOnly)
            is ModelRoute.Pivot -> {
                downloadModel(route.first, wifiOnly)
                downloadModel(route.second, wifiOnly)
            }
        }
    }

    suspend fun downloadModel(model: ModelFiles, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        if (wifiOnly && !onWifi()) {
            throw IllegalStateException("Wi-Fi required for Bergamot downloads")
        }
        val key = storageKey(model.from, model.to, model.architecture)
        val lock = downloadLocks.computeIfAbsent(key) { Mutex() }
        lock.withLock {
            _inProgress.update { it + key }
            try {
                val dir = modelDir(model.from, model.to, model.architecture)
                dir.mkdirs()
                for (file in model.files) {
                    ensureFile(dir, file)
                }
                File(dir, "version.txt").writeText(model.version)
            } finally {
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
            totalBytes = files.sumOf { it.decompressedSize },
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

    private fun ensureFile(dir: File, file: ModelFile) {
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
            downloadTo(file.url, compressed)
            if (file.compressedHash.isNotBlank()) {
                val compressedDigest = sha256(compressed)
                if (!compressedDigest.equals(file.compressedHash, ignoreCase = true)) {
                    throw IllegalStateException(
                        "SHA-256 mismatch for ${file.name}.zst: expected ${file.compressedHash}, got $compressedDigest",
                    )
                }
            }
            decompressZstd(compressed, tmp, file.decompressedSize)
            val digest = sha256(tmp)
            if (!digest.equals(file.decompressedHash, ignoreCase = true)) {
                throw IllegalStateException(
                    "SHA-256 mismatch for ${file.name}: expected ${file.decompressedHash}, got $digest",
                )
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

    private fun downloadTo(url: String, dest: File) {
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
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decompressZstd(input: File, output: File, maxBytes: Long = 0L) {
        val maxAllowed = if (maxBytes > 0) (maxBytes * 1.1 + 4096).toLong() else Long.MAX_VALUE
        ZstdInputStream(FileInputStream(input)).use { zstd ->
            FileOutputStream(output).use { out ->
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                while (true) {
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

    private fun onWifi(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

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
