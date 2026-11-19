package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InkWallDetectorTest {
    private fun white(w: Int, h: Int): LumaImage = LumaImage(w, h, ByteArray(w * h) { 255.toByte() })

    private fun vLine(image: LumaImage, x: Int, top: Int, bottom: Int, value: Int = 0) {
        for (y in top until bottom) {
            if (x in 0 until image.width && y in 0 until image.height) {
                image.bytes[y * image.width + x] = value.toByte()
            }
        }
    }

    private fun hLine(image: LumaImage, y: Int, left: Int, right: Int, value: Int = 0) {
        for (x in left until right) {
            if (x in 0 until image.width && y in 0 until image.height) {
                image.bytes[y * image.width + x] = value.toByte()
            }
        }
    }

    private val refUnit = 1f

    @Test
    fun twoTouchingBubblesAreAWall() {
        val image = white(120, 60)
        // Left bubble border and right bubble border with a paper gutter.
        vLine(image, 47, 5, 55)
        vLine(image, 48, 5, 55)
        vLine(image, 52, 5, 55)
        vLine(image, 53, 5, 55)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun oneBubbleTwoColumnsIsNoWall() {
        val image = white(120, 60)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertFalse(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun thinTwoPixelBordersAreStillDetected() {
        val image = white(120, 60)
        // Two thin borders with a single-pixel paper gutter.
        vLine(image, 48, 5, 55)
        vLine(image, 49, 5, 55)
        vLine(image, 51, 5, 55)
        vLine(image, 52, 5, 55)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun screentoneGutterWithOneBorderIsAWall() {
        val image = white(120, 60)
        // Grey screentone band in the gap plus a single border line.
        for (y in 5 until 55) {
            for (x in 50 until 56) {
                image.bytes[y * image.width + x] = 200.toByte()
            }
        }
        vLine(image, 45, 5, 55)
        vLine(image, 46, 5, 55)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun darkPageWithLightPaperStillWorks() {
        val image = LumaImage(120, 60, ByteArray(120 * 60) { 30.toByte() })
        // White bubble interiors around both boxes.
        for (y in 5 until 55) {
            for (x in 5 until 95) {
                image.bytes[y * image.width + x] = 255.toByte()
            }
        }
        vLine(image, 47, 5, 55)
        vLine(image, 48, 5, 55)
        vLine(image, 52, 5, 55)
        vLine(image, 53, 5, 55)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun overlappingBoxesAreNeverAWall() {
        val image = white(120, 60)
        vLine(image, 50, 5, 55)
        val a = IntRect(10, 10, 60, 50)
        val b = IntRect(40, 10, 90, 50)
        assertFalse(InkWallDetector.hasWall(a, b, Orientation.VERTICAL, image, refUnit))
    }

    @Test
    fun stackedBubblesAreAHorizontalWall() {
        val image = white(120, 120)
        hLine(image, 52, 5, 115)
        hLine(image, 53, 5, 115)
        hLine(image, 57, 5, 115)
        hLine(image, 58, 5, 115)
        val a = IntRect(10, 10, 90, 40)
        val b = IntRect(10, 70, 90, 100)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.HORIZONTAL, image, refUnit))
    }

    @Test
    fun stackedTextWithoutABorderIsNoHorizontalWall() {
        val image = white(120, 120)
        val a = IntRect(10, 10, 90, 40)
        val b = IntRect(10, 70, 90, 100)
        assertFalse(InkWallDetector.hasWall(a, b, Orientation.HORIZONTAL, image, refUnit))
    }

    @Test
    fun unknownOrientationFollowsTheSeparatingAxis() {
        val vertical = white(120, 60)
        vLine(vertical, 47, 5, 55)
        vLine(vertical, 48, 5, 55)
        vLine(vertical, 52, 5, 55)
        vLine(vertical, 53, 5, 55)
        val a = IntRect(10, 10, 40, 50)
        val b = IntRect(60, 10, 90, 50)
        assertTrue(InkWallDetector.hasWall(a, b, Orientation.UNKNOWN, vertical, refUnit))

        val horizontal = white(120, 120)
        hLine(horizontal, 52, 5, 115)
        hLine(horizontal, 53, 5, 115)
        hLine(horizontal, 57, 5, 115)
        hLine(horizontal, 58, 5, 115)
        val c = IntRect(10, 10, 90, 40)
        val d = IntRect(10, 70, 90, 100)
        assertTrue(InkWallDetector.hasWall(c, d, Orientation.UNKNOWN, horizontal, refUnit))

        // Diagonal separation is neither axis: no wall.
        val e = IntRect(10, 10, 40, 40)
        val f = IntRect(60, 60, 90, 90)
        assertFalse(InkWallDetector.hasWall(e, f, Orientation.UNKNOWN, horizontal, refUnit))
    }
}
