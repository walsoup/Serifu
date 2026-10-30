package app.serifu.core.layout

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RowSpans
import kotlin.math.max
import kotlin.math.min

/** A closed outline of a bubble interior, in frame pixels, as x,y pairs. */
class CleanContour(val xy: FloatArray) {
    fun contains(x: Float, y: Float): Boolean {
        val n = xy.size / 2
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = xy[i * 2]
            val yi = xy[i * 2 + 1]
            val xj = xy[j * 2]
            val yj = xy[j * 2 + 1]
            val crosses = (yi > y) != (yj > y)
            if (crosses && yj != yi) {
                val atX = (xj - xi) * (y - yi) / (yj - yi) + xi
                if (x < atX) inside = !inside
            }
            j = i
        }
        return inside
    }
}

/**
 * The shape that replaces the original lettering. It follows the balloon,
 * covers the glyphs, and stops short of the ink outline.
 */
object BubbleClean {
    fun contours(mask: RowSpans, cover: IntRect): List<CleanContour> {
        val coverBox = cover.expand(Tuning.CLEAN_COVER_PAD_PX)
        val cap = mask.scale.toFloat()
        val inset = Tuning.CLEAN_WALL_INSET_PX
        val half = mask.scale / 2f
        val runs = ArrayList<ArrayList<RowEdge>>()
        var current = ArrayList<RowEdge>()
        var pendingGap = 0
        for (row in 0 until mask.height) {
            if (mask.isEmptyRow(row)) {
                pendingGap++
                if (pendingGap > 1 && current.isNotEmpty()) {
                    runs.add(current)
                    current = ArrayList()
                }
                continue
            }
            pendingGap = 0
            val rawLeft = mask.minX[row] * mask.scale.toFloat()
            val rawRight = (mask.maxX[row] + 1) * mask.scale.toFloat()
            val yTop = (mask.y0 + row) * mask.scale.toFloat()
            val overlaps = yTop + mask.scale > coverBox.top && yTop < coverBox.bottom
            val edged = edge(rawLeft, rawRight, overlaps, coverBox, cap, inset)
            if (edged.second - edged.first < 1f) continue
            current.add(
                RowEdge(
                    left = edged.first,
                    right = edged.second,
                    rawLeft = rawLeft,
                    rawRight = rawRight,
                    y = yTop + half,
                    overlaps = overlaps,
                ),
            )
        }
        if (current.isNotEmpty()) runs.add(current)
        return runs.mapNotNull { contourOf(it, coverBox, cap, half) }
    }

    private fun edge(
        rawLeft: Float,
        rawRight: Float,
        overlaps: Boolean,
        cover: IntRect,
        cap: Float,
        inset: Float,
    ): Pair<Float, Float> {
        var left = rawLeft
        var right = rawRight
        if (overlaps) {
            left = min(rawLeft, cover.left.toFloat()).coerceAtLeast(rawLeft - cap)
            right = max(rawRight, cover.right.toFloat()).coerceAtMost(rawRight + cap)
        }
        if (right - left <= inset * 2f + 2f) return left to right
        val insetLeft = left + inset
        val insetRight = right - inset
        return if (overlaps) {
            min(insetLeft, cover.left.toFloat()).coerceAtLeast(rawLeft - cap) to
                max(insetRight, cover.right.toFloat()).coerceAtMost(rawRight + cap)
        } else {
            insetLeft to insetRight
        }
    }

    private fun contourOf(rows: List<RowEdge>, cover: IntRect, cap: Float, half: Float): CleanContour? {
        if (rows.size < 3) return null
        val radius = when {
            rows.size >= 14 -> 2
            rows.size >= 7 -> 1
            else -> 0
        }
        val left = smooth(FloatArray(rows.size) { rows[it].left }, radius)
        val right = smooth(FloatArray(rows.size) { rows[it].right }, radius)
        val xsL = FloatArray(rows.size)
        val xsR = FloatArray(rows.size)
        for (i in rows.indices) {
            val row = rows[i]
            // Keep the whole inset span. Smoothing may only step outward, and
            // only by one mask pixel, so the outline is nicked rather than the
            // glyphs being uncovered.
            var l = min(left[i], row.left).coerceAtLeast(row.rawLeft - cap)
            var r = max(right[i], row.right).coerceAtMost(row.rawRight + cap)
            if (row.overlaps) {
                l = min(l, cover.left.toFloat()).coerceAtLeast(row.rawLeft - cap)
                r = max(r, cover.right.toFloat()).coerceAtMost(row.rawRight + cap)
            }
            if (r - l < 1f) {
                l = row.left
                r = row.right
            }
            xsL[i] = l
            xsR[i] = r
        }
        val xy = FloatArray((rows.size + 2) * 4)
        var p = 0
        fun put(x: Float, y: Float) {
            xy[p++] = x
            xy[p++] = y
        }
        put(xsL[0], rows[0].y - half)
        for (i in rows.indices) put(xsL[i], rows[i].y)
        put(xsL[rows.lastIndex], rows.last().y + half)
        put(xsR[rows.lastIndex], rows.last().y + half)
        for (i in rows.indices.reversed()) put(xsR[i], rows[i].y)
        put(xsR[0], rows[0].y - half)
        return CleanContour(xy.copyOf(p))
    }

    private fun smooth(values: FloatArray, radius: Int): FloatArray {
        if (radius <= 0) return values
        return FloatArray(values.size) { index ->
            var sum = 0f
            var count = 0
            for (k in (index - radius)..(index + radius)) {
                if (k in values.indices) {
                    sum += values[k]
                    count++
                }
            }
            sum / count
        }
    }

    private class RowEdge(
        val left: Float,
        val right: Float,
        val rawLeft: Float,
        val rawRight: Float,
        val y: Float,
        val overlaps: Boolean,
    )
}
