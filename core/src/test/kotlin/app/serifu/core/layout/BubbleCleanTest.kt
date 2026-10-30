package app.serifu.core.layout

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RowSpans
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubbleCleanTest {
    @Test
    fun theCleanCoversTheGlyphsAndNotTheGutter() {
        val height = 30
        val minX = IntArray(height)
        val maxX = IntArray(height)
        for (row in 0 until height) {
            val t = (row - 15) / 15.0
            val half = (12 * sqrt(1.0 - t * t)).toInt().coerceAtLeast(2)
            minX[row] = 20 - half
            maxX[row] = 20 + half
        }
        val mask = RowSpans(0, 0, 40, height, minX, maxX, scale = 4)
        val glyphs = IntRect(72, 48, 96, 80)
        val contour = BubbleClean.contours(mask, glyphs).single()
        assertTrue(contour.contains(84f, 64f))
        assertTrue(contour.contains(glyphs.centerX.toFloat(), glyphs.centerY.toFloat()))
        assertFalse(contour.contains(4f, 4f))
    }
}
