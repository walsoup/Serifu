package app.walsoup.serifu.ml

import android.content.Context
import app.serifu.core.Tuning
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class DownloadErrorKind {
    WIFI_REQUIRED,
    TIMEOUT,
    NETWORK,
    VERIFICATION_FAILED,
}

data class DownloadError(
    val kind: DownloadErrorKind,
    val message: String,
)

data class LanguagePack(
    val code: String,
    val name: String,
    val nativeName: String,
    val isSource: Boolean,
    val sizeMb: Int = 30,
)

enum class PackDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

object LanguageModelManager {
    val SOURCE_PACKS = listOf(
        LanguagePack(TranslateLanguage.JAPANESE, "Japanese", "日本語", isSource = true),
        LanguagePack(TranslateLanguage.KOREAN, "Korean", "한국어", isSource = true),
        LanguagePack(TranslateLanguage.CHINESE, "Chinese", "中文", isSource = true),
        LanguagePack(TranslateLanguage.FRENCH, "French", "Français", isSource = true),
        LanguagePack(TranslateLanguage.SPANISH, "Spanish", "Español", isSource = true),
        LanguagePack(TranslateLanguage.GERMAN, "German", "Deutsch", isSource = true),
        LanguagePack(TranslateLanguage.ITALIAN, "Italian", "Italiano", isSource = true),
        LanguagePack(TranslateLanguage.PORTUGUESE, "Portuguese", "Português", isSource = true),
        LanguagePack(TranslateLanguage.ENGLISH, "English", "English", isSource = true),
    )

    val POPULAR_TARGET_PACKS = listOf(
        LanguagePack(TranslateLanguage.ENGLISH, "English", "English", isSource = false),
        LanguagePack(TranslateLanguage.SPANISH, "Spanish", "Español", isSource = false),
        LanguagePack(TranslateLanguage.FRENCH, "French", "Français", isSource = false),
        LanguagePack(TranslateLanguage.GERMAN, "German", "Deutsch", isSource = false),
        LanguagePack(TranslateLanguage.ITALIAN, "Italian", "Italiano", isSource = false),
        LanguagePack(TranslateLanguage.PORTUGUESE, "Portuguese", "Português", isSource = false),
        LanguagePack(TranslateLanguage.RUSSIAN, "Russian", "Русский", isSource = false),
        LanguagePack(TranslateLanguage.INDONESIAN, "Indonesian", "Bahasa Indonesia", isSource = false),
        LanguagePack(TranslateLanguage.VIETNAMESE, "Vietnamese", "Tiếng Việt", isSource = false),
        LanguagePack(TranslateLanguage.THAI, "Thai", "ไทย", isSource = false),
    )

    val ALL_PACKS = (SOURCE_PACKS + POPULAR_TARGET_PACKS).distinctBy { it.code }

    private val _downloadedCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadedCodes: StateFlow<Set<String>> = _downloadedCodes.asStateFlow()

    private val _inProgressCodes = MutableStateFlow<Set<String>>(emptySet())
    val inProgressCodes: StateFlow<Set<String>> = _inProgressCodes.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, DownloadError>>(emptyMap())
    val errors: StateFlow<Map<String, DownloadError>> = _errors.asStateFlow()

    private data class Inflight(val job: Job, val generation: Long)

    private val inflight = ConcurrentHashMap<String, Inflight>()
    private val generations = ConcurrentHashMap<String, Long>()
    private val nextGeneration = AtomicLong(0)
    private val recoveryEpoch = AtomicLong(0)
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var downloadTimeoutMs: Long = Tuning.MLKIT_DOWNLOAD_TIMEOUT_MS

    @Volatile
    internal var recoveryDelayMs: Long = RECOVERY_DELAY_MS

    @Volatile
    internal var recoveriesScheduled: Int = 0

    @Volatile
    internal var fetchPack: suspend (code: String, conditions: DownloadConditions) -> Unit = { code, conditions ->
        defaultFetch(code, conditions)
    }

    @Volatile
    internal var probeDownloaded: suspend () -> Set<String>? = { defaultProbe() }

    fun isOnWifi(context: Context): Boolean = NetworkStatus.isUnmetered(context)

    fun startDownload(
        scope: CoroutineScope,
        codes: Set<String>,
        wifiOnly: Boolean,
        context: Context,
    ): Job {
        val appContext = context.applicationContext
        if (codes.isEmpty()) return Job().apply { complete() }
        if (wifiOnly && !NetworkStatus.isUnmetered(appContext)) {
            cancelDownload(codes)
            val wifiError = DownloadError(DownloadErrorKind.WIFI_REQUIRED, "Wi-Fi required by settings")
            _errors.update { current -> current + codes.associateWith { wifiError } }
            return Job().apply { complete() }
        }
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
        return scope.launch {
            supervisorScope {
                for (code in codes) {
                    val generation = nextGeneration.incrementAndGet()
                    generations[code] = generation
                    val child = launch(start = CoroutineStart.LAZY) {
                        downloadOne(code, generation, conditions, appContext, wifiOnly)
                    }
                    val flight = Inflight(child, generation)
                    val previous = inflight.put(code, flight)
                    child.invokeOnCompletion {
                        inflight.remove(code, flight)
                    }
                    previous?.job?.cancel()
                    child.start()
                }
            }
        }
    }

