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
}
