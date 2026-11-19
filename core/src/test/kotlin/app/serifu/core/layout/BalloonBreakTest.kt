package app.serifu.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalloonBreakTest {
    private fun widthOf(text: String): Float = text.length * 10f

    @Test
    fun aShortLineStaysWhole() {
        assertEquals(listOf("good morning"), BalloonBreak.lines("good morning", 200f, ::widthOf))
    }

    @Test
    fun wordsAreNeverHyphenated() {
        val lines = BalloonBreak.lines("extraordinary circumstances", 80f, ::widthOf)
        assertEquals(listOf("extraordinary", "circumstances"), lines)
        assertTrue(lines.none { '-' in it })
    }

    @Test
    fun aFunctionWordDoesNotSitAlone() {
        val lines = BalloonBreak.lines("walk to the market", 80f, ::widthOf)
        assertTrue(lines.none { it.equals("the", ignoreCase = true) || it.equals("to", ignoreCase = true) })
    }

    @Test
    fun aWidowedLastWordIsPulledUp() {
        assertEquals(
            listOf("hello", "there friend"),
            BalloonBreak.lines("hello there friend", 120f, ::widthOf),
        )
    }

    @Test
    fun balancingNeverLeavesAFunctionWordAtTheEnd() {
        val lines = BalloonBreak.lines("go to the big market today", 120f, ::widthOf)
        val functionWords = setOf("a", "an", "the", "to", "of", "and", "or", "but")
        for (line in lines.dropLast(1)) {
            val lastWord = line.trim().split(" ").last()
            assertTrue(lastWord.lowercase() !in functionWords, "line ends on '$lastWord': $lines")
        }
    }

    @Test
    fun cjkPacksCharactersWithoutSpaces() {
        val text = "おはようございます今日は"
        val lines = BalloonBreak.lines(text, 55f, ::widthOf, cjk = true)
        assertTrue(lines.size > 1, "expected multiple lines, got $lines")
        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.none { ' ' in it })
    }

    @Test
    fun cjkKeepsMidLineSpaces() {
        val lines = BalloonBreak.lines("あ いうえお", 45f, ::widthOf, cjk = true)
        assertEquals("あ いうえお", lines.joinToString(""))
    }
}