    fun cancelDownload(codes: Set<String>) {
        if (codes.isEmpty()) return
        val cleared = HashSet<String>()
        for (code in codes) {
            val flight = inflight[code]
            val generation = flight?.generation ?: generations[code]
            if (generation != null && generations.remove(code, generation)) {
                if (flight != null) inflight.remove(code, flight)
                flight?.job?.cancel()
                cleared += code
            } else if (generation == null) {
                cleared += code
            }
        }
        if (cleared.isNotEmpty()) {
            _inProgressCodes.update { it - cleared }
        }
    }

    suspend fun refreshDownloaded() {
        try {
            val codes = probeDownloaded() ?: return
            _downloadedCodes.value = codes
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    suspend fun deletePack(code: String) = withContext(Dispatchers.IO) {
        _errors.update { it - code }
        try {
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
        } catch (t: Throwable) {
            android.util.Log.e("LanguageModelManager", "Failed deleting $code", t)
        }
        refreshDownloaded()
    }

    suspend fun deletePacks(codes: Set<String>) = withContext(Dispatchers.IO) {
        _errors.update { it - codes }
        val manager = RemoteModelManager.getInstance()
        for (code in codes) {
            try {
                val model = TranslateRemoteModel.Builder(code).build()
                manager.deleteDownloadedModel(model).await()
            } catch (t: Throwable) {
                android.util.Log.e("LanguageModelManager", "Failed deleting $code", t)
            }
        }
        refreshDownloaded()
    }

    internal suspend fun recoverTimedOutPack(code: String) {
        refreshDownloaded()
        if (code !in _downloadedCodes.value) return
        _errors.update { current ->
            if (current[code]?.kind == DownloadErrorKind.TIMEOUT) current - code else current
        }
    }

    internal fun resetForTest() {
        recoveryEpoch.incrementAndGet()
        inflight.values.forEach { it.job.cancel() }
        inflight.clear()
        generations.clear()
        _inProgressCodes.value = emptySet()
        _errors.value = emptyMap()
        _downloadedCodes.value = emptySet()
        downloadTimeoutMs = Tuning.MLKIT_DOWNLOAD_TIMEOUT_MS
        recoveryDelayMs = RECOVERY_DELAY_MS
        recoveriesScheduled = 0
        fetchPack = { code, conditions -> defaultFetch(code, conditions) }
        probeDownloaded = { defaultProbe() }
    }

    private suspend fun downloadOne(
        code: String,
        generation: Long,
        conditions: DownloadConditions,
        appContext: Context,
        wifiOnly: Boolean,
    ) {
        if (generations[code] != generation) return
        _errors.update { it - code }
        _inProgressCodes.update { current ->
            if (generations[code] == generation) current + code else current
        }
        if (generations[code] != generation) return
        try {
            try {
                withTimeout(downloadTimeoutMs) {
                    fetchPack(code, conditions)
                }
            } catch (timedOut: TimeoutCancellationException) {
                if (generations[code] != generation) throw timedOut
                android.util.Log.w("LanguageModelManager", "Timed out downloading $code", timedOut)
                val kind = if (wifiOnly && !NetworkStatus.isUnmetered(appContext)) {
                    DownloadErrorKind.WIFI_REQUIRED
                } else {
                    DownloadErrorKind.TIMEOUT
                }
                _errors.update { it + (code to DownloadError(kind, "Download timed out")) }
                if (kind == DownloadErrorKind.TIMEOUT) scheduleRecovery(code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generations[code] != generation) return
                android.util.Log.e("LanguageModelManager", "Failed downloading $code", t)
                val kind = if (wifiOnly && !NetworkStatus.isUnmetered(appContext)) {
                    DownloadErrorKind.WIFI_REQUIRED
                } else {
                    DownloadErrorKind.NETWORK
                }
                _errors.update { it + (code to DownloadError(kind, "Download failed")) }
            }
        } finally {
            if (generations.remove(code, generation)) {
                _inProgressCodes.update { it - code }
            }
            if (coroutineContext.isActive) {
                refreshDownloaded()
            }
        }
    }

    private fun scheduleRecovery(code: String) {
        recoveriesScheduled += 1
        val epoch = recoveryEpoch.get()
        val delayMs = recoveryDelayMs
        recoveryScope.launch {
            delay(delayMs)
            if (recoveryEpoch.get() != epoch) return@launch
            if (_errors.value[code]?.kind != DownloadErrorKind.TIMEOUT) return@launch
            recoverTimedOutPack(code)
        }
    }

    private suspend fun defaultFetch(code: String, conditions: DownloadConditions) {
        val model = TranslateRemoteModel.Builder(code).build()
        withContext(Dispatchers.IO) {
            RemoteModelManager.getInstance().download(model, conditions).await()
        }
    }

    private suspend fun defaultProbe(): Set<String>? = withContext(Dispatchers.IO) {
        try {
            val models = RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
            models.map { it.language }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    private const val RECOVERY_DELAY_MS = 5_000L
}
