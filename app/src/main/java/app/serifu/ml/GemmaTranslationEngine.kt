package app.serifu.ml

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import app.serifu.core.Tuning
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.LlmResponseParser
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.PromptBuilder
import app.serifu.core.translate.TranslationCache
import app.serifu.core.translate.TranslationEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * High-quality on-device LLM tier (plan.md 11.4).
 * Gemma 4 E2B instruction-tuned running in-process on LiteRT-LM.
 *
 * - Loads lazily on the dedicated `serifu-llm` thread, GPU first with one
 *   CPU retry, and the working choice is remembered per device.
 * - One request at a time behind a [Mutex]; a fresh conversation per page.
 * - Hard 12 s timeout per page; callers merge missing ids with ML Kit.
 * - Bubble results go to [TranslationCache] under GEMMA4_E2B; full pages
 *   are also cached under `hash(ordered cacheKeys + target)` plus an
 *   optional JSON disk cache in `cacheDir/llm/` (20 MB LRU cap).
 */
class GemmaTranslationEngine(
    context: Context,
    private val cache: TranslationCache = TranslationCache(),
    private val diskCacheEnabled: () -> Boolean = { true },
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : TranslationEngine {
    private val appContext = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "serifu-llm").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private val diskCache = LlmDiskCache(File(appContext.cacheDir, "llm"))

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var lastUsedAt: Long = 0L

    override val id: EngineId = EngineId.GEMMA4_E2B

    override suspend fun availability(source: app.serifu.core.model.SourceLanguage, target: String): EngineAvailability {
        val gate = DeviceCapability.check(appContext)
        if (!gate.supported) return EngineAvailability.Unsupported(gate.reason ?: "This device cannot run Gemma.")
        if (!ModelCatalog.isComplete(appContext)) {
            return EngineAvailability.NeedsDownload(ModelCatalog.EXPECTED_BYTES)
        }
        return EngineAvailability.Ready
    }

    override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult =
        translateWithFallback(request, emptyMap())

    /**
     * Translates [request], filling any missing or empty LLM ids from
     * [fallbackById] (the ML Kit results for the same page, per 11.5).
     */
    suspend fun translateWithFallback(
        request: PageTranslationRequest,
        fallbackById: Map<Int, String>,
    ): PageTranslationResult {
        if (request.bubbles.isEmpty()) return PageTranslationResult(id, fallbackById)
        val diskKey = pageKey(request)
        val cached = cache.get(cache.key(id, request.target, diskKey))
        if (cached != null) {
            val restored = diskCache.get(diskKey)
            if (restored != null) return PageTranslationResult(id, merged(request, restored, fallbackById))
        }
        val started = clock()
        val raw = withTimeout(Tuning.GEMMA_PAGE_TIMEOUT_MS) {
            mutex.withLock {
                withContext(dispatcher) {
                    val active = ensureLoaded()
                    val prompt = PromptBuilder.build(request)
                    active.createConversation(conversationConfig()).use { conversation ->
                        conversation.sendMessage(prompt).toString()
                    }
                }
            }
        }
        lastUsedAt = clock()
        val decoded = LlmResponseParser.parse(raw, fallbackById)
        val final = merged(request, decoded, fallbackById)
        for ((bubbleId, text) in final) {
            val source = request.bubbles.firstOrNull { it.id == bubbleId }?.text ?: continue
            cache.put(cache.key(id, request.target, cacheKey(request, source)), text)
        }
        cache.put(cache.key(id, request.target, diskKey), PRESENT)
        if (diskCacheEnabled()) diskCache.put(diskKey, final)
        lastUsedAt = clock()
        return PageTranslationResult(id, final)
    }

    /** Copies a user-picked `.litertlm` file into `filesDir/models/` (SAF import, 11.4). */
    fun importSafFile(uri: Uri): Boolean {
        val dest = ModelCatalog.modelFile(appContext)
        val tmp = File(dest.parent, dest.name + ".import")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, BUFFER) }
            } ?: return false
            if (tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            recordSha(tmp)
            if (dest.exists() && !dest.delete()) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return false
            }
            return true
        } catch (_: Exception) {
            tmp.delete()
            return false
        }
    }

    /** Drops the loaded model; called on trim-memory and after 3 min idle. */
    fun unloadNow() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    fun unloadIfIdle(now: Long = clock()) {
        if (engine != null && now - lastUsedAt >= Tuning.GEMMA_UNLOAD_IDLE_MS) unloadNow()
    }

    override fun close() {
        unloadNow()
        try {
            dispatcher.close()
        } catch (_: Exception) {
        }
    }

    private fun ensureLoaded(): Engine {
        engine?.let { return it }
        val model = ModelCatalog.modelFile(appContext)
        verifyModelFile(model)
        val preference = backendPreference()
        val order = if (preference == BACKEND_CPU) listOf(BACKEND_CPU) else listOf(BACKEND_GPU, BACKEND_CPU)
        var failure: Exception? = null
        for (choice in order) {
            try {
                val created = Engine(
                    EngineConfig(
                        modelPath = model.path,
                        backend = if (choice == BACKEND_CPU) Backend.CPU() else Backend.GPU(),
                        cacheDir = appContext.cacheDir.path,
                    ),
                )
                created.initialize()
                saveBackendPreference(choice)
                engine = created
                lastUsedAt = clock()
                return created
            } catch (e: Exception) {
                failure = e
                if (choice == BACKEND_CPU) break
            }
        }
        throw failure ?: IllegalStateException("Gemma engine failed to load")
    }

    private fun verifyModelFile(model: File) {
        if (!model.isFile || model.length() != ModelCatalog.EXPECTED_BYTES) {
            throw ModelNotReady("Gemma model is missing or incomplete.")
        }
        val recorded = readRecordedSha()
        if (recorded != null) {
            val actual = sha256Hex(model)
            if (!actual.equals(recorded, ignoreCase = true)) {
                throw ModelNotReady("Gemma model hash mismatch.")
            }
        }
    }

    private fun conversationConfig(): ConversationConfig = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = Tuning.GEMMA_TOP_K,
            topP = Tuning.GEMMA_TOP_P,
            temperature = Tuning.GEMMA_TEMPERATURE,
        ),
    )

    private fun merged(
        request: PageTranslationRequest,
        decoded: Map<Int, String>,
        fallbackById: Map<Int, String>,
    ): Map<Int, String> {
        val out = HashMap<Int, String>()
        for (bubble in request.bubbles) {
            val text = decoded[bubble.id] ?: fallbackById[bubble.id]
            if (!text.isNullOrBlank()) out[bubble.id] = text
        }
        return out
    }

    private fun pageKey(request: PageTranslationRequest): String {
        val ordered = request.bubbles.sortedBy { it.id }
            .joinToString("|") { "${it.kind.name}:${it.text}" }
        return "page|${request.source.tag}|${request.target}|$ordered"
    }

    private fun cacheKey(request: PageTranslationRequest, text: String): String =
        "${request.source.tag}|$text"

    private fun backendPreference(): String =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND, BACKEND_GPU) ?: BACKEND_GPU

    private fun saveBackendPreference(choice: String) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, choice).apply()
    }

    private fun readRecordedSha(): String? = try {
        ModelCatalog.shaFile(appContext).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    internal fun recordSha(file: File) {
        try {
            ModelCatalog.shaFile(appContext).writeText(sha256Hex(file))
        } catch (_: Exception) {
        }
    }

    internal fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS = "gemma"
        const val KEY_BACKEND = "backend"
        const val BACKEND_GPU = "gpu"
        const val BACKEND_CPU = "cpu"
        const val PRESENT = "1"
        const val BUFFER = 1024 * 1024
    }
}

