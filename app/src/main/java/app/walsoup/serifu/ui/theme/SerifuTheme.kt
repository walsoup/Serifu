package app.walsoup.serifu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.walsoup.serifu.R
import app.walsoup.serifu.ui.design.DarkPalette
import app.walsoup.serifu.ui.design.LightPalette
import app.walsoup.serifu.ui.design.LocalSerifuPalette

val DelaGothicOne = FontFamily(Font(R.font.dela_gothic_one_regular))

/**
 * Serifu's own Hanko design system: Paper, Ink and Hanko vermilion.
 * Dark mode simply inverts paper and ink.
 */
@Composable
fun SerifuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSerifuPalette provides if (darkTheme) DarkPalette else LightPalette,
        content = content,
    )
}
