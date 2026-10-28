package app.serifu.core.change

import app.serifu.core.Tuning
import app.serifu.core.image.LumaImage
import kotlin.math.max

/**
 * Page fingerprint (plan.md 9.1): the 1/16-scale luma box-averaged to a
 * 32 x 56 grid, with status/navigation bar rows masked out. The clock,
 * battery icon and reader page counters never affect the distance.
 */
data class FrameFingerprint(
    val cells: ByteArray,
    val cols: Int = Tuning.FINGERPRINT_COLS,
    val rows: Int = Tuning.FINGERPRINT_ROWS,
    val topMaskRows: Int,
    val bottomMaskRows: Int,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    fun distance(other: FrameFingerprint): Float {
        if (cols != other.cols || rows != other.rows) return Float.MAX_VALUE
        var sum = 0L
        var count = 0
        for (row in 0 until rows) {
            if (row < topMaskRows || row >= rows - bottomMaskRows) continue
            if (row < other.topMaskRows || row >= rows - other.bottomMaskRows) continue
            for (col in 0 until cols) {
                val a = cells[row * cols + col].toInt() and 0xFF
                val b = other.cells[row * cols + col].toInt() and 0xFF
                sum += kotlin.math.abs(a - b)
                count++
            }
        }
        if (count == 0) return Float.MAX_VALUE
        return sum.toFloat() / count
    }

    fun isSamePage(other: FrameFingerprint): Boolean = distance(other) < Tuning.SAME_PAGE_MAD

    companion object {
        fun of(
            tiny: LumaImage,
            frameWidth: Int,
            frameHeight: Int,
            topInsetPx: Int,
            bottomInsetPx: Int,
        ): FrameFingerprint {
            val cols = Tuning.FINGERPRINT_COLS
            val rows = Tuning.FINGERPRINT_ROWS
            val cells = ByteArray(cols * rows)
            for (gy in 0 until rows) {
                val y0 = gy * tiny.height / rows
                val y1 = ((gy + 1) * tiny.height / rows).coerceAtLeast(y0 + 1)
                for (gx in 0 until cols) {
                    val x0 = gx * tiny.width / cols
                    val x1 = ((gx + 1) * tiny.width / cols).coerceAtLeast(x0 + 1)
                    var sum = 0L
                    var count = 0
                    for (y in y0 until y1) {
                        if (y !in 0 until tiny.height) continue
                        for (x in x0 until x1) {
                            if (x !in 0 until tiny.width) continue
                            sum += tiny[x, y]
                            count++
                        }
                    }
                    cells[gy * cols + gx] = (if (count == 0) 0 else sum / count).toByte()
                }
            }
            val topMaskPx = max(topInsetPx, (frameHeight * Tuning.STATUS_BAR_MASK_FRACTION).toInt())
            val bottomMaskPx = max(bottomInsetPx, (frameHeight * Tuning.NAV_BAR_MASK_FRACTION).toInt())
            val topMaskRows = ((topMaskPx.toLong() * rows / frameHeight).toInt()).coerceIn(0, rows)
            val bottomMaskRows = ((bottomMaskPx.toLong() * rows / frameHeight).toInt()).coerceIn(0, rows - topMaskRows)
            return FrameFingerprint(cells, cols, rows, topMaskRows, bottomMaskRows, frameWidth, frameHeight)
        }
    }
}
