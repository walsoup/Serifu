package app.walsoup.serifu.overlay

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.StaticLayout
import app.serifu.core.model.BubbleKind
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.SpeechBubbleCluster
import app.serifu.core.geometry.IntRect

class RenderBubble(
    val id: Int,
    val kind: BubbleKind,
    val patch: RectF,
    val patchCornerPx: Float,
    val fillColor: Int,
    val textColor: Int,
    val layout: StaticLayout,
    val layoutOrigin: PointF,
    val rotationDeg: Float,
    val seamless: Boolean,
    var outlineColor: Int,
    val sourceTextForDebug: String,
    val cleanPath: Path? = null,
)

class RenderPage(
    val bubbles: List<RenderBubble>,
    val frameW: Int,
    val frameH: Int,
    val rotation: Int,
    val sourceLines: List<String>,
    val debugLines: List<OcrLine>,
    val debugClusters: List<SpeechBubbleCluster> = emptyList(),
    val debugWalls: List<Pair<IntRect, IntRect>> = emptyList(),
    val debugDropped: List<OcrLine> = emptyList(),
)
