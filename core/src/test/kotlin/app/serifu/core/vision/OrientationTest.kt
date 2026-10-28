package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class OrientationTest {
    @Test
    fun tallWideAndSingleGlyph() {
        assertEquals(Orientation.VERTICAL, orientationOf(line("あいう", IntRect(0, 0, 40, 200))))
        assertEquals(40f, charSizeOf(line("あいう", IntRect(0, 0, 40, 200)), Orientation.VERTICAL))
        assertEquals(Orientation.HORIZONTAL, orientationOf(line("おはよう", IntRect(0, 0, 200, 40))))
        assertEquals(40f, charSizeOf(line("おはよう", IntRect(0, 0, 200, 40)), Orientation.HORIZONTAL))
        val single = line("あ", IntRect(0, 0, 30, 80))
        assertEquals(Orientation.UNKNOWN, orientationOf(single))
        assertEquals(30f, charSizeOf(single, Orientation.UNKNOWN))
    }

    private fun line(text: String, box: IntRect) =
        OcrLine(text, box, confidence = 0.9f, angleDeg = 0f, language = "ja", symbolBoxes = emptyList())
}
