package app.serifu.core.vision

import app.serifu.core.geometry.IntRect

data class OcrLine(
    val text: String,
    val box: IntRect,
    val confidence: Float,
    val angleDeg: Float,
    val language: String?,
    val symbolBoxes: List<IntRect>,
)
