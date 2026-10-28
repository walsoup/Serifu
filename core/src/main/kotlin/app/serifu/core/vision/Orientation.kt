package app.serifu.core.vision

import app.serifu.core.Tuning
import kotlin.math.min

enum class Orientation {
    VERTICAL,
    HORIZONTAL,
    UNKNOWN,
}

fun orientationOf(line: OcrLine): Orientation = when {
    line.text.codePointCount(0, line.text.length) <= 1 -> Orientation.UNKNOWN
    line.box.height > line.box.width * Tuning.VERTICAL_ASPECT -> Orientation.VERTICAL
    else -> Orientation.HORIZONTAL
}

fun charSizeOf(line: OcrLine, orientation: Orientation): Float = when (orientation) {
    Orientation.VERTICAL -> line.box.width.toFloat()
    Orientation.HORIZONTAL -> line.box.height.toFloat()
    Orientation.UNKNOWN -> min(line.box.width, line.box.height).toFloat()
}
