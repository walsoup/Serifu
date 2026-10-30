package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import app.serifu.core.image.RowSpans
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bubble interior estimation (plan.md 10.9): flood-fill the paper around the
 * glyphs so English lettering can use the whole bubble instead of the narrow
 * CJK column.
 *
 * Runs on the quarter-scale luma; results are scaled back up to frame pixels.
 */
object BubbleRegionEstimator {
    data class RegionResult(
        val mask: RowSpans?,
        val letteringRect: IntRect,
        val patchRect: IntRect,
        val leaked: Boolean,
    )

    fun estimate(
        quarter: LumaImage,
        unionRect: IntRect,
        charSizePx: Float,
        padPx: Int,
        scale: Int = Tuning.LUMA_PYRAMID_QUARTER,
    ): RegionResult {
        val frame = IntRect(0, 0, quarter.width * scale, quarter.height * scale)
        val fallback = fallback(unionRect, padPx, frame)
        // Search bound B in frame pixels, then mapped to quarter pixels.
        val expand = max(3f * charSizePx, 0.5f * max(unionRect.width, unionRect.height)).roundToInt()
        val boundFrame = IntRect(
            unionRect.left - expand,
            unionRect.top - expand,
            unionRect.right + expand,
            unionRect.bottom + expand,
        ).intersect(frame)
        if (boundFrame.isEmpty) return fallback
        val bound = IntRect(
            boundFrame.left / scale,
            boundFrame.top / scale,
            (boundFrame.right + scale - 1) / scale,
            (boundFrame.bottom + scale - 1) / scale,
        ).intersect(IntRect(0, 0, quarter.width, quarter.height))
        if (bound.isEmpty) return fallback
        val unionQ = IntRect(
            (unionRect.left / scale).coerceIn(0, quarter.width),
            (unionRect.top / scale).coerceIn(0, quarter.height),
            ((unionRect.right + scale - 1) / scale).coerceIn(0, quarter.width),
            ((unionRect.bottom + scale - 1) / scale).coerceIn(0, quarter.height),
        )
        val bg = backgroundLevel(quarter, unionQ)
        val w = bound.width
        val h = bound.height
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun index(x: Int, y: Int): Int = (y - bound.top) * w + (x - bound.left)
        fun matches(x: Int, y: Int): Boolean {
            if (x !in bound.left until bound.right || y !in bound.top until bound.bottom) return false
            return kotlin.math.abs(quarter[x, y] / 255f - bg) <= Tuning.BG_TOLERANCE
        }
        // Seeds: paper pixels inside the union rect.
        for (y in unionQ.top until unionQ.bottom) {
            for (x in unionQ.left until unionQ.right) {
                if (matches(x, y) && !visited[index(x, y)]) {
                    visited[index(x, y)] = true
                    queue[tail++] = y * quarter.width + x
                }
            }
        }
        if (tail == 0) return fallback
        while (head < tail) {
            val code = queue[head++]
            val x = code % quarter.width
            val y = code / quarter.width
            if (x + 1 < bound.right && matches(x + 1, y) && !visited[index(x + 1, y)]) {
                visited[index(x + 1, y)] = true; queue[tail++] = y * quarter.width + x + 1
            }
            if (x - 1 >= bound.left && matches(x - 1, y) && !visited[index(x - 1, y)]) {
                visited[index(x - 1, y)] = true; queue[tail++] = y * quarter.width + x - 1
            }
            if (y + 1 < bound.bottom && matches(x, y + 1) && !visited[index(x, y + 1)]) {
                visited[index(x, y + 1)] = true; queue[tail++] = (y + 1) * quarter.width + x
            }
            if (y - 1 >= bound.top && matches(x, y - 1) && !visited[index(x, y - 1)]) {
                visited[index(x, y - 1)] = true; queue[tail++] = (y - 1) * quarter.width + x
            }
        }
        // Leak guard: filled pixels touching more than 25% of B's perimeter.
        var touched = 0
        var perimeter = 0
        for (x in bound.left until bound.right) {
            perimeter += 2
            if (visited[index(x, bound.top)]) touched++
            if (bound.height > 1 && visited[index(x, bound.bottom - 1)]) touched++
        }
        for (y in bound.top + 1 until bound.bottom - 1) {
            perimeter += 2
            if (visited[index(bound.left, y)]) touched++
            if (bound.width > 1 && visited[index(bound.right - 1, y)]) touched++
        }
        if (perimeter > 0 && touched.toFloat() / perimeter > Tuning.LEAK_PERIMETER_FRACTION) {
            return fallback.copy(leaked = true)
        }
        // Row spans close the holes left by glyphs.
        val minX = IntArray(h) { -1 }
        val maxX = IntArray(h) { -1 }
        for (row in 0 until h) {
            var lo = Int.MAX_VALUE
            var hi = Int.MIN_VALUE
            for (col in 0 until w) {
                if (visited[row * w + col]) {
                    if (col < lo) lo = col
                    if (col > hi) hi = col
                }
            }
            if (hi >= lo) {
                minX[row] = bound.left + lo
                maxX[row] = bound.left + hi
            }
        }
        val mask = RowSpans(bound.left, bound.top, w, h, minX, maxX, scale)
        val maskBox = mask.boundingBox() ?: return fallback
        // Lettering box from the middle 70% of the mask height.
        val spanTop = maskBox.top
        val spanBottom = maskBox.bottom
        val spanH = (spanBottom - spanTop).coerceAtLeast(1)
        val midTop = spanTop + (spanH * (1f - Tuning.LETTERING_MIDDLE_HEIGHT_FRACTION) / 2f).roundToInt()
        val midBottom = spanBottom - (spanH * (1f - Tuning.LETTERING_MIDDLE_HEIGHT_FRACTION) / 2f).roundToInt()
        val widths = ArrayList<Int>()
        val centres = ArrayList<Int>()
        for (row in midTop until midBottom) {
            val local = row - bound.top
            if (local !in 0 until h || minX[local] < 0) continue
            widths.add(maxX[local] - minX[local] + 1)
            centres.add((minX[local] + maxX[local]) / 2)
        }
        if (widths.isEmpty()) return fallback
        val widthArray = widths.toIntArray()
        val minWidth = BubbleSpan.bodyWidth(widthArray)
        val threshold = BubbleSpan.bodyThreshold(widthArray)
        val bodyCentres = ArrayList<Int>()
        for (i in widths.indices) {
            if (widths[i] >= threshold) bodyCentres.add(centres[i])
        }
        val pool = if (bodyCentres.isEmpty()) centres else bodyCentres
        pool.sort()
        val centreX = pool[pool.size / 2]
        val midH = (midBottom - midTop).coerceAtLeast(1)
        val shrink = Tuning.LETTERING_SHRINK_FRACTION
        val letterQ = IntRect(
            (centreX - minWidth / 2f * (1f - shrink)).roundToInt(),
            (midTop + midH * shrink / 2f).roundToInt(),
            (centreX + minWidth / 2f * (1f - shrink)).roundToInt(),
            (midBottom - midH * shrink / 2f).roundToInt(),
        )
        val padQ = (padPx + scale - 1) / scale
        val unionPaddedQ = IntRect(
            unionQ.left - padQ, unionQ.top - padQ, unionQ.right + padQ, unionQ.bottom + padQ,
        )
        fun unionQ(a: IntRect, b: IntRect): IntRect {
            if (a.isEmpty) return b
            if (b.isEmpty) return a
            return IntRect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
        }
        val letterQ2 = unionQ(letterQ, unionPaddedQ)
        val lettering = IntRect(
            letterQ2.left * scale, letterQ2.top * scale,
            letterQ2.right * scale, letterQ2.bottom * scale,
        ).intersect(frame)
        val maskFrame = mask.boundingBoxFrame() ?: return fallback
        val patch = unionQ(unionQ(lettering, IntRect(
            unionRect.left - padPx, unionRect.top - padPx,
            unionRect.right + padPx, unionRect.bottom + padPx,
        )), maskFrame).intersect(frame)
        if (lettering.isEmpty || patch.isEmpty) return fallback
        return RegionResult(mask, lettering, patch, leaked = false)
    }

    private fun fallback(unionRect: IntRect, padPx: Int, frame: IntRect): RegionResult {
        val padded = IntRect(
            unionRect.left - padPx, unionRect.top - padPx,
            unionRect.right + padPx, unionRect.bottom + padPx,
        ).intersect(frame)
        val safe = if (padded.isEmpty) unionRect else padded
        return RegionResult(mask = null, letteringRect = safe, patchRect = safe, leaked = true)
    }

    private fun backgroundLevel(quarter: LumaImage, union: IntRect): Float {
        val samples = ArrayList<Int>(256)
        for (y in union.top until union.bottom) {
            if (y !in 0 until quarter.height) continue
            for (x in union.left until union.right) {
                if (x !in 0 until quarter.width) continue
                samples.add(quarter[x, y])
            }
        }
        if (samples.isEmpty()) return 1f
        samples.sort()
        val index = ((samples.size - 1) * Tuning.INK_WALL_BG_PERCENTILE).toInt()
            .coerceIn(0, samples.size - 1)
        return samples[index] / 255f
    }
}
