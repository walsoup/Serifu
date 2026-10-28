package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.RectMath

/**
 * Drops furigana readings before clustering (plan.md 10.6, Japanese only).
 *
 * A small kana-only line sitting beside a larger main line is a reading aid,
 * not dialogue. Merging it would duplicate the reading into the source text
 * and garble translation.
 */
object FuriganaFilter {
    private val kanaOnly = Regex("^[\u3040-\u30FF\u30FC]+$")

    data class Result(
        val kept: List<FilteredLine>,
        val dropped: List<FilteredLine>,
    )

    fun filter(lines: List<FilteredLine>): Result {
        if (lines.isEmpty()) return Result(lines, emptyList())
        val dropped = BooleanArray(lines.size)
        for (i in lines.indices) {
            val candidate = lines[i]
            if (!kanaOnly.matches(candidate.line.text.trim())) continue
            for (j in lines.indices) {
                if (i == j || dropped[j]) continue
                if (isFuriganaOf(candidate, lines[j])) {
                    dropped[i] = true
                    break
                }
            }
        }
        val kept = ArrayList<FilteredLine>(lines.size)
        val gone = ArrayList<FilteredLine>()
        for (i in lines.indices) {
            if (dropped[i]) gone.add(lines[i]) else kept.add(lines[i])
        }
        return Result(kept, gone)
    }

    private fun isFuriganaOf(f: FilteredLine, m: FilteredLine): Boolean {
        if (f.charSize >= Tuning.FURIGANA_SIZE_RATIO * m.charSize) return false
        val verticalMain = m.orientation == Orientation.VERTICAL ||
            (m.orientation == Orientation.UNKNOWN && f.orientation == Orientation.VERTICAL)
        return if (verticalMain) {
            if (f.orientation != Orientation.VERTICAL && f.orientation != Orientation.UNKNOWN) return false
            val gapX = RectMath.gapX(f.line.box, m.line.box)
            // Furigana sits right of the column it annotates.
            if (f.line.box.centerX < m.line.box.centerX) return false
            if (gapX < 0 || gapX >= Tuning.FURIGANA_GAP_FACTOR * m.charSize) return false
            val overlap = RectMath.overlapY(f.line.box, m.line.box).toFloat()
            overlap / f.line.box.height.toFloat() > Tuning.FURIGANA_OVERLAP
        } else {
            if (f.orientation != Orientation.HORIZONTAL && f.orientation != Orientation.UNKNOWN) return false
            // Horizontal equivalent: small kana directly above the main line.
            if (f.line.box.centerY > m.line.box.centerY) return false
            val gapY = RectMath.gapY(f.line.box, m.line.box)
            if (gapY < 0 || gapY >= Tuning.FURIGANA_GAP_FACTOR * m.charSize) return false
            val overlap = RectMath.overlapX(f.line.box, m.line.box).toFloat()
            overlap / f.line.box.width.toFloat() > Tuning.FURIGANA_OVERLAP
        }
    }
}