class ModelNotReady(message: String) : RuntimeException(message)

/** JSON disk cache in `cacheDir/llm/`, 20 MB cap, LRU by mtime (plan.md 11.4). */
internal class LlmDiskCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(val values: Map<String, String>)

    fun get(pageKey: String): Map<Int, String>? {
        val file = fileFor(pageKey)
        if (!file.isFile) return null
        return try {
            file.setLastModified(System.currentTimeMillis())
            val entry = json.decodeFromString(Entry.serializer(), file.readText())
            entry.values.mapNotNull { (key, value) ->
                key.toIntOrNull()?.let { it to value }
            }.toMap().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun put(pageKey: String, values: Map<Int, String>) {
        if (values.isEmpty()) return
        try {
            dir.mkdirs()
            val entry = Entry(values.mapKeys { it.key.toString() })
            fileFor(pageKey).writeText(json.encodeToString(Entry.serializer(), entry))
            trim()
        } catch (_: Exception) {
        }
    }

    private fun fileFor(pageKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val hex = digest.digest(pageKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.json")
    }

    private fun trim() {
        try {
            var total = 0L
            val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
            for (file in files) total += file.length()
            for (file in files) {
                if (total <= Tuning.LLM_DISK_CACHE_MAX_BYTES) break
                total -= file.length()
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
