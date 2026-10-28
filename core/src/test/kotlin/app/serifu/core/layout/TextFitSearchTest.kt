package app.serifu.core.layout

import app.serifu.core.Tuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFitSearchTest {
    @Test
    fun picksTheLargestFittingSizeWithinSevenProbes() {
        val result = TextFitSearch.fit(
            boxWidth = 200f,
            boxHeight = 80f,
            minPx = 9f,
            maxPx = 22f,
            absoluteMinPx = 8f,
        ) { size, _ ->
            MeasuredLine(heightPx = if (size <= 14f) 80f else 120f, midWordBreak = false)
        }
        assertTrue(result.iterations <= 7)
        assertTrue(result.sizePx in 13.5f..14f)
        assertEquals(200f, result.widthPx)
        assertEquals(80f, result.heightPx)
    }

    @Test
    fun overflowGrowsWidthThenHeightThenDropsToAbsoluteMinimum() {
        val widened = TextFitSearch.fit(100f, 50f, 10f, 20f, 8f) { size, width ->
            val fits = size <= 10f && width >= 119f
            MeasuredLine(heightPx = if (fits) 50f else 90f, midWordBreak = false)
        }
        assertEquals(10f, widened.sizePx)
        assertEquals(100f * (1f + Tuning.OVERFLOW_WIDTH_FRACTION), widened.widthPx)
        assertEquals(50f, widened.heightPx)

        val taller = TextFitSearch.fit(100f, 40f, 10f, 20f, 8f) { _, _ ->
            MeasuredLine(heightPx = 50f, midWordBreak = false)
        }
        assertEquals(10f, taller.sizePx)
        assertEquals(100f * (1f + Tuning.OVERFLOW_WIDTH_FRACTION), taller.widthPx)
        assertEquals(40f * (1f + Tuning.OVERFLOW_HEIGHT_FRACTION), taller.heightPx)

        val absolute = TextFitSearch.fit(100f, 40f, 10f, 20f, 8f) { size, _ ->
            MeasuredLine(heightPx = if (size <= 8f) 200f else 400f, midWordBreak = false)
        }
        assertEquals(8f, absolute.sizePx)
        assertEquals(200f, absolute.heightPx)
    }
}
