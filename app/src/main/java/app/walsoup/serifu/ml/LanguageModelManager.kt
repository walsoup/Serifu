package app.walsoup.serifu.ml

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    suspend fun refreshDownloaded() = withContext(Dispatchers.IO) {
        try {
            val models = RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
            _downloadedCodes.value = models.map { it.language }.toSet()
        } catch (_: Exception) {
        }
    }

    suspend fun downloadPacks(codes: Set<String>, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
        val manager = RemoteModelManager.getInstance()

        _inProgressCodes.value = _inProgressCodes.value + codes

        for (code in codes) {
            try {
                val model = TranslateRemoteModel.Builder(code).build()
                manager.download(model, conditions).await()
            } catch (t: Throwable) {
                android.util.Log.e("LanguageModelManager", "Failed downloading $code", t)
            } finally {
                _inProgressCodes.value = _inProgressCodes.value - code
            }
        }
        refreshDownloaded()
    }

    suspend fun deletePack(code: String) = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
        } catch (t: Throwable) {
            android.util.Log.e("LanguageModelManager", "Failed deleting $code", t)
        }
        refreshDownloaded()
    }

    suspend fun deletePacks(codes: Set<String>) = withContext(Dispatchers.IO) {
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
}
