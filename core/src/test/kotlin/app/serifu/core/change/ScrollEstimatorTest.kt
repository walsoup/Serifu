package app.serifu.core.change

import app.serifu.core.image.LumaImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollEstimatorTest {
    private val tinyW = 90
    private val tinyH = 200
    private val quarterW = 360
    private val quarterH = 800
    private val frameH = 3200

    private fun pattern(x: Int, y: Int): Int = (y * 37 + x * 11 + ((y / 9) * 53)) % 256

    /** Content moved up by [upTiny] tiny rows (normal scroll-down reading). */
    private fun tinyUp(upTiny: Int): LumaImage {
        return LumaImage(tinyW, tinyH, ByteArray(tinyW * tinyH) { i ->
            val x = i % tinyW
            val y = i / tinyW
            pattern(x, y + upTiny).toByte()
        })
    }

    private fun quarterUp(upQuarter: Int): LumaImage {
        return LumaImage(quarterW, quarterH, ByteArray(quarterW * quarterH) { i ->
            val x = i % quarterW
            val y = i / quarterW
            pattern(x / 4, y / 4 + upQuarter / 4).toByte()
        })
    }

    /** Shifts content up by [fullDyPx] full-res pixels; expects dyPx = +fullDyPx. */
    private fun estimateForShift(fullDyPx: Int): ScrollEstimator.ScrollEstimate? {
        val tinyShift = fullDyPx / 16
        val quarterShift = fullDyPx / 4
        return ScrollEstimator.estimate(
            prevTiny = tinyUp(0),
            curTiny = tinyUp(tinyShift),
            prevQuarter = quarterUp(0),
            curQuarter = quarterUp(quarterShift),
            frameHeightPx = frameH,
            topInsetPx = 0,
            bottomInsetPx = 0,
        )
    }

    @Test
    fun zeroShiftIsRecovered() {
        val estimate = estimateForShift(0)
        assertNotNull(estimate)
        assertTrue(abs(estimate.dyPx) <= 4, "dy=${estimate.dyPx}")
    }

    @Test
    fun smallShiftsAreRecoveredWithin4px() {
        for (dy in listOf(48, -48)) {
            val estimate = estimateForShift(dy)
            assertNotNull(estimate, "dy=$dy")
            assertTrue(abs(estimate.dyPx - dy) <= 4, "dy=$dy got ${estimate.dyPx}")
        }
    }

    @Test
    fun largeShiftIsRecoveredWithin4px() {
        for (dy in listOf(304, -304)) {
            val estimate = estimateForShift(dy)
            assertNotNull(estimate, "dy=$dy")
            assertTrue(abs(estimate.dyPx - dy) <= 4, "dy=$dy got ${estimate.dyPx}")
        }
    }

    @Test
    fun unrelatedPagesAreRejected() {
        val estimate = ScrollEstimator.estimate(
            prevTiny = tinyUp(0),
            curTiny = LumaImage(tinyW, tinyH, ByteArray(tinyW * tinyH) { 128.toByte() }),
            prevQuarter = quarterUp(0),
            curQuarter = LumaImage(quarterW, quarterH, ByteArray(quarterW * quarterH) { 30.toByte() }),
            frameHeightPx = frameH,
            topInsetPx = 0,
            bottomInsetPx = 0,
        )
        assertNull(estimate)
    }

    @Test
    fun horizontalPanIsRejected() {
        val prevQ = quarterUp(0)
        val pannedQ = LumaImage(quarterW, quarterH, ByteArray(quarterW * quarterH) { i ->
            val x = i % quarterW
            val y = i / quarterW
            pattern((x - 3) / 4, y / 4).toByte()
        })
        val estimate = ScrollEstimator.estimate(
            prevTiny = tinyUp(0),
            curTiny = tinyUp(0),
            prevQuarter = prevQ,
            curQuarter = pannedQ,
            frameHeightPx = frameH,
            topInsetPx = 0,
            bottomInsetPx = 0,
        )
        assertNull(estimate)
    }
}
