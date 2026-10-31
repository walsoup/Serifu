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
            val slot = client(request.source, request.target)
            val conditions = DownloadConditions.Builder().apply {
                if (wifiOnly()) requireWifi()
            }.build()
            android.util.Log.d(
                "MlKitTranslate",
                "download ${request.source.tag}->${request.target} wifiOnly=${wifiOnly()}",
            )
            try {
                kotlinx.coroutines.withTimeout(Tuning.MLKIT_DOWNLOAD_TIMEOUT_MS) {
                    slot.translator.downloadModelIfNeeded(conditions).await()
                }
            } catch (timedOut: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.w(
                    "MlKitTranslate",
                    "download timed out after ${Tuning.MLKIT_DOWNLOAD_TIMEOUT_MS}ms " +
                        "(${request.source.tag}, wifiOnly=${wifiOnly()})",
                )
                throw TranslationUnavailable(request.source.englishName)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("MlKitTranslate", "downloadModelIfNeeded failed", t)
                throw TranslationUnavailable(request.source.englishName)
            }
            val fresh = ArrayList<Attempt>(pending.size)
            for (bubble in pending) {
                fresh.add(translateBubble(slot, bubble))
            }
            for (attempt in fresh) {
                if (attempt.text.isEmpty()) continue
                translated[attempt.id] = attempt.text
                if (!attempt.cacheable) continue
                val source = pending.first { it.id == attempt.id }.text
                cache.put(cache.key(id, request.target, cacheKey(request, source)), attempt.text)
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

    private suspend fun translateBubble(slot: Slot, bubble: app.serifu.core.translate.BubbleText): Attempt {
        val budget = if (slot.warm) Tuning.MLKIT_TRANSLATE_TIMEOUT_MS else Tuning.MLKIT_TRANSLATE_COLD_TIMEOUT_MS
        return try {
            val text = kotlinx.coroutines.withTimeoutOrNull(budget) {
                slot.translator.translate(bubble.text).await().trim()
            }
            if (text == null) {
                android.util.Log.w(
                    "MlKitTranslate",
                    "timeout ${budget}ms [${bubble.id}]: '${bubble.text}'",
                )
                Attempt(bubble.id, bubble.text, cacheable = false)
            } else {
                slot.warm = true
                slot.usedAt = clock()
                android.util.Log.d("MlKitTranslate", "Translated [${bubble.id}]: '${bubble.text}' -> '$text'")
                Attempt(bubble.id, text, cacheable = true)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            android.util.Log.w("MlKitTranslate", "Failed translating bubble ${bubble.id}: '${bubble.text}'", t)
            Attempt(bubble.id, bubble.text, cacheable = false)
        }
    }

    private fun client(source: SourceLanguage, target: String): Slot {
        val key = "${languageCode(source)}|${targetCode(target)}"
        val existing = slots[key]
        if (existing != null) {
            existing.usedAt = clock()
            return existing
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(languageCode(source))
            .setTargetLanguage(targetCode(target))
            .build()
        val created = Slot(Translation.getClient(options), clock())
        slots[key] = created
        return created
    }

    private fun cacheKey(request: PageTranslationRequest, text: String): String =
        "${request.source.tag}|$text"

    private fun romanize(text: String, source: SourceLanguage): String {
        val id = when (source) {
            SourceLanguage.JA -> "Katakana-Latin; Hiragana-Latin"
            SourceLanguage.KO -> "Hangul-Latin"
            SourceLanguage.ZH -> "Han-Latin"
            else -> null
        }
        if (id == null) return text.uppercase(Locale.ENGLISH)
        val latin = try {
            Transliterator.getInstance(id).transliterate(text)
        } catch (_: IllegalArgumentException) {
            text
        }
        return latin.uppercase(Locale.ENGLISH)
    }

    private fun languageCode(source: SourceLanguage): String =
        TranslateLanguage.fromLanguageTag(source.tag) ?: TranslateLanguage.ENGLISH

    private fun targetCode(tag: String): String =
        TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.ENGLISH

    private class Slot(val translator: Translator, var usedAt: Long, var warm: Boolean = false)

    private class Attempt(val id: Int, val text: String, val cacheable: Boolean)
}
