package app.serifu.core.layout

import app.serifu.core.Tuning

data class MeasuredLine(
    val heightPx: Float,
    val midWordBreak: Boolean,
)

data class FitResult(
    val sizePx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val iterations: Int,
)

/**
 * Largest size that fits, then the overflow sequence: width +20%, height +30%, then 8 sp grown until the text fits.
 */
object TextFitSearch {
    fun fit(
        boxWidth: Float,
        boxHeight: Float,
        minPx: Float,
        maxPx: Float,
        absoluteMinPx: Float,
        measure: (sizePx: Float, widthPx: Float) -> MeasuredLine,
    ): FitResult {
        val lowSize = minOf(minPx, maxPx)
        val highSize = maxOf(minPx, maxPx)
        val width = boxWidth.coerceAtLeast(1f)
        val height = boxHeight.coerceAtLeast(1f)
        if (accepts(lowSize, width, height, measure)) {
            return search(lowSize, highSize, width, height, measure)
        }
        val widened = width * (1f + Tuning.OVERFLOW_WIDTH_FRACTION)
        if (accepts(lowSize, widened, height, measure)) {
            return FitResult(lowSize, widened, height, 0)
        }
        val taller = height * (1f + Tuning.OVERFLOW_HEIGHT_FRACTION)
        if (accepts(lowSize, widened, taller, measure)) {
            return FitResult(lowSize, widened, taller, 0)
        }
        return grow(absoluteMinPx, widened, taller, measure)
    }

    private fun search(
        minPx: Float,
        maxPx: Float,
        width: Float,
        height: Float,
        measure: (Float, Float) -> MeasuredLine,
    ): FitResult {
        var lo = minPx
        var hi = maxPx
        var best = minPx
        var iterations = 0
        while (iterations < Tuning.FIT_MAX_ITERATIONS && hi - lo >= Tuning.FIT_EPSILON_PX) {
            iterations++
            val mid = (lo + hi) / 2f
            if (accepts(mid, width, height, measure)) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        return FitResult(best, width, height, iterations)
    }

    private fun grow(
        sizePx: Float,
        startWidth: Float,
        startHeight: Float,
        measure: (Float, Float) -> MeasuredLine,
    ): FitResult {
        var width = startWidth
        var height = startHeight
        var measured = measure(sizePx, width)
        var steps = 0
        while (steps < Tuning.OVERFLOW_GROW_MAX_STEPS && (measured.midWordBreak || measured.heightPx > height)) {
            if (measured.midWordBreak) width *= Tuning.OVERFLOW_GROW_STEP
            if (measured.heightPx > height) height = measured.heightPx
            steps++
            measured = measure(sizePx, width)
        }
        if (measured.heightPx > height) height = measured.heightPx
        return FitResult(sizePx, width, height, 0)
    }

    private fun accepts(
        sizePx: Float,
        widthPx: Float,
        heightPx: Float,
        measure: (Float, Float) -> MeasuredLine,
    ): Boolean {
        val measured = measure(sizePx, widthPx)
        return !measured.midWordBreak && measured.heightPx <= heightPx
    }
}
