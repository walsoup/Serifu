package app.serifu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.serifu.capture.CapturedFrame
import app.serifu.capture.LumaPyramid
import app.serifu.capture.VerticalInsets
import app.serifu.coordinator.PagePipeline
import app.serifu.core.Tuning
import app.serifu.core.image.LumaMath
import app.serifu.core.model.SourceLanguage
import app.serifu.ml.MlKitTranslationEngine
import app.serifu.ml.OcrEngine
import app.serifu.overlay.LetteringRenderer
import app.serifu.overlay.RenderPage
import app.serifu.settings.Settings
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Renders the checked-in SVG panels, letters them with ML Kit, and writes
 * original/translated pairs where CI can pull them.
 */
@RunWith(AndroidJUnit4::class)
class SvgPanelPairTest {
    @Test
    fun panelsProduceTranslatedPairs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val ocr = OcrEngine()
        val translator = MlKitTranslationEngine(wifiOnly = { false })
        val pipeline = PagePipeline(context, translator, LetteringRenderer(context), ocr)
        try {
            for (name in listOf("greeting", "note")) {
                val original = assets.open("panels/$name.svg").use { SvgPanelRaster.bitmap(it) }
                try {
                    save(original, "serifu-pair-$name-original.png")
                    val frame = toFrame(original)
                    try {
                        val settings = Settings(sourceLanguage = SourceLanguage.JA, targetLanguage = "en")
                        val lines = runBlocking { ocr.recognize(frame.bitmap, SourceLanguage.JA) }
                        assertFalse("$name: OCR found no lines", lines.isEmpty())
                        val page = runBlocking {
                            pipeline.letter(lines, frame, settings, null, emptyList()) { System.currentTimeMillis() }
                        }.page
                        assertTrue("$name: pipeline produced no bubbles. lines=${lines.map { it.text }}", page != null && page.bubbles.isNotEmpty())
                        val lettered = page!!
                        val translated = original.copy(Bitmap.Config.ARGB_8888, true)
                        PanelCompositor.draw(translated, lettered)
                        save(translated, "serifu-pair-$name-translated.png")
                        translated.recycle()
                        val english = lettered.bubbles.any { bubble ->
                            bubble.layout.text.toString().any { it in 'A'..'Z' || it in 'a'..'z' }
                        }
                        assertTrue(
                            "$name: lettering stayed untranslated: " +
                                lettered.bubbles.joinToString { it.layout.text.toString() },
                            english,
                        )
                    } finally {
                        frame.recycle()
                    }
                } finally {
                    if (!original.isRecycled) original.recycle()
                }
            }
        } finally {
            translator.close()
            ocr.close()
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png")
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        )
        checkNotNull(uri) { "could not create $name" }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "could not write $name" }
        }
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

private object PanelCompositor {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val strokeRect = RectF()

    fun draw(bitmap: Bitmap, page: RenderPage) {
        val canvas = Canvas(bitmap)
        for (bubble in page.bubbles) {
            canvas.save()
            if (bubble.rotationDeg != 0f) {
                canvas.rotate(bubble.rotationDeg, bubble.patch.centerX(), bubble.patch.centerY())
            }
            fillPaint.color = bubble.fillColor
            val clean = bubble.cleanPath
            if (clean != null) {
                canvas.drawPath(clean, fillPaint)
                if (bubble.seamless) {
                    strokePaint.strokeWidth = Tuning.CLEAN_FEATHER_PX
                    strokePaint.color = bubble.fillColor
                    strokePaint.alpha = Tuning.PATCH_EDGE_STROKE_ALPHA
                    canvas.drawPath(clean, strokePaint)
                }
            } else {
                canvas.drawRoundRect(bubble.patch, bubble.patchCornerPx, bubble.patchCornerPx, fillPaint)
                if (bubble.seamless) {
                    strokePaint.strokeWidth = Tuning.PATCH_EDGE_STROKE_DP
                    strokePaint.color = bubble.fillColor
                    strokePaint.alpha = Tuning.PATCH_EDGE_STROKE_ALPHA
                    strokeRect.set(bubble.patch)
                    canvas.drawRoundRect(strokeRect, bubble.patchCornerPx, bubble.patchCornerPx, strokePaint)
                }
            }
            canvas.restore()
        }
        for (bubble in page.bubbles) {
            canvas.save()
            if (bubble.rotationDeg != 0f) {
                canvas.rotate(bubble.rotationDeg, bubble.patch.centerX(), bubble.patch.centerY())
            }
            canvas.translate(bubble.layoutOrigin.x, bubble.layoutOrigin.y)
            bubble.layout.draw(canvas)
            canvas.restore()
        }
    }
}

