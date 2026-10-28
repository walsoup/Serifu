package app.serifu.coordinator

import android.content.Context
import android.util.TypedValue
import app.serifu.capture.BitmapRgb
import app.serifu.capture.CapturedFrame
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.translate.BubbleText
import app.serifu.core.translate.CharBudget
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.vision.ClusterConfig
import app.serifu.core.vision.ClusterOutcome
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.SpeechBubbleClusterer
import app.serifu.core.vision.VisionInputs
import app.serifu.core.model.QualityMode
import app.serifu.core.translate.EngineId
import app.serifu.ml.GemmaTranslationEngine
import app.serifu.ml.MlKitTranslationEngine
import app.serifu.ml.OcrEngine
import app.serifu.ml.VerticalRetiler
import app.serifu.overlay.LetteringRenderer
import app.serifu.overlay.RenderPage
import app.serifu.settings.Settings
import kotlin.math.roundToInt

class PagePipeline(
    context: Context,
    private val translator: MlKitTranslationEngine,
    private val renderer: LetteringRenderer,
    private val ocr: OcrEngine,
    private val retiler: VerticalRetiler = VerticalRetiler(),
    private val gemma: GemmaTranslationEngine? = null,
) {
    private val metrics = context.applicationContext.resources.displayMetrics

    data class Lettering(
        val page: RenderPage?,
        val clusterMs: Long,
        val translateMs: Long,
        val translateGemmaMs: Long,
        val layoutMs: Long,
        val outcome: ClusterOutcome?,
        /** Present in BEST_PROGRESSIVE when a Gemma upgrade is worth attempting. */
        val upgrade: (suspend () -> Upgrade?)?,
    )

    data class Upgrade(
        val page: RenderPage,
        val translateGemmaMs: Long,
        val source: EngineId,
    )

    suspend fun letter(
        lines: List<OcrLine>,
        frame: CapturedFrame,
        settings: Settings,
        pill: IntRect?,
        previousLines: List<String>,
        now: () -> Long,
    ): Lettering {
        val frameWidth = frame.bitmap.width
        val frameHeight = frame.bitmap.height
        val clusterStarted = now()
        val retiled = retiler.retile(lines, frame, settings.sourceLanguage, ocr)
        val config = ClusterConfig(
            source = settings.sourceLanguage,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            patchPadPx = dp(Tuning.PATCH_PAD_DP),
            skipLatin = settings.skipLatinText,
            pill = pill,
        )
        val vision = VisionInputs(
            full = frame.luma.full,
            quarter = frame.luma.quarter,
            rgb = BitmapRgb(frame.bitmap),
            refUnit = Tuning.refUnit(frameWidth, frameHeight),
        )
        val outcome = SpeechBubbleClusterer.clusterWithVision(retiled, config, vision)
        val clusters = outcome.clusters
        val clustered = now()
        if (clusters.isEmpty()) {
            return Lettering(null, clustered - clusterStarted, 0, 0, 0, outcome, null)
        }
        val request = PageTranslationRequest(
            source = settings.sourceLanguage,
            target = settings.targetLanguage,
            bubbles = clusters.map { cluster ->
                BubbleText(
                    id = cluster.id,
                    kind = cluster.kind,
                    text = cluster.sourceText,
                    charBudget = CharBudget.of(
                        cluster.letteringRect.width.toFloat(),
                        cluster.letteringRect.height.toFloat(),
                        metrics.density,
                    ),
                )
            },
            previousPageLines = previousLines.take(Tuning.PREVIOUS_PAGE_CONTEXT_LINES),
            keepHonorifics = settings.keepHonorifics,
        )
        val translated = translator.translatePage(request)
        val translatedAt = now()
        val useGemmaNow = settings.quality == QualityMode.BEST_ONLY && gemma != null
        if (useGemmaNow) {
            val upgraded = runGemmaUpgrade(
                request = request,
                mlKitById = translated.byId,
                clusters = clusters,
                settings = settings,
                frameW = frameWidth,
                frameH = frameHeight,
                rotation = frame.rotation,
                debugLines = retiled,
                outcome = outcome,
                now = now,
            )
            if (upgraded != null) {
                return Lettering(
                    page = upgraded.page.takeIf { it.bubbles.isNotEmpty() },
                    clusterMs = clustered - clusterStarted,
                    translateMs = translatedAt - clustered,
                    translateGemmaMs = upgraded.translateGemmaMs,
                    layoutMs = 0L,
                    outcome = outcome,
                    upgrade = null,
                )
            }
        }
        val page = renderer.build(
            clusters = clusters,
            translations = translated.byId,
            settings = settings,
            frameW = frameWidth,
            frameH = frameHeight,
            rotation = frame.rotation,
            debugLines = retiled,
            debugClusters = clusters,
            debugWalls = outcome.inkWalls,
            debugDropped = outcome.droppedFurigana,
        )
        val laidOut = now()
        val upgrade = if (settings.quality == QualityMode.BEST_PROGRESSIVE && gemma != null) {
            val snapshot = translated.byId.toMap()
            val task: suspend () -> Upgrade? = {
                runGemmaUpgrade(
                    request = request,
                    mlKitById = snapshot,
                    clusters = clusters,
                    settings = settings,
                    frameW = frameWidth,
                    frameH = frameHeight,
                    rotation = frame.rotation,
                    debugLines = retiled,
                    outcome = outcome,
                    now = now,
                )
            }
            task
        } else {
            null
        }
        return Lettering(
            page = page.takeIf { it.bubbles.isNotEmpty() },
            clusterMs = clustered - clusterStarted,
            translateMs = translatedAt - clustered,
            translateGemmaMs = 0L,
            layoutMs = laidOut - translatedAt,
            outcome = outcome,
            upgrade = upgrade,
        )
    }

    private suspend fun runGemmaUpgrade(
        request: PageTranslationRequest,
        mlKitById: Map<Int, String>,
        clusters: List<app.serifu.core.vision.SpeechBubbleCluster>,
        settings: Settings,
        frameW: Int,
        frameH: Int,
        rotation: Int,
        debugLines: List<OcrLine>,
        outcome: ClusterOutcome,
        now: () -> Long,
    ): Upgrade? {
        val engine = gemma ?: return null
        if (engine.availability(request.source, request.target) != EngineAvailability.Ready) return null
        val started = now()
        val result = try {
            engine.translateWithFallback(request, mlKitById)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (result.byId == mlKitById) return null
        val page = renderer.build(
            clusters = clusters,
            translations = result.byId,
            settings = settings,
            frameW = frameW,
            frameH = frameH,
            rotation = rotation,
            debugLines = debugLines,
            debugClusters = clusters,
            debugWalls = outcome.inkWalls,
            debugDropped = outcome.droppedFurigana,
        )
        if (page.bubbles.isEmpty()) return null
        return Upgrade(page, now() - started, result.engine)
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).roundToInt()
}
