package app.serifu.core.vision

import app.serifu.core.Tuning

/**
 * After OCR, drop a frame whose Latin lines are our own lettering and capture once more.
 * Matching uses the first 12 characters, case-insensitive.
 */
object SelfDetection {
    private val latinOnly = Regex("^[A-Za-z0-9\\p{Punct}\\s]+$")

    fun leaked(lines: List<OcrLine>, shownTexts: List<String>): Boolean {
        val shown = shownTexts.map { prefixOf(it) }.filter { it.isNotEmpty() }
        if (shown.isEmpty()) return false
        var hits = 0
        for (line in lines) {
            val text = line.text.trim()
            if (text.isEmpty() || !latinOnly.matches(text)) continue
            val prefix = prefixOf(text)
            if (prefix.isNotEmpty() && shown.any { matches(it, prefix) }) {
                hits++
            }
        }
        return hits >= Tuning.SELF_DETECT_LATIN_LINES
    }

    private fun matches(shown: String, prefix: String): Boolean {
        val overlap = when {
            shown == prefix -> shown.length
            shown.startsWith(prefix) -> prefix.length
            prefix.startsWith(shown) -> shown.length
            else -> 0
        }
        // Two-character OCR lines must not count as leak hits.
        return overlap >= Tuning.SELF_DETECT_MIN_MATCH_CHARS
    }

    private fun prefixOf(text: String): String =
        text.trim().take(Tuning.SELF_DETECT_PREFIX_CHARS).lowercase()
}
