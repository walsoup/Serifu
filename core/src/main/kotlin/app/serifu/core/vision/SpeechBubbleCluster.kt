package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RowSpans
import app.serifu.core.model.BubbleKind

data class SpeechBubbleCluster(
    val id: Int,
    val kind: BubbleKind,
    val sourceText: String,
    val cacheKey: String,
    val orientation: Orientation,
    val unionRect: IntRect,
    val patchRect: IntRect,
    val letteringRect: IntRect,
    val bubbleMask: RowSpans? = null,
    val fillColor: Int,
    val textColor: Int,
    val charSizePx: Float,
    val angleDeg: Float,
    val members: List<OcrLine>,
)
