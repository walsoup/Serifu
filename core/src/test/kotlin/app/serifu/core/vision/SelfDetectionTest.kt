package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfDetectionTest {
    private val box = IntRect(0, 0, 10, 10)

    @Test
    fun twoLatinLinesMatchingShownLetteringLeak() {
        val lines = listOf(
            line("Hello there friend"),
            line("GOOD MORNING EVERYONE"),
        )
        val shown = listOf("Hello there friend, extra", "good morning everyone else")
        assertTrue(SelfDetection.leaked(lines, shown))
    }

    @Test
    fun oneMatchOrJapaneseDoesNotLeak() {
        assertFalse(SelfDetection.leaked(listOf(line("Hello there friend")), listOf("Hello there friend")))
        assertFalse(
            SelfDetection.leaked(
                listOf(line("おはよう"), line("待って")),
                listOf("Hello there", "Good morning"),
            ),
        )
        assertFalse(SelfDetection.leaked(listOf(line("Hello"), line("There")), emptyList()))
    }

    private fun line(text: String) = OcrLine(text, box, confidence = 0.9f, angleDeg = 0f, language = "en", symbolBoxes = emptyList())
}
