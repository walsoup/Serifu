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
import app.serifu.overlay.RenderPage
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
 * End-to-end pipeline check on the stick-figure sample page.
 * The plant says it is plastic. Lettering has to come back in the target language
 * and stay inside each bubble.
 */
@RunWith(AndroidJUnit4::class)
class PipelineOnTestPageTest {
    @Test
    fun testPageLettersCompletely() {
        val scenario = ActivityScenario.launch<TestPageActivity>(pageIntent())
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
            try {
                val (top, topLines) = letterVisibleScreen(scenario, pipeline, ocr)
                assertNotNull("pipeline produced no page", top)
                val dialogue = top!!.bubbles.count { it.kind == BubbleKind.DIALOGUE }
                assertTrue(diag(top, "top", topLines), dialogue >= 1)
                assertTrue(
                    "plastic plant line missing: " + top.debugClusters.map { it.sourceText },
                    top.debugClusters.any { it.sourceText.contains("プラスチック") },
                )
                checkBubbles(top)

                val bottomIntent = pageIntent(startBottom = true)
                val bottomScenario = ActivityScenario.launch<TestPageActivity>(bottomIntent)
                try {
                    val (bottom, bottomLines) = letterVisibleScreen(bottomScenario, pipeline, ocr)
                    assertNotNull("pipeline produced no page at bottom", bottom)
                    assertTrue(diag(bottom!!, "bottom", bottomLines), bottom.bubbles.isNotEmpty())
                    checkBubbles(bottom)
                } finally {
                    bottomScenario.close()
                }
            } finally {
                translator.close()
                ocr.close()
            }
        } finally {
            scenario.close()
        }
    }

    private fun checkBubbles(page: RenderPage) {
        for (bubble in page.bubbles) {
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
    }

    private fun diag(page: RenderPage, where: String, lineDiag: String = ""): String = buildString {
        append("$where bubbles=${page.bubbles.size}")
        for (b in page.bubbles) {
            append(" [bid=${b.id} kind=${b.kind} txt=${b.layout.text.toString().take(14)}]")
        }
        append(" debugClusters=${page.debugClusters.size}")
        for (c in page.debugClusters) {
            append(" [cid=${c.id} kind=${c.kind} src=${c.sourceText.take(14)}]")
        }
        if (lineDiag.isNotEmpty()) append(lineDiag)
    }

    private fun letterVisibleScreen(
        scenario: ActivityScenario<TestPageActivity>,
        pipeline: PagePipeline,
        ocr: OcrEngine,
    ): Pair<RenderPage?, String> {
        val bitmap = captureWindow(scenario)
        try {
            saveShot(bitmap)
            val frame = toFrame(bitmap)
            try {
                val settings = Settings(sourceLanguage = SourceLanguage.JA, targetLanguage = "en")
                val lines = runBlocking { ocr.recognize(frame.bitmap, SourceLanguage.JA) }
                assertFalse("OCR found no lines on the test page", lines.isEmpty())
                val lineDiag = buildString {
                    append(" lines=${lines.size}")
                    for (l in lines) {
                        append(" [ocr=${l.text.take(14)} conf=${"%.2f".format(l.confidence)} box=${l.box} ang=${"%.0f".format(l.angleDeg)}]")
                    }
                }
                val page = runBlocking {
                    pipeline.letter(lines, frame, settings, null, emptyList()) { System.currentTimeMillis() }
                }.page
                return page to lineDiag
            } finally {
                frame.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
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

    private var shotIndex = 0

    /** Persists each capture where CI can pull it (internal + shared Downloads). */
    private fun saveShot(bitmap: Bitmap) {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            try {
                val dir = java.io.File(context.filesDir, "shots")
                dir.mkdirs()
                java.io.File(dir, "shot-${shotIndex}.png").outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Exception) {
            }
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "serifu-shot-${shotIndex}.png")
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png")
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } catch (_: Exception) {
            }
            shotIndex++
        } catch (_: Exception) {
        }
    }

    private fun pageIntent(startBottom: Boolean = false) = android.content.Intent(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TestPageActivity::class.java,
    ).putExtra(TestPageActivity.EXTRA_LANGUAGE, "ja")
        .putExtra(TestPageActivity.EXTRA_START_BOTTOM, startBottom)

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
