package app.serifu.core.change

import app.serifu.core.Tuning
import app.serifu.core.image.LumaImage
import kotlin.math.abs
import kotlin.math.max

/**
 * Vertical scroll estimation (plan.md 9.3): coarse match on the 1/16-scale
 * luma, fine match on the quarter-scale luma, horizontal-pan rejection.
 * Result is in full-resolution pixels.
 */
object ScrollEstimator {
    data class ScrollEstimate(val dyPx: Int, val residual: Float)

    fun estimate(
        prevTiny: LumaImage,
        curTiny: LumaImage,
        prevQuarter: LumaImage,
        curQuarter: LumaImage,
        frameHeightPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
    ): ScrollEstimate? {
        if (prevTiny.width != curTiny.width || prevTiny.height != curTiny.height) return null
        if (prevQuarter.width != curQuarter.width || prevQuarter.height != curQuarter.height) return null
        val tinyH = prevTiny.height
        if (tinyH <= 0) return null
        val topMaskT = maskRows(topInsetPx, frameHeightPx, tinyH, Tuning.STATUS_BAR_MASK_FRACTION)
        val bottomMaskT = maskRows(bottomInsetPx, frameHeightPx, tinyH, Tuning.NAV_BAR_MASK_FRACTION)
        val maxDy = (tinyH * Tuning.SCROLL_COARSE_FRACTION).toInt().coerceAtLeast(1)
        var bestDy = 0
        var bestCost = Float.MAX_VALUE
        for (dy in -maxDy..maxDy) {
            val cost = rowMad(prevTiny, curTiny, dy, topMaskT, bottomMaskT)
            if (cost < bestCost) {
                bestCost = cost
                bestDy = dy
            }
        }
        // Fine pass on quarter scale around 4x the coarse offset.
        val quarterH = prevQuarter.height
        val topMaskQ = maskRows(topInsetPx, frameHeightPx, quarterH, Tuning.STATUS_BAR_MASK_FRACTION)
        val bottomMaskQ = maskRows(bottomInsetPx, frameHeightPx, quarterH, Tuning.NAV_BAR_MASK_FRACTION)
        val center = bestDy * Tuning.SCROLL_FINE_SCALE
        var fineDy = center
        var fineCost = Float.MAX_VALUE
        for (dq in center - Tuning.SCROLL_FINE_RADIUS_ROWS..center + Tuning.SCROLL_FINE_RADIUS_ROWS) {
            val cost = rowMad(prevQuarter, curQuarter, dq, topMaskQ, bottomMaskQ)
            if (cost < fineCost) {
                fineCost = cost
                fineDy = dq
            }
        }
        val overlap = overlapFraction(quarterH, fineDy, topMaskQ, bottomMaskQ)
        if (overlap < Tuning.SCROLL_MIN_OVERLAP_FRACTION) return null
        if (fineCost >= Tuning.SCROLL_ACCEPT_MAD) return null
        if (!horizontalZero(prevQuarter, curQuarter, fineDy, topMaskQ, bottomMaskQ)) return null
        return ScrollEstimate(dyPx = fineDy * Tuning.LUMA_PYRAMID_QUARTER, residual = fineCost)
    }

    private fun maskRows(insetPx: Int, frameH: Int, levelH: Int, fraction: Float): Int {
        if (frameH <= 0 || levelH <= 0) return 0
        val maskPx = max(insetPx, (frameH * fraction).toInt())
        return ((maskPx.toLong() * levelH / frameH).toInt()).coerceIn(0, levelH)
    }

    private fun unmasked(height: Int, y: Int, topMask: Int, bottomMask: Int): Boolean =
        y >= topMask && y < height - bottomMask

    private fun rowMad(
        prev: LumaImage,
        cur: LumaImage,
        dy: Int,
        topMask: Int,
        bottomMask: Int,
    ): Float {
        var sum = 0L
        var count = 0
        for (yPrev in 0 until prev.height) {
            val yCur = yPrev - dy
            if (yCur !in 0 until cur.height) continue
            if (!unmasked(prev.height, yPrev, topMask, bottomMask)) continue
            if (!unmasked(cur.height, yCur, topMask, bottomMask)) continue
            for (x in 0 until prev.width) {
                sum += abs(prev[x, yPrev] - cur[x, yCur])
                count++
            }
        }
        if (count == 0) return Float.MAX_VALUE
        return sum.toFloat() / count
    }

    private fun overlapFraction(height: Int, dy: Int, topMask: Int, bottomMask: Int): Float {
        var overlap = 0
        var total = 0
        for (y in 0 until height) {
            if (!unmasked(height, y, topMask, bottomMask)) continue
            total++
            val other = y - dy
            if (other in 0 until height && unmasked(height, other, topMask, bottomMask)) overlap++
        }
        if (total == 0) return 0f
        return overlap.toFloat() / total
    }

    private fun horizontalZero(
        prev: LumaImage,
        cur: LumaImage,
        dy: Int,
        topMask: Int,
        bottomMask: Int,
    ): Boolean {
        var bestDx = 0
        var bestCost = Float.MAX_VALUE
        for (dx in -Tuning.SCROLL_DX_SEARCH_COLS..Tuning.SCROLL_DX_SEARCH_COLS) {
            val cost = colShiftMad(prev, cur, dy, dx, topMask, bottomMask)
            if (cost < bestCost) {
                bestCost = cost
                bestDx = dx
            }
        }
        return bestDx == 0
    }

    private fun colShiftMad(
        prev: LumaImage,
        cur: LumaImage,
        dy: Int,
        dx: Int,
        topMask: Int,
        bottomMask: Int,
    ): Float {
        var sum = 0L
        var count = 0
        for (yPrev in 0 until prev.height) {
            val yCur = yPrev - dy
            if (yCur !in 0 until cur.height) continue
            if (!unmasked(prev.height, yPrev, topMask, bottomMask)) continue
            if (!unmasked(cur.height, yCur, topMask, bottomMask)) continue
            for (xPrev in 0 until prev.width) {
                val xCur = xPrev - dx
                if (xCur !in 0 until cur.width) continue
                sum += abs(prev[xPrev, yPrev] - cur[xCur, yCur])
                count++
            }
        }
        if (count == 0) return Float.MAX_VALUE
        return sum.toFloat() / count
    }
}
