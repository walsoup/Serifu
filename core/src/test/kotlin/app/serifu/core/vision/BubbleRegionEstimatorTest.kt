package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BubbleRegionEstimatorTest {
    private fun white(w: Int, h: Int): LumaImage = LumaImage(w, h, ByteArray(w * h) { 255.toByte() })

    private fun ellipseOutline(image: LumaImage, cx: Int, cy: Int, rx: Int, ry: Int) {
        for (deg in 0 until 360) {
            val x = (cx + rx * kotlin.math.cos(Math.toRadians(deg.toDouble()))).toInt()
            val y = (cy + ry * kotlin.math.sin(Math.toRadians(deg.toDouble()))).toInt()
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val px = x + dx
                    val py = y + dy
                    if (px in 0 until image.width && py in 0 until image.height) {
                        image.bytes[py * image.width + px] = 0
                    }
                }
            }
        }
    }

    @Test
    fun ellipseBubbleLetteringIsWiderThanTheColumn() {
        // Quarter-scale 60x60 stands in for a 240x240 frame (scale 4).
        val quarter = white(60, 60)
        ellipseOutline(quarter, 30, 30, 20, 25)
        // Narrow CJK column in frame pixels.
        val union = IntRect(100, 60, 140, 200)
        val region = BubbleRegionEstimator.estimate(quarter, union, charSizePx = 40f, padPx = 16)
        assertNotNull(region.mask)
        assertTrue(region.letteringRect.width > (union.width * 1.5f).toInt())
    }

    @Test
    fun borderlessTextOnWhiteFallsBack() {
        val quarter = white(60, 60)
        val union = IntRect(100, 60, 140, 200)
        val region = BubbleRegionEstimator.estimate(quarter, union, charSizePx = 40f, padPx = 16)
        assertNull(region.mask)
        assertTrue(region.leaked)
    }

    @Test
    fun captionRectangleHasHighFillRatio() {
        val quarter = white(60, 60)
        // Rectangular narration box: 3px black border, white interior.
        for (x in 5 until 55) {
            for (y in listOf(5, 6, 7, 52, 53, 54)) {
                if (y in 0 until 60) quarter.bytes[y * 60 + x] = 0
            }
        }
        for (y in 5 until 55) {
            for (x in listOf(5, 6, 7, 52, 53, 54)) {
                quarter.bytes[y * 60 + x] = 0
            }
        }
        val union = IntRect(80, 80, 160, 160)
        val region = BubbleRegionEstimator.estimate(quarter, union, charSizePx = 20f, padPx = 16)
        assertNotNull(region.mask)
        assertTrue(region.mask!!.fillRatio() > 0.92f)
    }
}
