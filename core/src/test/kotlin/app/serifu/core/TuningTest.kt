package app.serifu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuningTest {
    @Test
    fun settleDelayDefaultSitsOnTheDocumentedRange() {
        assertTrue(Tuning.SETTLE_DELAY_DEFAULT_MS in Tuning.SETTLE_DELAY_MIN_MS..Tuning.SETTLE_DELAY_MAX_MS)
        assertEquals(450, Tuning.snapSettleDelay(450))
        assertEquals(250, Tuning.snapSettleDelay(100))
        assertEquals(1000, Tuning.snapSettleDelay(5000))
        assertEquals(500, Tuning.snapSettleDelay(480))
    }

    @Test
    fun screenshotIntervalFollowsApiLevel() {
        assertEquals(1050L, Tuning.minScreenshotIntervalMs(30))
        assertEquals(1050L, Tuning.minScreenshotIntervalMs(32))
        assertEquals(350L, Tuning.minScreenshotIntervalMs(33))
        assertEquals(350L, Tuning.minScreenshotIntervalMs(36))
    }

    @Test
    fun emptyPageRetryBacksOff() {
        assertEquals(800L, Tuning.emptyPageRetryDelayMs(0))
        assertEquals(1600L, Tuning.emptyPageRetryDelayMs(1))
    }

    @Test
    fun fontRangeClampsMatchTheSpec() {
        assertEquals(8, Tuning.snapMinFontSp(1))
        assertEquals(14, Tuning.snapMinFontSp(40))
        assertEquals(16, Tuning.snapMaxFontSp(9))
        assertEquals(28, Tuning.snapMaxFontSp(40))
    }
}
