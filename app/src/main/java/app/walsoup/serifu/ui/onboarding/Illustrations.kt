package app.walsoup.serifu.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.walsoup.serifu.ui.design.Serifu

/**
 * 14.8.1 Welcome Illustration:
 * Three overlapping manga panels; the front panel holds a speech bubble
 * transitioning from vertical Japanese glyphs into horizontal lettered lines.
 */
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Canvas(modifier) {
        val ink = c.text
        val paper = c.surface
        val stroke = Stroke(width = 1.5.dp.toPx())

        fun panel(left: Float, top: Float, w: Float, h: Float) {
            drawRoundRect(paper, Offset(left, top), Size(w, h), CornerRadius(4.dp.toPx()))
            drawRoundRect(ink, Offset(left, top), Size(w, h), CornerRadius(4.dp.toPx()), style = stroke)
        }

        // Back panels
        panel(size.width * 0.06f, size.height * 0.20f, size.width * 0.38f, size.height * 0.60f)
        panel(size.width * 0.24f, size.height * 0.12f, size.width * 0.38f, size.height * 0.60f)

        // Front focus panel
        val fx = size.width * 0.44f
        val fy = size.height * 0.16f
        val fw = size.width * 0.48f
        val fh = size.height * 0.68f
        panel(fx, fy, fw, fh)

        // Speech bubble inside the front panel
        val bubble = Path().apply {
            addOval(Rect(fx + fw * 0.10f, fy + fh * 0.18f, fx + fw * 0.90f, fy + fh * 0.72f))
        }
        drawPath(bubble, paper)
        drawPath(bubble, ink, style = stroke)

        // Lettered lines inside the bubble
        val lineStroke = 2.dp.toPx()
        repeat(3) { i ->
            val y = fy + fh * (0.34f + i * 0.10f)
            val startX = fx + fw * 0.24f
            val endX = fx + fw * (0.76f - i * 0.08f)
            drawLine(c.primary, Offset(startX, y), Offset(endX, y), lineStroke)
        }
    }
}

/**
 * 14.8.2 Language Seals:
 * Three traditional red Hanko seal stamps (日, 中, 한) stamped dynamically.
 */
@Composable
fun LanguageSeals(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Canvas(modifier) {
        val seals = listOf(
            Triple(-6f, 0.24f, "日"),
            Triple(2f, 0.50f, "中"),
            Triple(7f, 0.76f, "한"),
        )
        seals.forEach { (angle, cx, glyph) ->
            val pivot = Offset(size.width * cx, size.height * 0.5f)
            rotate(angle, pivot) {
                val side = 52.dp.toPx()
                val left = pivot.x - side / 2f
                val top = pivot.y - side / 2f
                val radius = CornerRadius(6.dp.toPx())

                // Hanko vermilion seal
                drawRoundRect(c.primary, Offset(left, top), Size(side, side), radius)
                drawRoundRect(c.text, Offset(left, top), Size(side, side), radius, style = Stroke(1.dp.toPx()))

                drawContext.canvas.nativeCanvas.drawText(
                    glyph,
                    pivot.x,
                    pivot.y + 7.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = c.onPrimary.toArgb()
                        textSize = 20.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    },
                )
            }
        }
    }
}

/**
 * 14.8.3 Restricted Settings:
 * Simplified device diagram showing the 3-dots menu and the highlighted row.
 */
@Composable
fun RestrictedIllustration(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx())
        val left = size.width * 0.25f
        val top = size.height * 0.08f
        val w = size.width * 0.50f
        val h = size.height * 0.84f

        drawRoundRect(c.surface, Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()))
        drawRoundRect(c.text, Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()), style = stroke)

        // Highlighted menu row
        val rowTop = top + h * 0.38f
        drawRoundRect(c.container, Offset(left + 8.dp.toPx(), rowTop), Size(w - 16.dp.toPx(), 26.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawRoundRect(c.primary, Offset(left + 8.dp.toPx(), rowTop), Size(w - 16.dp.toPx(), 26.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Three dots menu (top right)
        drawCircle(c.text, 2.5.dp.toPx(), Offset(left + w - 16.dp.toPx(), top + 16.dp.toPx()))
        drawCircle(c.text, 2.5.dp.toPx(), Offset(left + w - 16.dp.toPx(), top + 26.dp.toPx()))
        drawCircle(c.text, 2.5.dp.toPx(), Offset(left + w - 16.dp.toPx(), top + 36.dp.toPx()))
    }
}

/**
 * 14.8.4 Enable Service Illustration:
 * Phone outline with accessibility toggle switching on and the floating pill attached.
 */
@Composable
fun EnableIllustration(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx())
        val left = size.width * 0.25f
        val top = size.height * 0.08f
        val w = size.width * 0.50f
        val h = size.height * 0.84f

        drawRoundRect(c.surface, Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()))
        drawRoundRect(c.text, Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()), style = stroke)

        // Switch track
        val switchLeft = left + w * 0.45f
        val switchTop = top + h * 0.42f
        drawRoundRect(c.primary, Offset(switchLeft, switchTop), Size(w * 0.38f, 18.dp.toPx()), CornerRadius(9.dp.toPx()))

        // Floating pill on the edge of the device
        val pillWidth = 14.dp.toPx()
        val pillHeight = 36.dp.toPx()
        drawRoundRect(c.text, Offset(left + w - pillWidth * 0.5f, top + h * 0.25f), Size(pillWidth, pillHeight), CornerRadius(7.dp.toPx()))
    }
}

/**
 * 14.8.5 Try It Illustration:
 * Speech bubble with a stamped checkmark seal.
 */
@Composable
fun TryIllustration(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx())
        val bubble = Path().apply {
            addOval(
                Rect(
                    size.width * 0.20f,
                    size.height * 0.16f,
                    size.width * 0.80f,
                    size.height * 0.82f,
                ),
            )
        }
        drawPath(bubble, c.surface)
        drawPath(bubble, c.text, style = stroke)

        // Hanko check seal
        val side = 32.dp.toPx()
        val sealLeft = size.width * 0.60f
        val sealTop = size.height * 0.20f
        drawRoundRect(c.primary, Offset(sealLeft, sealTop), Size(side, side), CornerRadius(4.dp.toPx()))

        // Check mark in white
        val checkPath = Path().apply {
            moveTo(sealLeft + side * 0.25f, sealTop + side * 0.50f)
            lineTo(sealLeft + side * 0.45f, sealTop + side * 0.72f)
            lineTo(sealLeft + side * 0.78f, sealTop + side * 0.28f)
        }
        drawPath(checkPath, c.onPrimary, style = Stroke(width = 2.5.dp.toPx()))
    }
}
