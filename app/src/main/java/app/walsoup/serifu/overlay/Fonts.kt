package app.walsoup.serifu.overlay

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import app.walsoup.serifu.R
import app.serifu.core.model.LetteringFont

object SerifuFonts {
    @Composable
    fun lettering(font: LetteringFont): FontFamily = when (font) {
        LetteringFont.COMIC_NEUE -> remember { FontFamily(Font(R.font.comic_neue_bold, FontWeight.Bold)) }
        LetteringFont.ATKINSON -> remember { FontFamily(Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold)) }
        LetteringFont.PATRICK_HAND -> remember { FontFamily(Font(R.font.patrick_hand_regular)) }
    }

    @Composable
    fun sfx(): FontFamily = remember { FontFamily(Font(R.font.bangers_regular)) }
}

object OverlayTypefaces {
    private val loaded = HashMap<Int, Typeface>()

    fun of(context: Context, font: LetteringFont): Typeface = load(
        context,
        when (font) {
            LetteringFont.COMIC_NEUE -> R.font.comic_neue_bold
            LetteringFont.ATKINSON -> R.font.atkinson_hyperlegible_bold
            LetteringFont.PATRICK_HAND -> R.font.patrick_hand_regular
        },
    ) ?: Typeface.DEFAULT_BOLD

    fun sfx(context: Context): Typeface = load(context, R.font.bangers_regular) ?: Typeface.DEFAULT_BOLD

    private fun load(context: Context, fontId: Int): Typeface? {
        loaded[fontId]?.let { return it }
        val typeface = ResourcesCompat.getFont(context, fontId) ?: return null
        loaded[fontId] = typeface
        return typeface
    }
}
