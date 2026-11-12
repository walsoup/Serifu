package app.walsoup.serifu.ml

import android.util.Log
import app.serifu.bergamot.BergamotNative
import app.serifu.core.model.ModelVariant
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.QualityMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.TranslationEngine

/**
 * Routes on-device translation to Bergamot when selected and ready, otherwise
 * ML Kit. [PageTranslationResult.engine] reports which backend actually ran.
 */
class OnDeviceTranslationEngine(
    private val mlKit: TranslationEngine,
    private val bergamot: BergamotTranslationEngine,
    private val models: BergamotModelManager,
    private val preferred: () -> OnDeviceEngine,
    private val nativeAvailable: () -> Boolean = { BergamotNative.available },
    private val variant: () -> ModelVariant = { ModelVariant.LITE },
    private val quality: () -> QualityMode = { QualityMode.FAST },
) : TranslationEngine {
    override val id: EngineId
        get() = when (preferred()) {
            OnDeviceEngine.BERGAMOT -> EngineId.BERGAMOT
            OnDeviceEngine.MLKIT -> EngineId.MLKIT
        }

    fun resolveEngineId(source: SourceLanguage, target: String): EngineId {
        return if (canUseBergamot(source, target)) EngineId.BERGAMOT else EngineId.MLKIT
    }

    fun canUseBergamot(source: SourceLanguage, target: String): Boolean {
        if (preferred() != OnDeviceEngine.BERGAMOT) return false
        if (!nativeAvailable()) return false
        val route = models.localRoute(source.tag, target, variant()) ?: return false
        return models.isRouteReady(route)
    }

    override suspend fun availability(source: SourceLanguage, target: String): EngineAvailability {
        return if (preferred() == OnDeviceEngine.BERGAMOT && nativeAvailable()) {
            when (val bergamotAvailability = bergamot.availability(source, target)) {
                is EngineAvailability.Ready -> bergamotAvailability
                else -> mlKit.availability(source, target)
            }
        } else {
            mlKit.availability(source, target)
        }
    }

    override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult {
        if (canUseBergamot(request.source, request.target)) {
            return try {
                bergamot.translatePage(request)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                Log.w(TAG, "Bergamot translation timed out, falling back to ML Kit", cancelled)
                mlKit.translatePage(request)
            } catch (t: Throwable) {
                Log.w(TAG, "Bergamot failed, falling back to ML Kit", t)
                mlKit.translatePage(request)
            }
        }
        if (preferred() == OnDeviceEngine.BERGAMOT) {
            Log.i(TAG, "Using ML Kit until Bergamot model is ready for ${request.source.tag}->${request.target}")
        }
        return mlKit.translatePage(request)
    }

    suspend fun prewarm(source: SourceLanguage, target: String) {
        if (quality() == QualityMode.BEST_ONLY) return
        if (preferred() == OnDeviceEngine.BERGAMOT && canUseBergamot(source, target)) {
            bergamot.prewarm(source, target)
        }
    }

    fun trimMemory() {
        bergamot.trimMemory()
    }

    override fun close() {
        bergamot.close()
        mlKit.close()
    }

    private companion object {
        const val TAG = "OnDeviceTranslate"
    }
}
