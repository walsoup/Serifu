package app.serifu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.serifu.ui.design.Serifu

/**
 * A manga-panel container: crisp ink border, 8dp corners, raised paper surface.
 */
@Composable
fun MangaPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    borderColor: Color = Serifu.colors.text.copy(alpha = 0.85f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Serifu.colors.surface)
            .border(1.5.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = contentDescription,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(18.dp),
        content = content,
    )
}

/**
 * A genuine manga speech bubble with an ink outline and tail, rendering dialogue preview.
 */
@Composable
fun SpeechBubblePreview(
    text: String,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    fontLabel: String? = null,
) {
    val c = Serifu.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 1.5.dp.toPx()
                val bubbleWidth = size.width * 0.88f
                val bubbleHeight = size.height * 0.76f
                val left = (size.width - bubbleWidth) / 2f
                val top = 8.dp.toPx()

                val path = Path().apply {
                    addOval(Rect(left, top, left + bubbleWidth, top + bubbleHeight))
                    // Speech bubble tail
                    moveTo(left + bubbleWidth * 0.38f, top + bubbleHeight - 2.dp.toPx())
                    lineTo(left + bubbleWidth * 0.28f, top + bubbleHeight + 16.dp.toPx())
                    lineTo(left + bubbleWidth * 0.46f, top + bubbleHeight - 2.dp.toPx())
                }

                // Fill bubble with pristine surface
                drawPath(path, c.surface)
                // Outline bubble with ink
                drawPath(path, c.text, style = Stroke(width = strokeWidth))
            }

            Text(
                text = text,
                fontFamily = fontFamily,
                fontSize = 18.sp,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .padding(bottom = 12.dp),
            )
        }

        if (fontLabel != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = fontLabel,
                style = Serifu.type.monoTiny,
                color = c.textSecondary,
            )
        }
    }
}

/**
 * Manga screentone dot grid drawn in Compose Canvas:
 * Diagonal 8dp spacing, Screentone grey at 16% alpha.
 */
@Composable
fun HalftoneScreentone(
    modifier: Modifier = Modifier,
    dotColor: Color = Serifu.colors.muted.copy(alpha = 0.22f),
    spacing: Dp = 8.dp,
    dotRadius: Dp = 1.2.dp,
) {
    Canvas(modifier) {
        val step = spacing.toPx()
        val radius = dotRadius.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            val offsetX = if (row % 2 == 1) step / 2f else 0f
            var x = offsetX
            while (x < size.width) {
                drawCircle(dotColor, radius, Offset(x, y))
                x += step
            }
            y += step
            row++
        }
    }
}
