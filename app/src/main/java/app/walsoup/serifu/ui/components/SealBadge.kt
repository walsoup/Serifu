package app.walsoup.serifu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.walsoup.serifu.ui.design.Serifu

/**
 * Traditional Japanese vermilion Hanko seal badge for languages (JA, ZH, KO) and status.
 */
@Composable
fun SealBadge(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    size: Dp = 32.dp,
    textSize: TextUnit = 13.sp,
) {
    val shape = RoundedCornerShape(4.dp)
    val c = Serifu.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (filled) c.primary else c.container)
            .border(1.2.dp, if (filled) c.text else c.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (filled) c.onPrimary else c.textSecondary,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(1.dp),
        )
    }
}
