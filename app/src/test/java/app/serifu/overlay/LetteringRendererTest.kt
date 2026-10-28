package app.serifu.overlay

import app.serifu.core.geometry.IntRect
import app.serifu.core.model.BubbleKind
import app.serifu.core.vision.Orientation
import app.serifu.core.vision.SpeechBubbleCluster
import app.serifu.settings.Settings
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
}
