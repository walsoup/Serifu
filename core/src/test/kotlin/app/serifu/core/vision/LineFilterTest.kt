package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineFilterTest {
    @Test
    fun sizeThresholdScalesWithRefUnit() {
        val narrow = LineFilter.keep(listOf(line("あい", IntRect(0, 0, 8, 40))), 1080, 1920, skipLatin = true, pill = null)
        assertTrue(narrow.isEmpty())
        val scaled = LineFilter.keep(listOf(line("あい", IntRect(0, 0, 12, 80))), 2160, 3840, skipLatin = true, pill = null)
        assertTrue(scaled.isEmpty())
        val kept = LineFilter.keep(listOf(line("あい", IntRect(0, 0, 24, 80))), 2160, 3840, skipLatin = true, pill = null)
        assertEquals(1, kept.size)
    }

    @Test
    fun dropsPunctuationLowConfidenceAndOptionalLatin() {
        val lines = listOf(
            line("...", IntRect(0, 0, 40, 40)),
            line("あ", IntRect(0, 0, 40, 80), confidence = 0.2f),
            line("Hello", IntRect(0, 0, 80, 40)),
            line("おはよう", IntRect(0, 0, 40, 160)),
        )
        val skipped = LineFilter.keep(lines, 1080, 1920, skipLatin = true, pill = null).map { it.line.text }
        assertEquals(listOf("おはよう"), skipped)
        val latinKept = LineFilter.keep(lines, 1080, 1920, skipLatin = false, pill = null).map { it.line.text }
        assertEquals(listOf("Hello", "おはよう"), latinKept)
    }

    @Test
    fun zeroConfidenceIsKeptAndPillInteriorIsDropped() {
        val inside = line("隠れ", IntRect(10, 10, 40, 80))
        val outside = line("見える", IntRect(100, 10, 140, 80))
        val unknown = line("信頼なし", IntRect(200, 10, 240, 120), confidence = 0f)
        val kept = LineFilter.keep(
            listOf(inside, outside, unknown),
            1080,
            1920,
            skipLatin = true,
            pill = IntRect(0, 0, 80, 200),
        ).map { it.line.text }
        assertEquals(listOf("見える", "信頼なし"), kept)
    }

    @Test
    fun verticalTextIsNotRotatedButOffAxisIs() {
        assertEquals(false, LineFilter.isOffAxis(0f))
        assertEquals(false, LineFilter.isOffAxis(90f))
        assertEquals(false, LineFilter.isOffAxis(-90f))
        assertEquals(false, LineFilter.isOffAxis(180f))
        assertEquals(true, LineFilter.isOffAxis(24f))
        assertEquals(true, LineFilter.isOffAxis(45f))
        assertEquals(true, LineFilter.isOffAxis(-30f))
        val vertical = LineFilter.keep(
            listOf(line("おはよう", IntRect(0, 0, 40, 400), angleDeg = 90f)),
            1080, 1920, skipLatin = true, pill = null,
        )
        assertEquals(1, vertical.size)
        assertEquals(false, vertical.single().rotated)
    }

    private fun line(text: String, box: IntRect, confidence: Float = 0.9f, angleDeg: Float = 0f) =
        OcrLine(text, box, confidence, angleDeg = angleDeg, language = "ja", symbolBoxes = emptyList())
}
