package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import kotlin.math.abs
import kotlin.math.min

data class FilteredLine(
    val line: OcrLine,
    val orientation: Orientation,
    val charSize: Float,
    val rotated: Boolean,
)

object LineFilter {
    private val punctuationOnly = Regex("^[\\p{Punct}\\s]+$")
    private val latinOnly = Regex("^[A-Za-z0-9\\p{Punct}\\s]+$")

    fun keep(
        lines: List<OcrLine>,
        frameWidth: Int,
        frameHeight: Int,
        skipLatin: Boolean,
        pill: IntRect?,
    ): List<FilteredLine> {
        val minSize = Tuning.LINE_MIN_SIZE_REF_UNITS * Tuning.refUnit(frameWidth, frameHeight)
        return lines.mapNotNull { line ->
            val box = line.box
            if (min(box.width, box.height).toFloat() < minSize) return@mapNotNull null
            val text = line.text.trim()
            if (text.isEmpty() || punctuationOnly.matches(text)) return@mapNotNull null
            // A recognizer with no confidence score reports 0 for every line. Dropping those would erase the page.
            if (line.confidence > 0f && line.confidence < Tuning.LINE_MIN_CONFIDENCE) return@mapNotNull null
            if (skipLatin && latinOnly.matches(text)) return@mapNotNull null
            if (pill != null && !pill.isEmpty && pill.contains(box)) return@mapNotNull null
            val orientation = orientationOf(line)
            FilteredLine(
                line = line,
                orientation = orientation,
                charSize = charSizeOf(line, orientation),
                rotated = isOffAxis(line.angleDeg),
            )
        }
    }

    /**
     * True only for off-axis rotation (neither horizontal nor vertical).
     * A ~90° text angle is normal upright tategaki for Japanese/Chinese,
     * not rotated sound-effect lettering, so it must not route to SFX.
     */
    fun isOffAxis(angleDeg: Float): Boolean {
        val axis = abs(angleDeg) % 180f
        return axis > Tuning.ROTATED_ANGLE_DEG && abs(axis - 90f) > Tuning.ROTATED_ANGLE_DEG
    }
}
