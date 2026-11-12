package app.walsoup.serifu.ml

import android.icu.text.Transliterator
import app.serifu.core.model.SourceLanguage
import java.util.Locale

/** Sound effects are romanized instead of translated. */
object SfxRomanizer {
    fun romanize(text: String, source: SourceLanguage): String {
        val id = when (source) {
            SourceLanguage.JA -> "Katakana-Latin; Hiragana-Latin"
            SourceLanguage.KO -> "Hangul-Latin"
            SourceLanguage.ZH -> "Han-Latin"
            else -> null
        }
        if (id == null) return text.uppercase(Locale.ENGLISH)
        val latin = try {
            Transliterator.getInstance(id).transliterate(text)
        } catch (_: IllegalArgumentException) {
            text
        }
        return latin.uppercase(Locale.ENGLISH)
    }
}
