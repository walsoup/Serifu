package app.serifu.core.image

import app.serifu.core.geometry.IntRect

/**
 * Binary mask of a bubble interior stored as one horizontal span per row.
 * Coordinates are in the space the flood fill ran in (quarter-scale luma
 * pixels). [x0]/[y0] is the origin of the search bound B in that space.
 * Empty rows hold minX = -1.
 *
 * plan.md 10.9 step 6.
 */
class RowSpans(
    val x0: Int,
    val y0: Int,
    val width: Int,
    val height: Int,
    val minX: IntArray,
    val maxX: IntArray,
    val scale: Int = 4,
) {
    init {
        require(width > 0 && height > 0)
        require(minX.size == height && maxX.size == height)
    }

    fun isEmptyRow(row: Int): Boolean = minX[row] < 0 || maxX[row] < minX[row]

    fun filledCount(): Long {
        var total = 0L
        for (row in 0 until height) {
            if (!isEmptyRow(row)) total += (maxX[row] - minX[row] + 1)
        }
        return total
    }

    /** Tight bounding box of filled pixels, in mask space. Null when empty. */
    fun boundingBox(): IntRect? {
        var top = -1
        var bottom = -1
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (row in 0 until height) {
            if (isEmptyRow(row)) continue
            if (top < 0) top = row
            bottom = row
            if (minX[row] < left) left = minX[row]
            if (maxX[row] > right) right = maxX[row]
        }
        if (top < 0) return null
        return IntRect(left, y0 + top, right + 1, y0 + bottom + 1)
    }

    fun fillRatio(): Float {
        val box = boundingBox() ?: return 0f
        val area = box.width.toLong() * box.height.toLong()
        if (area <= 0) return 0f
        return (filledCount().toFloat() / area.toFloat()).coerceIn(0f, 1f)
    }

    /** Bounding box scaled back to full-frame pixels. */
    fun boundingBoxFrame(): IntRect? {
        val box = boundingBox() ?: return null
        return IntRect(
            box.left * scale,
            box.top * scale,
            box.right * scale,
            box.bottom * scale,
        )
    }
}
