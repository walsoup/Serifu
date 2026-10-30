package app.serifu.core.vision

import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleSpanTest {
    @Test
    fun aTailDoesNotSetTheLetteringWidth() {
        val widths = intArrayOf(40, 42, 38, 36, 8, 7, 6)
        assertEquals(36, BubbleSpan.bodyWidth(widths))
    }

    @Test
    fun aPlainOvalKeepsItsNarrowShoulder() {
        val widths = intArrayOf(30, 36, 40, 36, 30)
        assertEquals(30, BubbleSpan.bodyWidth(widths))
    }
}
