package app.walsoup.serifu.ml

import android.os.SystemClock
import android.util.Log
import app.serifu.bergamot.BergamotNative
import app.serifu.core.Tuning
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.ModelVariant
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.ModelFiles
import app.serifu.core.translate.ModelRoute
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.TranslationCache
import app.serifu.core.translate.TranslationEngine
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device Bergamot translation. One native batched call per page, model kept
 * warm on a dedicated thread, idle eviction after [Tuning.TRANSLATOR_IDLE_MS].
 */
open class BergamotTranslationEngine(
    private val models: BergamotModelManager,
    private val variant: () -> ModelVariant = { ModelVariant.LITE },
    private val cache: TranslationCache = TranslationCache(),
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val dispatcher: ExecutorCoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, THREAD_NAME).apply { isDaemon = true }
        }
        .asCoroutineDispatcher(),
) : TranslationEngine {
    override val id: EngineId = EngineId.BERGAMOT

    private var slot: Slot? = null
    private val closed = AtomicBoolean(false)

    override suspend fun availability(source: SourceLanguage, target: String): EngineAvailability {
        if (!BergamotNative.available) {
            return EngineAvailability.Unsupported("Bergamot native library failed to load")
        }
        val route = try {
            models.resolveRoute(source.tag, target, variant(), allowFetch = false)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "catalog unavailable", t)
            return EngineAvailability.NeedsDownload(null)
        } ?: return if (models.hasCatalogCache()) {
            EngineAvailability.Unsupported("No Bergamot model for ${source.tag}→$target")
        } else {
            EngineAvailability.NeedsDownload(null)
        }
        return if (models.isRouteReady(route)) {
            EngineAvailability.Ready
        } else {
            EngineAvailability.NeedsDownload(route.totalBytes)
        }
    }

    suspend fun prewarm(source: SourceLanguage, target: String) {
        if (!BergamotNative.available) return
        val route = models.resolveRoute(source.tag, target, variant(), allowFetch = false) ?: return
        if (!models.isRouteReady(route)) return
        withContext(dispatcher) {
            evictIdleLocked(clock())
            withTimeout(Tuning.BERGAMOT_LOAD_TIMEOUT_MS) {
                ensureSlot(route)
            }
        }
    }

    open override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult {
        if (!BergamotNative.available) {
            throw TranslationUnavailable("Bergamot")
        }
        val route = models.resolveRoute(request.source.tag, request.target, variant(), allowFetch = false)
            ?: throw TranslationUnavailable(request.source.englishName)
        if (!models.isRouteReady(route)) {
            throw TranslationUnavailable(request.source.englishName)
        }

        val translated = HashMap<Int, String>()
        val pending = ArrayList<app.serifu.core.translate.BubbleText>()
        for (bubble in request.bubbles) {
            if (bubble.text.isBlank()) continue
            if (bubble.kind == BubbleKind.SFX) {
                translated[bubble.id] = SfxRomanizer.romanize(bubble.text, request.source)
                continue
            }
            val cached = cache.get(cache.key(id, request.target, cacheKey(request, bubble.text)))
            if (cached != null) translated[bubble.id] = cached else pending.add(bubble)
        }
        if (pending.isEmpty()) {
            return PageTranslationResult(id, translated)
        }

        val outputs = withContext(dispatcher) {
            evictIdleLocked(clock())
            val active = withTimeout(Tuning.BERGAMOT_LOAD_TIMEOUT_MS) {
                ensureSlot(route)
            }
            val inputs = pending.map { it.text }.toTypedArray()
            withTimeout(Tuning.BERGAMOT_TRANSLATE_TIMEOUT_MS) {
                when (active) {
                    is Slot.Direct -> BergamotNative.translate(active.handle, inputs)
                    is Slot.Pivot -> BergamotNative.translatePivot(active.first, active.second, inputs)
                }
            }.also { active.usedAt = clock() }
        }
        if (outputs.size != pending.size) {
            throw IllegalStateException(
                "Bergamot returned ${outputs.size} translations for ${pending.size} inputs",
            )
        }

        for (index in pending.indices) {
            val bubble = pending[index]
            val text = outputs.getOrNull(index)?.trim().orEmpty()
            if (text.isEmpty()) continue
            translated[bubble.id] = text
            cache.put(cache.key(id, request.target, cacheKey(request, bubble.text)), text)
            if (app.walsoup.serifu.BuildConfig.DEBUG) {
                Log.d(TAG, "Translated [${bubble.id}]: '${bubble.text}' -> '$text'")
            } else {
                Log.d(TAG, "Translated [${bubble.id}]: ${bubble.text.length} chars -> ${text.length} chars")
            }
        }
        return PageTranslationResult(id, translated)
    }

    fun trimMemory() {
        try {
            dispatcher.asExecutor().execute {
                val current = slot
                slot = null
                try {
                    current?.free()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to free native slot on trim", t)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to dispatch trim to Bergamot dispatcher", t)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val release = {
            val current = slot
            slot = null
            try {
                current?.free()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to free native slot", t)
            }
        }
        if (Thread.currentThread().name == THREAD_NAME) {
            release()
            dispatcher.close()
        } else {
            try {
                dispatcher.asExecutor().execute {
                    try {
                        release()
                    } finally {
                        dispatcher.close()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to dispatch release to Bergamot dispatcher", t)
                dispatcher.close()
            }
        }
    }

    private fun ensureSlot(route: ModelRoute): Slot {
        val key = routeKey(route)
        val existing = slot
        if (existing != null && existing.key == key) {
            existing.usedAt = clock()
            return existing
        }
        existing?.free()
        slot = null
        val started = clock()
        val created = when (route) {
            is ModelRoute.Direct -> {
                val handle = loadModel(route.model)
                Slot.Direct(key, handle, clock())
            }
            is ModelRoute.Pivot -> {
                val first = loadModel(route.first)
                val second = try {
                    loadModel(route.second)
                } catch (t: Throwable) {
                    BergamotNative.free(first)
                    throw t
                }
                Slot.Pivot(key, first, second, clock())
            }
        }
        Log.d(TAG, "loaded $key in ${clock() - started}ms")
        slot = created
        return created
    }

    private fun loadModel(model: ModelFiles): Long {
        val paths = models.localPaths(model)
        val yaml = models.buildConfigYaml(paths)
        val handle = BergamotNative.load(yaml)
        if (handle == 0L) {
            throw IllegalStateException("Bergamot failed to load ${model.pairCode}/${model.architecture}")
        }
        return handle
    }

    private fun evictIdleLocked(now: Long) {
        val current = slot ?: return
        if (now - current.usedAt >= Tuning.TRANSLATOR_IDLE_MS) {
            current.free()
            slot = null
        }
    }

    private fun routeKey(route: ModelRoute): String = when (route) {
        is ModelRoute.Direct ->
            "${route.model.from}-${route.model.to}|${route.model.architecture}|${route.model.version}"
        is ModelRoute.Pivot ->
            "${route.first.from}-${route.first.to}|${route.first.architecture}+" +
                "${route.second.from}-${route.second.to}|${route.second.architecture}"
    }

    private fun cacheKey(request: PageTranslationRequest, text: String): String =
        "${request.source.tag}|${variant().name}|$text"

    private sealed class Slot {
        abstract val key: String
        abstract var usedAt: Long
        abstract fun free()

        class Direct(
            override val key: String,
            val handle: Long,
            override var usedAt: Long,
        ) : Slot() {
            override fun free() = BergamotNative.free(handle)
        }

        class Pivot(
            override val key: String,
            val first: Long,
            val second: Long,
            override var usedAt: Long,
        ) : Slot() {
            override fun free() {
                BergamotNative.free(first)
                BergamotNative.free(second)
            }
        }
    }

    private companion object {
        const val TAG = "BergamotTranslate"
        const val THREAD_NAME = "bergamot-translate"
    }
}
