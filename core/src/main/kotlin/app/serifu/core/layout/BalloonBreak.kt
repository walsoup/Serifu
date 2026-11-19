package app.serifu.core.layout

/**
 * Line breaks for a speech balloon. Words stay whole, short function words
 * don't end a line, and a one-word last line is pulled up when it fits.
 * CJK targets ([cjk]) pack characters instead of words.
 */
object BalloonBreak {
    private val whitespace = Regex("\\s+")
    private val keepWithNext = setOf(
        "a", "an", "the", "to", "of", "and", "or", "but", "in", "on", "at", "for",
        "nor", "as", "if", "so", "by", "my", "your", "i",
    )

    fun lines(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        cjk: Boolean = false,
    ): List<String> {
        if (text.contains('\n')) {
            return text.split('\n').flatMap { part ->
                if (part.isBlank()) emptyList() else breakParagraph(part, maxWidth, measure, cjk)
            }
        }
        return breakParagraph(text, maxWidth, measure, cjk)
    }

    private fun breakParagraph(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        cjk: Boolean,
    ): List<String> {
        val sep = if (cjk) "" else " "
        val words = if (cjk) {
            // Characters pack; whitespace segments stay separated by one space token.
            text.trim().split(whitespace).filter { it.isNotEmpty() }
                .flatMapIndexed { index, segment ->
                    val chars = segment.map { it.toString() }
                    if (index == 0) chars else listOf(" ") + chars
                }
        } else {
            text.trim().split(whitespace).filter { it.isNotEmpty() }
        }
        if (words.isEmpty()) return emptyList()
        val whole = words.joinToString(sep)
        if (maxWidth <= 0f || measure(whole) <= maxWidth) return listOf(whole)
        return balance(pack(words, maxWidth, measure, cjk), maxWidth, measure, cjk)
            .map { it.joinToString(sep).trim() }
            .filter { it.isNotEmpty() }
    }

    private fun pack(
        words: List<String>,
        maxWidth: Float,
        measure: (String) -> Float,
        cjk: Boolean,
    ): MutableList<MutableList<String>> {
        val sep = if (cjk) "" else " "
        val lines = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (word in words) {
            if (current.isEmpty()) {
                current.add(word)
                continue
            }
            val trial = current.joinToString(sep) + sep + word
            if (measure(trial) <= maxWidth) {
                current.add(word)
                continue
            }
            val held = current.last()
            if (!cjk && current.size > 1 && held.lowercase() in keepWithNext) {
                val carried = mutableListOf(held, word)
                if (measure(carried.joinToString(" ")) <= maxWidth) {
                    val remaining = current.dropLast(1).toMutableList()
                    // The shortened line must not itself end on a function word.
                    while (remaining.size > 1 && remaining.last().lowercase() in keepWithNext) {
                        val extended = (listOf(remaining.last()) + carried).joinToString(" ")
                        if (measure(extended) > maxWidth) break
                        carried.add(0, remaining.removeAt(remaining.lastIndex))
                    }
                    lines.add(remaining)
                    current = carried
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
        cjk: Boolean,
    ): List<List<String>> {
        if (lines.size < 2) return lines
        val sep = if (cjk) "" else " "
        val last = lines.last()
        val prev = lines[lines.size - 2]
        while (prev.size > 1 && last.size < prev.size) {
            // Never leave the previous line ending on a function word.
            if (!cjk && prev.size > 2 && prev[prev.size - 2].lowercase() in keepWithNext) break
            val moved = prev.last()
            val trialLast = (listOf(moved) + last).joinToString(sep)
            if (measure(trialLast) > maxWidth) break
            val trialPrev = prev.dropLast(1).joinToString(sep)
            val before = kotlin.math.abs(measure(prev.joinToString(sep)) - measure(last.joinToString(sep)))
            val after = kotlin.math.abs(measure(trialPrev) - measure(trialLast))
            if (last.size > 1 && after >= before) break
            prev.removeAt(prev.lastIndex)
            last.add(0, moved)
        }
        return lines
    }
}
