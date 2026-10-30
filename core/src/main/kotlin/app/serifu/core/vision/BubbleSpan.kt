package app.serifu.core.vision

import app.serifu.core.Tuning

/**
 * The lettering column inside a bubble. A tail, or one jagged row, must not
 * collapse that column to a few pixels.
 */
object BubbleSpan {
    fun bodyWidth(widths: IntArray): Int {
        if (widths.isEmpty()) return 0
        val sorted = widths.copyOf()
        sorted.sort()
        val median = sorted[sorted.size / 2]
        val threshold = (median * Tuning.TAIL_WIDTH_RATIO).toInt().coerceAtLeast(1)
        var minBody = Int.MAX_VALUE
        for (width in sorted) {
            if (width >= threshold && width < minBody) minBody = width
        }
        return if (minBody == Int.MAX_VALUE) median else minBody
    }

    fun bodyThreshold(widths: IntArray): Int {
        if (widths.isEmpty()) return 1
        val sorted = widths.copyOf()
        sorted.sort()
        val median = sorted[sorted.size / 2]
        return (median * Tuning.TAIL_WIDTH_RATIO).toInt().coerceAtLeast(1)
    }
}
