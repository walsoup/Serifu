package app.serifu.ml

import android.icu.text.Transliterator
import android.os.SystemClock
import app.serifu.core.Tuning
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.TranslationCache
import app.serifu.core.translate.TranslationEngine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Locale

class TranslationUnavailable(val languageName: String) : RuntimeException(languageName)

/**
 * On-device ML Kit translation. Tasks cannot be cancelled; callers drop stale generations.
 * Sound effects are romanized instead of translated, because ML Kit turns them into noise.
 */
class MlKitTranslationEngine(
    private val cache: TranslationCache = TranslationCache(),
    private val wifiOnly: () -> Boolean = { true },
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : TranslationEngine {
    private val slots = HashMap<String, Slot>()

    override val id: EngineId = EngineId.MLKIT

    override suspend fun availability(source: SourceLanguage, target: String): EngineAvailability {
        val downloaded = RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
        val wanted = listOf(languageCode(source), targetCode(target))
        val present = downloaded.map { it.language }.toSet()
        return if (wanted.all { it in present }) {
            EngineAvailability.Ready
        } else {
            EngineAvailability.NeedsDownload(Tuning.MLKIT_PACK_APPROX_BYTES)
        }
    }

    override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult {
        evictIdle(clock())
        val translated = HashMap<Int, String>()
        val pending = ArrayList<app.serifu.core.translate.BubbleText>()
        for (bubble in request.bubbles) {
            if (bubble.text.isBlank()) continue
            if (bubble.kind == BubbleKind.SFX) {
                translated[bubble.id] = romanize(bubble.text, request.source)
                continue
            }
            val cached = cache.get(cache.key(id, request.target, cacheKey(request, bubble.text)))
            if (cached != null) translated[bubble.id] = cached else pending.add(bubble)
        }
        if (pending.isNotEmpty()) {
            val translator = client(request.source, request.target)
            val conditions = DownloadConditions.Builder().apply {
                if (wifiOnly()) requireWifi()
            }.build()
            try {
                translator.downloadModelIfNeeded(conditions).await()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                throw TranslationUnavailable(request.source.englishName)
            }
            val fresh = coroutineScope {
                pending.map { bubble ->
                    async {
                        val text = translator.translate(bubble.text).await().trim()
                        bubble.id to text
                    }
                }.awaitAll()
            }
            for ((bubbleId, text) in fresh) {
                if (text.isEmpty()) continue
                translated[bubbleId] = text
                val source = pending.first { it.id == bubbleId }.text
                cache.put(cache.key(id, request.target, cacheKey(request, source)), text)
            }
        }
        return PageTranslationResult(id, translated)
    }

    override fun close() {
        slots.values.forEach { it.translator.close() }
        slots.clear()
    }

    private fun evictIdle(now: Long) {
        val stale = slots.filterValues { now - it.usedAt >= Tuning.TRANSLATOR_IDLE_MS }.keys
        for (key in stale) slots.remove(key)?.translator?.close()
    }

    private fun client(source: SourceLanguage, target: String): Translator {
        val key = "${languageCode(source)}|${targetCode(target)}"
        val existing = slots[key]
        if (existing != null) {
            existing.usedAt = clock()
            return existing.translator
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(languageCode(source))
            .setTargetLanguage(targetCode(target))
            .build()
        val created = Translation.getClient(options)
        slots[key] = Slot(created, clock())
        return created
    }

    private fun cacheKey(request: PageTranslationRequest, text: String): String =
        "${request.source.tag}|$text"

    private fun romanize(text: String, source: SourceLanguage): String {
        val id = when (source) {
            SourceLanguage.JA -> "Katakana-Latin; Hiragana-Latin"
            SourceLanguage.KO -> "Hangul-Latin"
            SourceLanguage.ZH -> "Han-Latin"
        }
        val latin = try {
            Transliterator.getInstance(id).transliterate(text)
        } catch (_: IllegalArgumentException) {
            text
        }
        return latin.uppercase(Locale.ENGLISH)
    }

    private fun languageCode(source: SourceLanguage): String = when (source) {
        SourceLanguage.JA -> TranslateLanguage.JAPANESE
        SourceLanguage.ZH -> TranslateLanguage.CHINESE
        SourceLanguage.KO -> TranslateLanguage.KOREAN
    }

    private fun targetCode(tag: String): String =
        TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.ENGLISH

    private class Slot(val translator: Translator, var usedAt: Long)
}
