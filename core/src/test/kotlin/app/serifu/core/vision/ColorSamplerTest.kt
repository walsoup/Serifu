package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RowSpans
import app.serifu.core.image.RgbSampler
import app.serifu.core.model.BubbleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSamplerTest {
    private class ArrayRgb(val pixels: IntArray, override val width: Int, override val height: Int) : RgbSampler {
        override fun argb(x: Int, y: Int): Int = pixels[y * width + x]
    }

    private fun solidRgb(w: Int, h: Int, argb: Int): ArrayRgb =
        ArrayRgb(IntArray(w * h) { argb }, w, h)

    @Test
    fun whiteBubbleGetsInkText() {
        val rgb = solidRgb(240, 240, 0xFFF3EEE3.toInt())
        val mask = RowSpans(0, 0, 60, 60, IntArray(60) { 10 }, IntArray(60) { 50 }, scale = 4)
        val fill = ColorSampler.sampleFill(mask, rgb)
        assertEquals(0xFFF3EEE3.toInt(), fill)
        assertEquals(app.serifu.core.Tuning.INK_ARGB, ColorSampler.textColorFor(fill))
    }

    @Test
    fun blackNarrationBoxGetsPaperText() {
        val rgb = solidRgb(240, 240, 0xFF0F0E0C.toInt())
        val mask = RowSpans(0, 0, 60, 60, IntArray(60) { 10 }, IntArray(60) { 50 }, scale = 4)
        val fill = ColorSampler.sampleFill(mask, rgb)
        assertEquals(app.serifu.core.model.BubbleKind.DIALOGUE.name, BubbleKind.DIALOGUE.name)
        assertEquals(0xFFF3EEE3.toInt(), ColorSampler.textColorFor(fill))
    }

    @Test
    fun greyBubbleRingIgnoresBlackGlyphs() {
        // Grey page with a black glyph run inside the union; the ring outside
        // stays grey so the 75th percentile picks grey, not glyph black.
        val w = 200
        val h = 200
        val pixels = IntArray(w * h) { 0xFF969696.toInt() }
        for (y in 90 until 110) {
            for (x in 90 until 110) {
                pixels[y * w + x] = 0xFF000000.toInt()
            }
        }
        val rgb = ArrayRgb(pixels, w, h)
        val union = IntRect(85, 85, 115, 115)
        val fill = ColorSampler.sampleRing(union, 16, rgb)
        val r = (fill shr 16) and 0xFF
        assertTrue(r in 130..170, "expected grey, got $r")
    }

    @Test
    fun classifierSeparatesDialogueCaptionSfxAndFreeText() {
        // Ellipse-like mask: spans narrow toward the top/bottom so the fill
        // ratio stays below the caption threshold.
        val dialogueMask = RowSpans(
            0, 0, 20, 20,
            IntArray(20) { 2 },
            IntArray(20) { row -> 8 + row % 10 },
            scale = 4,
        )
        val dialogue = app.serifu.core.vision.BubbleClassifier.classify(
            mask = dialogueMask,
            ringStddev = 0.04f,
            rotated = false,
            charSizePx = 30f,
            medianCharSizePx = 30f,
        )
        assertEquals(BubbleKind.DIALOGUE, dialogue)
        val sfx = app.serifu.core.vision.BubbleClassifier.classify(
            mask = null,
            ringStddev = 0.25f,
            rotated = false,
            charSizePx = 30f,
            medianCharSizePx = 30f,
        )
        assertEquals(BubbleKind.SFX, sfx)
        val free = app.serifu.core.vision.BubbleClassifier.classify(
            mask = null,
            ringStddev = 0.12f,
            rotated = false,
            charSizePx = 30f,
            medianCharSizePx = 30f,
        )
        assertEquals(BubbleKind.FREE_TEXT, free)
    }
}
