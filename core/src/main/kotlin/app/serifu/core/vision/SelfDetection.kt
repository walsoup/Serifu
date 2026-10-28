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
            if (prefix.isNotEmpty() && shown.any { it == prefix || it.startsWith(prefix) || prefix.startsWith(it) }) {
                hits++
            }
        }
        return hits >= Tuning.SELF_DETECT_LATIN_LINES
    }

    private fun prefixOf(text: String): String =
        text.trim().take(Tuning.SELF_DETECT_PREFIX_CHARS).lowercase()
}
