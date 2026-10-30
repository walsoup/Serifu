package app.serifu.overlay

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RowSpans
import app.serifu.core.model.BubbleKind
import app.serifu.core.vision.Orientation
import app.serifu.core.vision.SpeechBubbleCluster
import app.serifu.settings.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LetteringRendererTest {
    @Test
    fun englishFitsInsideAPatchThatCoversTheJapanese() {
        val context = RuntimeEnvironment.getApplication()
        val union = IntRect(100, 80, 160, 460)
        val cluster = SpeechBubbleCluster(
            id = 0,
            kind = BubbleKind.DIALOGUE,
            sourceText = "おはよう！今日も早いね",
            cacheKey = "ja|おはよう！今日も早いね",
            orientation = Orientation.VERTICAL,
            unionRect = union,
            patchRect = union.expand(12),
            letteringRect = union.expand(12),
            fillColor = InkPaper.PAPER,
            textColor = InkPaper.INK,
            charSizePx = 60f,
            angleDeg = 0f,
            members = emptyList(),
        )
        val page = LetteringRenderer(context).build(
            clusters = listOf(cluster),
            translations = mapOf(0 to "Good morning. You are early again today."),
            settings = Settings(),
            frameW = 1080,
            frameH = 1920,
            rotation = 0,
            debugLines = emptyList(),
        )
        val bubble = page.bubbles.single()
        assertTrue(bubble.layout.height <= bubble.patch.height() + 1f)
        assertTrue(bubble.patch.left <= union.left && bubble.patch.top <= union.top)
        assertTrue(bubble.patch.right >= union.right && bubble.patch.bottom >= union.bottom)
        assertTrue(bubble.layoutOrigin.y >= bubble.patch.top - 1f)
        assertTrue(bubble.layoutOrigin.y + bubble.layout.height <= bubble.patch.bottom + 1f)
    }

    @Test
    fun dialogueCleansTheBalloonAndDoesNotHyphenate() {
        val context = RuntimeEnvironment.getApplication()
        val height = 28
        val minX = IntArray(height)
        val maxX = IntArray(height)
        for (row in 0 until height) {
            val pinch = if (row < 4 || row > height - 5) 4 else 0
            minX[row] = 3 + pinch
            maxX[row] = 22 - pinch
        }
        val mask = RowSpans(8, 6, 28, height, minX, maxX, scale = 4)
        val union = IntRect(56, 40, 100, 150)
        val cluster = SpeechBubbleCluster(
            id = 1,
            kind = BubbleKind.DIALOGUE,
            sourceText = "おはよう",
            cacheKey = "ja|おはよう",
            orientation = Orientation.VERTICAL,
            unionRect = union,
            patchRect = union.expand(8),
            letteringRect = IntRect(48, 36, 120, 160),
            bubbleMask = mask,
            fillColor = InkPaper.PAPER,
            textColor = InkPaper.INK,
            charSizePx = 28f,
            angleDeg = 0f,
            members = emptyList(),
        )
        val page = LetteringRenderer(context).build(
            clusters = listOf(cluster),
            translations = mapOf(1 to "Good morning. You are early again today."),
            settings = Settings(),
            frameW = 1080,
            frameH = 1920,
            rotation = 0,
            debugLines = emptyList(),
        )
        val bubble = page.bubbles.single()
        assertNotNull(bubble.cleanPath)
        assertFalse(bubble.cleanPath!!.isEmpty)
        assertFalse(bubble.layout.text.toString().contains('\u00ad'))
        assertTrue(bubble.patch.left <= union.left && bubble.patch.right >= union.right)
    }
}
