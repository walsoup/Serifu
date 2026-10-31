package app.serifu.core.vision

import app.serifu.core.model.SourceLanguage
import java.text.Normalizer

object TextNormalizer {
    private val whitespace = Regex("\\s+")
    private val koreanBreaks = Regex("[\\t\\n\\r]+")
    private val koreanSpaces = Regex(" {2,}")
    private val ellipsisRun = Regex("[…⋯.．・]{2,}")

    fun normalize(text: String, source: SourceLanguage): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val spaced = if (source == SourceLanguage.JA || source == SourceLanguage.ZH) {
            composed.replace(whitespace, "")
        } else {
            composed.replace(koreanBreaks, " ").replace(whitespace, " ").trim()
        }
        return spaced.replace(ellipsisRun, "…")
    }

    fun cacheKey(source: SourceLanguage, normalized: String): String = "${source.tag}|$normalized"
}
