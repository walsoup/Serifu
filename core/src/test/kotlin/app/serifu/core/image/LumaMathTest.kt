package app.serifu.core.image

import app.serifu.core.geometry.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumaMathTest {
    @Test
    fun rec709Endpoints() {
        assertEquals(0, LumaMath.of(0, 0, 0))
        assertEquals(255, LumaMath.of(255, 255, 255))
    }

    @Test
    fun boxAverageQuartersTheFrame() {
        val pixels = IntArray(16) { 0xFFFFFFFF.toInt() }
        val luma = LumaMath.boxDownsample(pixels, 4, 4, 2)
        assertEquals(2, luma.width)
        assertEquals(2, luma.height)
        assertEquals(255, luma[0, 0])
        assertEquals(255, luma[1, 1])
    }

    @Test
    fun secureBlackNeedsADarkFlatFrame() {
        val black = LumaImage(4, 4, ByteArray(16))
        assertTrue(LumaMath.isSecureBlack(black))
        val mixed = ByteArray(16) { index -> if (index < 8) 0 else 255.toByte() }
        assertFalse(LumaMath.isSecureBlack(LumaImage(4, 4, mixed)))
    }
}

class PillMaskTest {
    @Test
    fun medianIgnoresTheInterior() {
        val width = 5
        val height = 5
        val pixels = IntArray(width * height) { 0xFF101010.toInt() }
        val center = IntRect(2, 2, 3, 3)
        pixels[2 * width + 2] = 0xFF000000.toInt()
        val samples = PillMask.sampleRing(pixels, width, height, center, ringPx = 1)
        assertEquals(0xFF101010.toInt(), PillMask.medianArgb(samples))
        PillMask.fill(pixels, width, center, PillMask.medianArgb(samples))
        assertEquals(0xFF101010.toInt(), pixels[2 * width + 2])
    }
}
