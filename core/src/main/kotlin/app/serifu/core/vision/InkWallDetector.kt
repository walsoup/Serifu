package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.geometry.RectMath
import app.serifu.core.image.LumaImage
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ink-wall test C4 (plan.md 10.5): two adjacent bubbles are separated by a
 * dark ink border. Sampling is dense (every pixel) along 3 parallel rays so
 * a 2 px border is never stepped over.
 *
 * Deviation: the spec computes the background level on the quarter-scale luma
 * and samples the full-resolution luma. This implementation takes a single
 * full-resolution [LumaImage] and uses a 3 px full-res ring, which is the
 * stricter of the two and avoids threading two pyramids through the clusterer.
 */
object InkWallDetector {
    fun hasWall(
        a: IntRect,
        b: IntRect,
        orientation: Orientation,
        luma: LumaImage,
        refUnit: Float,
    ): Boolean {
        if (RectMath.gapX(a, b) <= 0 && RectMath.gapY(a, b) <= 0) return false
        return when (orientation) {
            Orientation.VERTICAL -> verticalWall(a, b, luma, refUnit)
            Orientation.HORIZONTAL -> horizontalWall(a, b, luma, refUnit)
            Orientation.UNKNOWN -> {
                // Single glyphs: check along whichever axis separates them.
                if (RectMath.gapX(a, b) > 0 && RectMath.gapY(a, b) == 0) {
                    verticalWall(a, b, luma, refUnit)
                } else if (RectMath.gapY(a, b) > 0 && RectMath.gapX(a, b) == 0) {
                    horizontalWall(a, b, luma, refUnit)
                } else {
                    false
                }
            }
        }
    }

    private fun verticalWall(a: IntRect, b: IntRect, luma: LumaImage, refUnit: Float): Boolean {
        if (RectMath.gapX(a, b) <= 0) return false
        val top = max(a.top, b.top)
        val bottom = minOf(a.bottom, b.bottom)
        if (bottom - top <= 0) return false
        val leftEdge: Int
        val rightEdge: Int
        if (a.right <= b.left) {
            leftEdge = a.right
            rightEdge = b.left
        } else {
            leftEdge = b.right
            rightEdge = a.left
        }
        if (rightEdge - leftEdge <= 0) return false
        val bg = backgroundLevel(luma, listOf(a, b))
        val height = (bottom - top).toFloat()
        var hits = 0
        for (fraction in rayFractions()) {
            val y = (top + height * fraction).toInt().coerceIn(0, luma.height - 1)
            if (rayHitsWall(luma, bg, refUnit, x0 = leftEdge, y0 = y, dx = 1, dy = 0, end = rightEdge)) hits++
        }
        return hits >= Tuning.INK_WALL_RAY_HITS_REQUIRED
    }

    private fun horizontalWall(a: IntRect, b: IntRect, luma: LumaImage, refUnit: Float): Boolean {
        if (RectMath.gapY(a, b) <= 0) return false
        val left = max(a.left, b.left)
        val right = minOf(a.right, b.right)
        if (right - left <= 0) return false
        val topEdge: Int
        val bottomEdge: Int
        if (a.bottom <= b.top) {
            topEdge = a.bottom
            bottomEdge = b.top
        } else {
            topEdge = b.bottom
            bottomEdge = a.top
        }
        if (bottomEdge - topEdge <= 0) return false
        val bg = backgroundLevel(luma, listOf(a, b))
        val width = (right - left).toFloat()
        var hits = 0
        for (fraction in rayFractions()) {
            val x = (left + width * fraction).toInt().coerceIn(0, luma.width - 1)
            if (rayHitsWall(luma, bg, refUnit, x0 = x, y0 = topEdge, dx = 0, dy = 1, end = bottomEdge)) hits++
        }
        return hits >= Tuning.INK_WALL_RAY_HITS_REQUIRED
    }

    private fun rayFractions(): FloatArray =
        floatArrayOf(Tuning.INK_WALL_RAY_LOW, Tuning.INK_WALL_RAY_MID, Tuning.INK_WALL_RAY_HIGH)

    private fun rayHitsWall(
        luma: LumaImage,
        bg: Float,
        refUnit: Float,
        x0: Int,
        y0: Int,
        dx: Int,
        dy: Int,
        end: Int,
    ): Boolean {
        val step = max(1, refUnit.roundToInt())
        val minInkRunPx = Tuning.INK_RUN_REF_UNITS * refUnit
        var inkRuns = 0
        var inkLenPx = 0
        var inInk = false
        var runHadPriorBg = false
        var seenBg = false
        var otherStreak = 0
        var otherRunFound = false
        var x = x0
        var y = y0
        // Walk the gap only: glyph ink touching the ray ends is not a border
        // run unless background was seen before it.
        while (x in 0 until luma.width && y in 0 until luma.height) {
            if (dx != 0 && x >= end) break
            if (dy != 0 && y >= end) break
            val sample = luma[x, y] / 255f
            val isInk = sample < bg - Tuning.INK_DELTA
            val isBg = !isInk && kotlin.math.abs(sample - bg) <= Tuning.BG_TOLERANCE
            if (isInk) {
                if (!inInk) {
                    inInk = true
                    inkLenPx = 0
                    runHadPriorBg = seenBg
                }
                inkLenPx += step
            } else {
                if (inInk) {
                    if (inkLenPx >= minInkRunPx && runHadPriorBg) inkRuns++
                    inInk = false
                    inkLenPx = 0
                }
                if (!isBg) {
                    otherStreak++
                    if (otherStreak >= Tuning.OTHER_RUN_MIN) otherRunFound = true
                } else {
                    otherStreak = 0
                    seenBg = true
                }
            }
            x += dx * step
            y += dy * step
        }
        if (inInk && inkLenPx >= minInkRunPx && runHadPriorBg) inkRuns++
        if (inkRuns == 0) return false
        return inkRuns >= 2 || otherRunFound
    }

    internal fun backgroundLevel(luma: LumaImage, boxes: List<IntRect>): Float {
        val ring = Tuning.SAMPLE_RING_PX
        val samples = ArrayList<Int>(256)
        for (box in boxes) {
            val top = box.top - ring
            val bottom = box.bottom + ring
            val left = box.left - ring
            val right = box.right + ring
            for (y in top until bottom) {
                if (y !in 0 until luma.height) continue
                val interiorY = y in box.top until box.bottom
                for (x in left until right) {
                    if (x !in 0 until luma.width) continue
                    if (interiorY && x in box.left until box.right) continue
                    samples.add(luma[x, y])
                }
            }
        }
        if (samples.isEmpty()) return 1f
        samples.sort()
        val index = ((samples.size - 1) * Tuning.INK_WALL_BG_PERCENTILE).toInt().coerceIn(0, samples.size - 1)
        return samples[index] / 255f
    }
}
