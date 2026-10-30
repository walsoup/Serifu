package app.serifu.core.layout

/**
 * Line breaks for a speech balloon. Words stay whole, short function words
 * don't end a line, and a one-word last line is pulled up when it fits.
 */
object BalloonBreak {
    private val keepWithNext = setOf(
        "a", "an", "the", "to", "of", "and", "or", "but", "in", "on", "at", "for",
        "nor", "as", "if", "so", "by", "my", "your", "i",
    )

    fun lines(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (text.contains('\n')) {
            return text.split('\n').flatMap { part ->
                if (part.isBlank()) emptyList() else breakParagraph(part, maxWidth, measure)
            }
        }
        return breakParagraph(text, maxWidth, measure)
    }

    private fun breakParagraph(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val whole = words.joinToString(" ")
        if (maxWidth <= 0f || measure(whole) <= maxWidth) return listOf(whole)
        return balance(pack(words, maxWidth, measure), maxWidth, measure).map { it.joinToString(" ") }
    }

    private fun pack(
        words: List<String>,
        maxWidth: Float,
        measure: (String) -> Float,
    ): MutableList<MutableList<String>> {
        val lines = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (word in words) {
            if (current.isEmpty()) {
                current.add(word)
                continue
            }
            val trial = current.joinToString(" ") + " " + word
            if (measure(trial) <= maxWidth) {
                current.add(word)
                continue
            }
            val held = current.last()
            if (current.size > 1 && held.lowercase() in keepWithNext) {
                val remaining = current.dropLast(1)
                val heldWithNext = "$held $word"
                if (measure(heldWithNext) <= maxWidth) {
                    lines.add(remaining.toMutableList())
                    current = mutableListOf(held, word)
                    continue
                }
            }
            lines.add(current)
            current = mutableListOf(word)
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun balance(
        lines: MutableList<MutableList<String>>,
        maxWidth: Float,
        measure: (String) -> Float,
    ): List<List<String>> {
        if (lines.size < 2) return lines
        val last = lines.last()
        val prev = lines[lines.size - 2]
        while (prev.size > 1 && last.size < prev.size) {
            val moved = prev.last()
            val trialLast = (listOf(moved) + last).joinToString(" ")
            if (measure(trialLast) > maxWidth) break
            val trialPrev = prev.dropLast(1).joinToString(" ")
            val before = kotlin.math.abs(measure(prev.joinToString(" ")) - measure(last.joinToString(" ")))
            val after = kotlin.math.abs(measure(trialPrev) - measure(trialLast))
            if (last.size > 1 && after >= before) break
            prev.removeAt(prev.lastIndex)
            last.add(0, moved)
        }
        return lines
    }
}
