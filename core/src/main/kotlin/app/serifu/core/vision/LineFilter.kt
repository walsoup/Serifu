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
    // \p{P} (all Unicode punctuation), not ASCII-only \p{Punct}: lines of 。。。/！！！/、、、
    // must not survive to the translator. Runs on raw OCR text (NFKC happens later).
    private val punctuationOnly = Regex("^[\\p{P}\\s·・—–-]+$")
    private val latinOnly = Regex("^[A-Za-z0-9\\p{P}\\s·・—–-]+$")
    private val hasCjk = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")

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
            val cjkCount = hasCjk.findAll(text).count()
            if (skipLatin && (latinOnly.matches(foldFullwidth(text)) || cjkCount == 0 || (text.length >= 4 && cjkCount.toFloat() / text.length < 0.25f))) {
                return@mapNotNull null
            }
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
     * Folds fullwidth ASCII (U+FF01-U+FF5E) and the ideographic space to ASCII
     * before the Latin test. The filter sees raw OCR text; NFKC normalization
     * happens later for rendering.
     */
    private fun foldFullwidth(text: String): String {
        var folded: StringBuilder? = null
        for (i in text.indices) {
            val c = text[i]
            val code = c.code
            val mapped = when {
                code in 0xFF01..0xFF5E -> (code - 0xFEE0).toChar()
                code == 0x3000 -> ' '
                else -> c
            }
            if (mapped != c && folded == null) {
                folded = StringBuilder(text.length).append(text, 0, i)
            }
            folded?.append(mapped)
        }
        return folded?.toString() ?: text
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
