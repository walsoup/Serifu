package app.serifu

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.serifu.capture.CapturedFrame
import app.serifu.capture.LumaPyramid
import app.serifu.capture.VerticalInsets
import app.serifu.coordinator.PagePipeline
import app.serifu.core.Tuning
import app.serifu.core.image.LumaMath
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import app.serifu.ml.MlKitTranslationEngine
import app.serifu.ml.OcrEngine
import app.serifu.overlay.LetteringRenderer
import app.serifu.settings.Settings
import app.serifu.ui.testpage.TestPageActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end pipeline check on the original-art test page (plan.md 17.3).
 * Runs [PagePipeline] directly on a PixelCopy capture of [TestPageActivity]:
 * 6 dialogue clusters + 1 caption + 1 SFX, touching bubbles separate,
 * no furigana in source texts, every bubble translated, layouts inside boxes.
 */
@RunWith(AndroidJUnit4::class)
class PipelineOnTestPageTest {
    @Test
    fun testPageLettersCompletely() {
        val scenario = ActivityScenario.launch(TestPageActivity::class.java)
        try {
            val bitmap = captureWindow(scenario)
            try {
                val frame = toFrame(bitmap)
                try {
                    val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    val ocr = OcrEngine()
                    val translator = MlKitTranslationEngine(wifiOnly = { false })
                    val pipeline = PagePipeline(
                        appContext,
                        translator,
                        LetteringRenderer(appContext),
                        ocr,
                    )
                    val lines = runBlocking { ocr.recognize(frame.bitmap, SourceLanguage.JA) }
                    assertFalse("OCR found no lines on the test page", lines.isEmpty())
                    val settings = Settings(sourceLanguage = SourceLanguage.JA, targetLanguage = "en")
                    val lettered = runBlocking {
                        pipeline.letter(lines, frame, settings, null, emptyList()) { System.currentTimeMillis() }
                    }
                    val page = lettered.page
                    assertNotNull("pipeline produced no page", page)
                    val bubbles = page!!.bubbles
                    val byKind = bubbles.groupBy { it.kind }
                    assertEquals("dialogue clusters", 6, byKind[BubbleKind.DIALOGUE]?.size ?: 0)
                    assertEquals("caption boxes", 1, byKind[BubbleKind.CAPTION]?.size ?: 0)
                    assertEquals("sfx tags", 1, byKind[BubbleKind.SFX]?.size ?: 0)
                    for (bubble in bubbles) {
                        assertFalse(
                            "furigana leaked into source text: ${bubble.sourceTextForDebug}",
                            bubble.sourceTextForDebug.contains("せんぱい"),
                        )
                        val text = bubble.layout.text.toString().trim()
                        assertTrue("empty lettering for bubble ${bubble.id}", text.isNotEmpty())
                        assertTrue(
                            "layout overflows patch for bubble ${bubble.id}",
                            bubble.layout.width <= bubble.patch.width() + 1f &&
                                bubble.layout.height <= bubble.patch.height() + 1f,
                        )
                    }
                    translator.close()
                    ocr.close()
                } finally {
                    frame.recycle()
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } finally {
            scenario.close()
        }
    }

    private fun captureWindow(scenario: ActivityScenario<TestPageActivity>): Bitmap {
        // Let Compose finish first composition.
        Thread.sleep(2500)
        var window: Window? = null
        scenario.onActivity { activity -> window = activity.window }
        val host = checkNotNull(window) { "no window" }
        val view = host.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var copyResult = -1
        PixelCopy.request(
            host,
            Rect(0, 0, view.width, view.height),
            bitmap,
            { result ->
                copyResult = result
                latch.countDown()
            },
            Handler(Looper.getMainLooper()),
        )
        assertTrue("PixelCopy timed out", latch.await(15, TimeUnit.SECONDS))
        assertEquals("PixelCopy failed", PixelCopy.SUCCESS, copyResult)
        return bitmap
    }

    private fun toFrame(bitmap: Bitmap): CapturedFrame {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val full = LumaMath.boxDownsample(pixels, width, height, 1, ByteArray(width * height))
        val quarter = LumaMath.boxDownsample(
            pixels, width, height,
            Tuning.LUMA_PYRAMID_QUARTER,
            ByteArray(width / Tuning.LUMA_PYRAMID_QUARTER * (height / Tuning.LUMA_PYRAMID_QUARTER)),
        )
        val tiny = LumaMath.boxDownsample(
            pixels, width, height,
            Tuning.LUMA_PYRAMID_TINY,
            ByteArray(width / Tuning.LUMA_PYRAMID_TINY * (height / Tuning.LUMA_PYRAMID_TINY)),
        )
        return CapturedFrame(
            bitmap = bitmap,
            luma = LumaPyramid(full, quarter, tiny),
            rotation = 0,
            systemBars = VerticalInsets(0, 0),
            timestampUptime = System.currentTimeMillis(),
            secureBlack = false,
            onRecycle = {},
        )
    }
}
