package app.serifu.core.translate

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

    fun parse(raw: String, fallbackById: Map<Int, String>): Map<Int, String> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return HashMap(fallbackById)
        val candidate = raw.substring(start, end + 1)
        val reply = try {
            json.decodeFromString(Reply.serializer(), candidate)
        } catch (_: Exception) {
            return HashMap(fallbackById)
        }
        val decoded = HashMap<Int, String>()
        for (entry in reply.items) {
            if (entry.id < 0) continue
            val cleaned = stripWrappingQuotes(entry.text.trim())
            if (cleaned.isEmpty()) continue
            decoded[entry.id] = cleaned
        }
        if (decoded.isEmpty()) return HashMap(fallbackById)
        val merged = HashMap<Int, String>(fallbackById)
        for ((id, text) in decoded) {
            if (id in fallbackById) merged[id] = text
        }
        return merged
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
}
