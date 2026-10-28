package app.serifu.core.translate

import app.serifu.core.model.TargetLanguage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Builds the exact manga-translation prompt from plan.md 11.5.
 * Pure function of the page request; no Android dependencies.
 */
object PromptBuilder {
    private val json = Json

    fun build(request: PageTranslationRequest): String {
        val sourceName = request.source.englishName
        val targetName = TargetLanguage.fromTag(request.target).englishName
        val honorificsRule = if (request.keepHonorifics) {
            "Keep Japanese honorifics such as -san, -kun, -chan, -sama and -senpai."
        } else {
            "Translate or drop honorifics naturally."
        }
        val previous = if (request.previousPageLines.isEmpty()) {
            "None"
        } else {
            request.previousPageLines.joinToString("\n")
        }
        val bubblesJson = buildJsonArray {
            for (bubble in request.bubbles) {
                add(
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(bubble.id),
                            "kind" to JsonPrimitive(bubble.kind.name.lowercase()),
                            "max" to JsonPrimitive(bubble.charBudget),
                            "src" to JsonPrimitive(bubble.text),
                        ),
                    ),
                )
            }
        }
        val bubblesSerialized = json.encodeToString(JsonArray.serializer(), bubblesJson)
        return buildString {
            append("You are a professional manga translator and letterer. ")
            append("Translate the ")
            append(sourceName)
            append(" text below into natural, idiomatic ")
            append(targetName)
            append(", the way an official published translation would read.")
            append("\n\nRules:\n")
            append("- Bubbles are listed in reading order. Use the neighbouring bubbles to work out who is speaking, pronouns and tone.\n")
            append("- Each bubble must fit its speech balloon: stay within \"max\" characters for that bubble.\n")
            append("- ")
            append(honorificsRule)
            append("\n")
            append("- Keep character names in their usual romanization.\n")
            append("- For kind \"sfx\", give a short English sound effect in capitals (for example THUD, BA-DUMP, RUSTLE). If there is no good equivalent, romanize it.\n")
            append("- The text came from OCR and may contain small errors. Translate what the author most plausibly wrote.\n")
            append("- Never add notes, explanations or quotation marks around the translation.\n")
            append("- Reply with JSON only, exactly in this shape: {\"t\":[{\"id\":0,\"text\":\"...\"}]} with one entry for every input id.")
            append("\n\nPrevious page (context only, do not translate):\n")
            append(previous)
            append("\n\nBubbles:\n")
            append(bubblesSerialized)
        }
    }
}
