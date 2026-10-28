package app.serifu.ui.testpage

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.serifu.overlay.InkPaper

/**
 * Original sample chapter. Six vertical speech bubbles, one caption, one rotated sound effect.
 * Bubble columns sit far enough apart that milestone-3 clustering keeps all six separate.
 * Milestone 4 moves two of them into contact so the ink-wall check has something to split.
 */
@Composable
fun SamplePage(modifier: Modifier = Modifier) {
    val glyphPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkPaper.INK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    Canvas(modifier) {
        val ink = Color(InkPaper.INK)
        val margin = size.width * 0.05f
        val gap = size.width * 0.04f
        val panelHeight = (size.height - margin * 2f - gap * 3f) / 4f
        val char = size.width * 0.046f
        repeat(4) { index ->
            val top = margin + index * (panelHeight + gap)
            drawRect(
                color = ink,
                topLeft = Offset(margin, top),
                size = Size(size.width - margin * 2f, panelHeight),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        fun panelTop(index: Int) = margin + index * (panelHeight + gap)
        reader(size.width * 0.51f, panelTop(0) + panelHeight * 0.58f, char, ink)
        reader(size.width * 0.51f, panelTop(1) + panelHeight * 0.58f, char, ink)
        reader(size.width * 0.51f, panelTop(2) + panelHeight * 0.72f, char, ink)
        verticalBubble(
            centerX = size.width * 0.76f,
            top = panelTop(0) + char * 0.45f,
            text = "おはよう！今日も早いね",
            ruby = null,
            char = char,
            paint = glyphPaint,
        )
        verticalBubble(
            centerX = size.width * 0.26f,
            top = panelTop(0) + char * 0.45f,
            text = "先輩、待ってください！",
            ruby = "せんぱい",
            char = char,
            paint = glyphPaint,
        )
        verticalBubble(
            centerX = size.width * 0.76f,
            top = panelTop(1) + char * 0.7f,
            text = "…まさか、本当に来るとは",
            ruby = null,
            char = char,
            paint = glyphPaint,
        )
        verticalBubble(
            centerX = size.width * 0.26f,
            top = panelTop(1) + char * 1.5f,
            text = "お腹すいた",
            ruby = null,
            char = char,
            paint = glyphPaint,
        )
        caption(size.width * 0.5f, panelTop(2) + char * 0.28f, "屋上にて", char, glyphPaint)
        verticalBubble(
            centerX = size.width * 0.76f,
            top = panelTop(2) + panelHeight * 0.42f,
            text = "行こう",
            ruby = null,
            char = char,
            paint = glyphPaint,
        )
        verticalBubble(
            centerX = size.width * 0.26f,
            top = panelTop(2) + panelHeight * 0.42f,
            text = "約束だよ",
            ruby = null,
            char = char,
            paint = glyphPaint,
        )
        soundEffect(size.width * 0.52f, panelTop(3) + panelHeight * 0.5f, char * 2.7f, glyphPaint)
        reader(size.width * 0.24f, panelTop(3) + panelHeight * 0.62f, char * 0.8f, ink)
        reader(size.width * 0.8f, panelTop(3) + panelHeight * 0.62f, char * 0.8f, ink)
    }
}

private fun DrawScope.verticalBubble(
    centerX: Float,
    top: Float,
    text: String,
    ruby: String?,
    char: Float,
    paint: Paint,
) {
    val lines = text.codePointCount(0, text.length)
    val textHeight = lines * char * 1.08f
    val bubbleWidth = char * 3.3f
    val bubbleHeight = textHeight + char * 1.5f
    val left = centerX - bubbleWidth / 2f
    drawOval(Color.White, Offset(left, top), Size(bubbleWidth, bubbleHeight))
    drawOval(
        Color(InkPaper.INK),
        Offset(left, top),
        Size(bubbleWidth, bubbleHeight),
        style = Stroke(width = 2.dp.toPx()),
    )
    val tail = Path().apply {
        moveTo(centerX - char * 0.15f, top + bubbleHeight * 0.86f)
        lineTo(centerX - char * 0.9f, top + bubbleHeight + char * 0.55f)
        lineTo(centerX + char * 0.45f, top + bubbleHeight * 0.94f)
        close()
    }
    drawPath(tail, Color.White)
    drawPath(tail, Color(InkPaper.INK), style = Stroke(width = 2.dp.toPx()))
    paint.textSize = char
    var baseline = top + char * 1.2f
    var index = 0
    var offset = 0
    val native = drawContext.canvas.nativeCanvas
    while (offset < text.length) {
        val code = text.codePointAt(offset)
        native.drawText(String(Character.toChars(code)), centerX, baseline, paint)
        if (ruby != null && index == 0) {
            paint.textSize = char * 0.42f
            var rubyBaseline = baseline
            var rubyOffset = 0
            while (rubyOffset < ruby.length) {
                val rubyCode = ruby.codePointAt(rubyOffset)
                native.drawText(
                    String(Character.toChars(rubyCode)),
                    centerX + char * 0.95f,
                    rubyBaseline,
                    paint,
                )
                rubyBaseline += char * 0.48f
                rubyOffset += Character.charCount(rubyCode)
            }
            paint.textSize = char
        }
        baseline += char * 1.08f
        offset += Character.charCount(code)
        index++
    }
}

private fun DrawScope.caption(centerX: Float, top: Float, text: String, char: Float, paint: Paint) {
    paint.textSize = char
    val textWidth = paint.measureText(text)
    val width = textWidth + char * 1.6f
    val height = char * 1.7f
    val left = centerX - width / 2f
    drawRect(Color.White, Offset(left, top), Size(width, height))
    drawRect(Color(InkPaper.INK), Offset(left, top), Size(width, height), style = Stroke(width = 2.dp.toPx()))
    drawContext.canvas.nativeCanvas.drawText(text, centerX, top + height * 0.68f, paint)
}

private fun DrawScope.soundEffect(centerX: Float, centerY: Float, char: Float, paint: Paint) {
    paint.textSize = char
    rotate(24f, Offset(centerX, centerY)) {
        drawContext.canvas.nativeCanvas.drawText("ドン", centerX, centerY, paint)
    }
}

private fun DrawScope.reader(x: Float, y: Float, char: Float, ink: Color) {
    val stroke = Stroke(width = 2.dp.toPx())
    drawCircle(ink, radius = char * 0.55f, center = Offset(x, y - char * 1.3f), style = stroke)
    drawLine(ink, Offset(x, y - char * 0.7f), Offset(x, y + char * 0.9f), strokeWidth = 2.dp.toPx())
    drawLine(ink, Offset(x - char * 0.7f, y), Offset(x + char * 0.7f, y), strokeWidth = 2.dp.toPx())
    drawLine(ink, Offset(x, y + char * 0.9f), Offset(x - char * 0.45f, y + char * 1.8f), strokeWidth = 2.dp.toPx())
    drawLine(ink, Offset(x, y + char * 0.9f), Offset(x + char * 0.45f, y + char * 1.8f), strokeWidth = 2.dp.toPx())
}
