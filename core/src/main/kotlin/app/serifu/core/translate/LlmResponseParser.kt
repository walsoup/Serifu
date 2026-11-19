package app.serifu.core.translate

import app.serifu.core.Tuning
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses the LLM JSON reply from plan.md 11.5.
 * Any missing or empty id falls back to the ML Kit text for that bubble.
 * Total decode failure falls back to the whole [fallbackById] map.
 */
object LlmResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class Entry(
        val id: Int = -1,
        val text: String = "",
    )

    @Serializable
    private data class Reply(
        @SerialName("t")
        val items: List<Entry> = emptyList(),
    )

    /**
     * @param budgets bubble id to char budget; a translation longer than
     *   `budget * [Tuning.LLM_TRANSLATION_LENGTH_FACTOR]` is treated as a
     *   hijacked reply shape and falls back for that id only.
     */
    fun parse(
        raw: String,
        fallbackById: Map<Int, String>,
        budgets: Map<Int, Int>? = null,
    ): Map<Int, String> {
        if (raw.length > Tuning.LLM_RESPONSE_MAX_CHARS) return HashMap(fallbackById)
        val reply = extractReply(raw) ?: return HashMap(fallbackById)
        val decoded = HashMap<Int, String>()
        for (entry in reply.items) {
            if (entry.id < 0) continue
            val cleaned = stripWrappingQuotes(entry.text.trim())
            if (cleaned.isEmpty()) continue
            val budget = budgets?.get(entry.id)
            if (budget != null && budget > 0 &&
                cleaned.length > budget * Tuning.LLM_TRANSLATION_LENGTH_FACTOR
            ) {
                continue
            }
            // Duplicate ids: last wins (documented; benign).
            decoded[entry.id] = cleaned
        }
        if (decoded.isEmpty()) return HashMap(fallbackById)
        val merged = HashMap<Int, String>(fallbackById)
        for ((id, text) in decoded) {
            if (id in fallbackById) merged[id] = text
        }
        return merged
    }

    /**
     * Brace spans, outermost first then inside-out: prose carrying a brace
     * before, between, or after the JSON must not sink a valid reply. Bounded.
     */
    private fun extractReply(raw: String): Reply? {
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        for (i in raw.indices) {
            when (raw[i]) {
                '{' -> starts.add(i)
                '}' -> ends.add(i)
            }
        }
        if (starts.isEmpty() || ends.isEmpty()) return null
        fun attempt(start: Int, end: Int): Reply? {
            if (end <= start) return null
            return try {
                val reply = json.decodeFromString(Reply.serializer(), raw.substring(start, end + 1))
                reply.takeIf { it.items.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }
        // Clean reply: first '{' to last '}'.
        attempt(starts.first(), ends.last())?.let { return it }
        var attempts = 1
        // Leading prose brace: later starts, same end.
        for (start in starts.drop(1)) {
            if (attempts++ >= MAX_SPAN_ATTEMPTS) return null
            attempt(start, ends.last())?.let { return it }
        }
        // Trailing prose brace: same start, earlier ends.
        for (end in ends.dropLast(1).asReversed()) {
            if (attempts++ >= MAX_SPAN_ATTEMPTS) return null
            attempt(starts.first(), end)?.let { return it }
        }
        // Middle reply between prose braces: remaining spans inside-out.
        for (start in starts.asReversed()) {
            for (end in ends) {
                if (end == ends.last() || start == starts.first()) continue
                if (attempts++ >= MAX_SPAN_ATTEMPTS) return null
                attempt(start, end)?.let { return it }
            }
        }
        return null
    }

    private fun stripWrappingQuotes(text: String): String {
        if (text.length >= 2) {
            val first = text.first()
            val last = text.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return text.substring(1, text.length - 1).trim()
            }
        }
        return text
    }

    private const val MAX_SPAN_ATTEMPTS = 64
}
