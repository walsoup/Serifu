package app.serifu.core.translate

import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {
    private fun request(keepHonorifics: Boolean = true) = PageTranslationRequest(
        source = SourceLanguage.JA,
        target = "en",
        bubbles = listOf(
            BubbleText(0, BubbleKind.DIALOGUE, "おはよう！", 42),
            BubbleText(1, BubbleKind.SFX, "ドン", 8),
        ),
        previousPageLines = listOf("先輩、待ってください！"),
        keepHonorifics = keepHonorifics,
    )

    @Test
    fun templateShapeAndHonorificsOn() {
        val prompt = PromptBuilder.build(request(true))
        assertTrue(prompt.startsWith("You are a professional manga translator and letterer. Translate the Japanese text below into natural, idiomatic English"))
        assertTrue(prompt.contains("Keep Japanese honorifics such as -san, -kun, -chan, -sama and -senpai."))
        assertTrue(prompt.contains("Previous page (context only, do not translate):\n先輩、待ってください！"))
        assertTrue(prompt.contains("\"id\":0"))
        assertTrue(prompt.contains("\"kind\":\"dialogue\""))
        assertTrue(prompt.contains("\"max\":42"))
        assertTrue(prompt.contains("{\"t\":[{\"id\":0,\"text\":\"...\"}]}"))
    }

    @Test
    fun honorificsOffAndEmptyContext() {
        val prompt = PromptBuilder.build(request(false).copy(previousPageLines = emptyList()))
        assertTrue(prompt.contains("Translate or drop honorifics naturally."))
        assertTrue(prompt.contains("Previous page (context only, do not translate):\nNone"))
    }

    @Test
    fun jsonEscapesQuotesAndNewlines() {
        val tricky = request().copy(
            bubbles = listOf(BubbleText(3, BubbleKind.DIALOGUE, "He said \"go\"\n約束だよ", 40)),
        )
        val prompt = PromptBuilder.build(tricky)
        assertTrue(prompt.contains("\\n"), "newline must be JSON-escaped, got: $prompt")
        assertTrue(prompt.contains("\\u0022") || prompt.contains("\\\""), "quotes must be escaped, got: $prompt")
    }
}
