package app.serifu.coordinator

import android.content.Context
import android.util.TypedValue
import app.serifu.capture.BitmapRgb
import app.serifu.capture.CapturedFrame
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.translate.BubbleText
import app.serifu.core.translate.CharBudget
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.vision.ClusterConfig
import app.serifu.core.vision.ClusterOutcome
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.SpeechBubbleClusterer
import app.serifu.core.vision.VisionInputs
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
) {
    private val metrics = context.applicationContext.resources.displayMetrics

    data class Lettering(
        val page: RenderPage?,
        val clusterMs: Long,
        val translateMs: Long,
        val layoutMs: Long,
        val outcome: ClusterOutcome?,
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
            return Lettering(null, clustered - clusterStarted, 0, 0, outcome)
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
        return Lettering(
            page = page.takeIf { it.bubbles.isNotEmpty() },
            clusterMs = clustered - clusterStarted,
            translateMs = translatedAt - clustered,
            layoutMs = laidOut - translatedAt,
            outcome = outcome,
        )
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).roundToInt()
}