private object SvgPanelRaster {
    fun bitmap(xml: InputStream): Bitmap {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml, null)
        var width = 1
        var height = 1
        val commands = ArrayList<Draw>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "svg" -> {
                        width = (number(parser, "width") ?: width.toFloat()).toInt()
                        height = (number(parser, "height") ?: height.toFloat()).toInt()
                    }
                    "rect" -> commands += rect(parser)
                    "ellipse" -> commands += ellipse(parser)
                    "text" -> {
                        commands += text(parser)
                        continue
                    }
                }
            }
            parser.next()
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (command in commands) command.draw(canvas, paint)
        return bitmap
    }

    private fun rect(parser: XmlPullParser): Draw {
        val x = number(parser, "x") ?: 0f
        val y = number(parser, "y") ?: 0f
        val w = number(parser, "width") ?: 0f
        val h = number(parser, "height") ?: 0f
        return Shape(
            fill = color(parser, "fill"),
            stroke = color(parser, "stroke"),
            strokeWidth = number(parser, "stroke-width") ?: 1f,
        ) { canvas, paint ->
            canvas.drawRect(x, y, x + w, y + h, paint)
        }
    }

    private fun ellipse(parser: XmlPullParser): Draw {
        val cx = number(parser, "cx") ?: 0f
        val cy = number(parser, "cy") ?: 0f
        val rx = number(parser, "rx") ?: 0f
        val ry = number(parser, "ry") ?: 0f
        val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        return Shape(
            fill = color(parser, "fill"),
            stroke = color(parser, "stroke"),
            strokeWidth = number(parser, "stroke-width") ?: 1f,
        ) { canvas, paint ->
            canvas.drawOval(oval, paint)
        }
    }

    private fun text(parser: XmlPullParser): Draw {
        val x = number(parser, "x") ?: 0f
        val y = number(parser, "y") ?: 0f
        val size = number(parser, "font-size") ?: 32f
        val fill = color(parser, "fill") ?: Color.BLACK
        val center = parser.getAttributeValue(null, "text-anchor") == "middle"
        val body = StringBuilder()
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "text") {
            if (parser.eventType == XmlPullParser.TEXT) body.append(parser.text)
        }
        return Draw { canvas, paint ->
            paint.style = Paint.Style.FILL
            paint.color = fill
            paint.textSize = size
            paint.typeface = Typeface.SANS_SERIF
            paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
            canvas.drawText(body.toString().trim(), x, y, paint)
        }
    }

    private fun number(parser: XmlPullParser, name: String): Float? =
        parser.getAttributeValue(null, name)?.filter { it.isDigit() || it == '.' || it == '-' }?.toFloatOrNull()

    private fun color(parser: XmlPullParser, name: String): Int? {
        val raw = parser.getAttributeValue(null, name) ?: return null
        if (raw == "none") return null
        return Color.parseColor(raw)
    }

    private fun interface Draw {
        fun draw(canvas: Canvas, paint: Paint)
    }

    private class Shape(
        val fill: Int?,
        val stroke: Int?,
        val strokeWidth: Float,
        val mark: (Canvas, Paint) -> Unit,
    ) : Draw {
        override fun draw(canvas: Canvas, paint: Paint) {
            if (fill != null) {
                paint.style = Paint.Style.FILL
                paint.color = fill
                mark(canvas, paint)
            }
            if (stroke != null) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeWidth
                paint.color = stroke
                mark(canvas, paint)
            }
        }
    }
}
