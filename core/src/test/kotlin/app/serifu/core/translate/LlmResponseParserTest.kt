package app.serifu.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmResponseParserTest {
    private val fallback = mapOf(0 to "Good morning", 1 to "BOOM")

    @Test
    fun cleanJson() {
        val out = LlmResponseParser.parse("""{"t":[{"id":0,"text":"Morning!"},{"id":1,"text":"THUD"}]}""", fallback)
        assertEquals(mapOf(0 to "Morning!", 1 to "THUD"), out)
    }

    @Test
    fun proseAndCodeFences() {
        val raw = "Here you go:\n```json\n{\"t\": [{\"id\": 0, \"text\": \"Morning!\" }, {\"id\": 1, \"text\": \"THUD\"}]} \n```\nDone."
        val out = LlmResponseParser.parse(raw, fallback)
        assertEquals(mapOf(0 to "Morning!", 1 to "THUD"), out)
    }

    @Test
    fun missingIdsFallBack() {
        val out = LlmResponseParser.parse("""{"t":[{"id":0,"text":"Morning!"}]}""", fallback)
        assertEquals(mapOf(0 to "Morning!", 1 to "BOOM"), out)
    }

    @Test
    fun emptyTextFallsBack() {
        val out = LlmResponseParser.parse("""{"t":[{"id":0,"text":"  "},{"id":1,"text":"THUD"}]}""", fallback)
        assertEquals(mapOf(0 to "Good morning", 1 to "THUD"), out)
    }

    @Test
    fun wrappedQuotesStripped() {
        val out = LlmResponseParser.parse("""{"t":[{"id":0,"text":"\"Morning!\""},{"id":1,"text":"'THUD'"}]}""", fallback)
        assertEquals(mapOf(0 to "Morning!", 1 to "THUD"), out)
    }

    @Test
    fun garbageFallsBackEntirely() {
        val out = LlmResponseParser.parse("no json here at all", fallback)
        assertEquals(fallback, out)
        val broken = LlmResponseParser.parse("{not valid json", fallback)
        assertEquals(fallback, broken)
    }

    @Test
    fun unknownIdsIgnored() {
        val out = LlmResponseParser.parse("""{"t":[{"id":0,"text":"Morning!"},{"id":9,"text":"Extra"}]}""", fallback)
        assertEquals(mapOf(0 to "Morning!", 1 to "BOOM"), out)
    }
}
