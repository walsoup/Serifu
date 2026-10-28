package app.serifu.core.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuriganaFilterTest {
    private fun line(box: app.serifu.core.geometry.IntRect, charSize: Float, orientation: Orientation, text: String) =
        FilteredLine(
            line = OcrLine(text, box, confidence = 0.9f, angleDeg = 0f, language = "ja", symbolBoxes = emptyList()),
            orientation = orientation,
            charSize = charSize,
            rotated = false,
        )

    @Test
    fun smallKanaRightOfColumnIsDropped() {
        val main = line(
            app.serifu.core.geometry.IntRect(100, 20, 140, 200),
            40f, Orientation.VERTICAL, "先輩",
        )
        val furi = line(
            app.serifu.core.geometry.IntRect(140, 40, 155, 100),
            15f, Orientation.VERTICAL, "せんぱい",
        )
        val result = FuriganaFilter.filter(listOf(main, furi))
        assertEquals(1, result.kept.size)
        assertEquals("先輩", result.kept.single().line.text)
        assertEquals(1, result.dropped.size)
    }

    @Test
    fun smallKanjiIsKept() {
        val main = line(
            app.serifu.core.geometry.IntRect(100, 20, 140, 200),
            40f, Orientation.VERTICAL, "先輩",
        )
        val small = line(
            app.serifu.core.geometry.IntRect(140, 40, 155, 100),
            15f, Orientation.VERTICAL, "漢",
        )
        val result = FuriganaFilter.filter(listOf(main, small))
        assertEquals(2, result.kept.size)
    }

    @Test
    fun sameSizeKanaIsKept() {
        val main = line(
            app.serifu.core.geometry.IntRect(100, 20, 140, 200),
            40f, Orientation.VERTICAL, "先輩",
        )
        val same = line(
            app.serifu.core.geometry.IntRect(140, 40, 180, 200),
            40f, Orientation.VERTICAL, "あいう",
        )
        val result = FuriganaFilter.filter(listOf(main, same))
        assertEquals(2, result.kept.size)
    }

    @Test
    fun horizontalFuriganaAboveIsDropped() {
        val main = line(
            app.serifu.core.geometry.IntRect(20, 100, 200, 140),
            40f, Orientation.HORIZONTAL, "先輩",
        )
        val furi = line(
            app.serifu.core.geometry.IntRect(60, 75, 110, 95),
            20f, Orientation.HORIZONTAL, "せんぱい",
        )
        val result = FuriganaFilter.filter(listOf(main, furi))
        assertEquals(1, result.kept.size)
        assertTrue(result.dropped.any { it.line.text == "せんぱい" })
    }
}
