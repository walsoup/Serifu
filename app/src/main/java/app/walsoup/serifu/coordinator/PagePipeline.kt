package app.walsoup.serifu.coordinator

import android.content.Context
import android.util.TypedValue
import app.walsoup.serifu.capture.BitmapRgb
import app.walsoup.serifu.capture.CapturedFrame
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.translate.BubbleText
import app.serifu.core.translate.CharBudget
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.vision.ClusterConfig
import app.serifu.core.vision.ClusterOutcome
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.SpeechBubbleCluster
import app.serifu.core.vision.SpeechBubbleClusterer
import app.serifu.core.vision.VisionInputs
import app.walsoup.serifu.ml.MlKitTranslationEngine
import app.walsoup.serifu.ml.OcrEngine
import app.walsoup.serifu.ml.VerticalRetiler
import app.walsoup.serifu.ml.RemoteAiTranslationEngine
import app.walsoup.serifu.overlay.LetteringRenderer
import app.walsoup.serifu.overlay.RenderPage
import app.walsoup.serifu.settings.Settings
import kotlin.math.roundToInt

class PagePipeline(
    context: Context,
    private val translator: MlKitTranslationEngine,
    private val renderer: LetteringRenderer,
    private val ocr: OcrEngine,
    private val retiler: VerticalRetiler = VerticalRetiler(),
    private val remoteAi: RemoteAiTranslationEngine? = null,
) {
    private val metrics = context.applicationContext.resources.displayMetrics

    data class Lettering(
        val page: RenderPage?,
        val clusterMs: Long,
        val translateMs: Long,
        val translateGemmaMs: Long,
        val layoutMs: Long,
        val outcome: ClusterOutcome?,
        val request: PageTranslationRequest? = null,
        val clusters: List<SpeechBubbleCluster> = emptyList(),
        val retiled: List<OcrLine> = emptyList(),
    )

    fun hasAiConfigured(settings: Settings): Boolean =
        remoteAi != null && remoteAi.isConfigured(settings)

    suspend fun translateWithAi(
        request: PageTranslationRequest,
        clusters: List<SpeechBubbleCluster>,
        frameW: Int,
        frameH: Int,
        rotation: Int,
        retiled: List<OcrLine>,
        outcome: ClusterOutcome,
        settings: Settings,
    ): RenderPage? {
        val ai = remoteAi ?: return null
        val result = ai.translatePage(request)
        return renderer.build(
            clusters = clusters,
            translations = result.byId,
            settings = settings,
            frameW = frameW,
            frameH = frameH,
            rotation = rotation,
            debugLines = retiled,
            debugClusters = clusters,
            debugWalls = outcome.inkWalls,
            debugDropped = outcome.droppedFurigana,
        )
    }

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
            skipLatin = settings.skipLatinText && settings.sourceLanguage.isCjk,
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
        android.util.Log.d(
            "Serifu",
            "clustered ${clusters.size} bubbles in ${clustered - clusterStarted}ms",
        )
        if (clusters.isEmpty()) {
            return Lettering(null, clustered - clusterStarted, 0, 0, 0, outcome)
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

        val useAiOnly = settings.quality == app.serifu.core.model.QualityMode.BEST_ONLY && hasAiConfigured(settings)
        var translateMlKitMs = 0L
        var translateAiMs = 0L

        val translated = if (useAiOnly) {
            val aiStart = now()
            try {
                val res = remoteAi!!.translatePage(request)
                translateAiMs = now() - aiStart
                res
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                android.util.Log.w("Serifu", "Remote AI translation timed out in BEST_ONLY, falling back to ML Kit", cancelled)
                val mlStart = now()
                val res = translator.translatePage(request)
                translateMlKitMs = now() - mlStart
                res
            } catch (t: Throwable) {
                android.util.Log.w("Serifu", "Remote AI translation failed in BEST_ONLY, falling back to ML Kit", t)
                val mlStart = now()
                val res = translator.translatePage(request)
                translateMlKitMs = now() - mlStart
                res
            }
        } else {
            val mlStart = now()
            val res = translator.translatePage(request)
            translateMlKitMs = now() - mlStart
            res
        }

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
            translateMs = translateMlKitMs,
            translateGemmaMs = translateAiMs,
            layoutMs = laidOut - translatedAt,
            outcome = outcome,
            request = request,
            clusters = clusters,
            retiled = retiled,
        )
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).roundToInt()
}
