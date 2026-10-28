package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechBubbleClustererTest {
    private val config = ClusterConfig(
        source = SourceLanguage.JA,
        frameWidth = 1080,
        frameHeight = 1920,
        patchPadPx = 8,
        skipLatin = true,
        pill = null,
    )

    @Test
    fun threeColumnsInOneBubbleBecomeOneClusterInReadingOrder() {
        val lines = listOf(
            line("きくけ", IntRect(80, 20, 120, 220)),
            line("あいう", IntRect(200, 20, 240, 220)),
            line("えおか", IntRect(140, 20, 180, 220)),
        )
        val clusters = SpeechBubbleClusterer.cluster(lines, config)
        assertEquals(1, clusters.size)
        assertEquals("あいうえおかきくけ", clusters.single().sourceText)
        assertEquals(BubbleKind.DIALOGUE, clusters.single().kind)
    }

    @Test
    fun distantBubblesStaySeparateAndJapaneseOrdersRightToLeft() {
        val lines = listOf(
            line("左", IntRect(40, 40, 90, 180)),
            line("右", IntRect(700, 40, 750, 180)),
        )
        val clusters = SpeechBubbleClusterer.cluster(lines, config)
        assertEquals(listOf("右", "左"), clusters.map { it.sourceText })
        assertEquals(listOf(0, 1), clusters.map { it.id })
    }

    @Test
    fun soundEffectSizedGlyphDoesNotJoinDialogue() {
        val lines = listOf(
            line("セリフ", IntRect(40, 400, 80, 640)),
            line("ドン", IntRect(400, 40, 560, 180), angle = 24f),
        )
        val clusters = SpeechBubbleClusterer.cluster(lines, config)
        assertEquals(2, clusters.size)
        assertTrue(clusters.any { it.kind == BubbleKind.SFX && it.sourceText == "ドン" })
        assertTrue(clusters.any { it.kind == BubbleKind.DIALOGUE && it.sourceText == "セリフ" })
    }

    @Test
    fun koreanRowsJoinWithSpacesAndReadTopToBottom() {
        val korean = config.copy(source = SourceLanguage.KO)
        val lines = listOf(
            line("다음", IntRect(40, 200, 180, 250)),
            line("안녕", IntRect(40, 40, 180, 90)),
        )
        val clusters = SpeechBubbleClusterer.cluster(lines, korean)
        assertEquals(listOf("안녕", "다음"), clusters.map { it.sourceText })
    }

    private fun line(text: String, box: IntRect, angle: Float = 0f) =
        OcrLine(text, box, confidence = 0.95f, angleDeg = angle, language = "ja", symbolBoxes = emptyList())
}
