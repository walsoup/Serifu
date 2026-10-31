package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import app.serifu.core.image.RgbSampler
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

    @Test
    fun separatedColumnsSharingSameSpeechBubbleAreMerged() {
        val quarter = LumaImage(270, 480, ByteArray(270 * 480) { 255.toByte() })
        // Center at (100, 75) in quarter coords (400, 300 in frame coords)
        for (deg in 0 until 360) {
            val qx = (100 + 36 * kotlin.math.cos(Math.toRadians(deg.toDouble()))).toInt()
            val qy = (75 + 40 * kotlin.math.sin(Math.toRadians(deg.toDouble()))).toInt()
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val px = (qx + dx).coerceIn(0, 269)
                    val py = (qy + dy).coerceIn(0, 479)
                    quarter.bytes[py * 270 + px] = 0
                }
            }
        }
        val full = LumaImage(1080, 1920, ByteArray(1080 * 1920) { 255.toByte() })
        val mockRgb = object : RgbSampler {
            override val width = 1080
            override val height = 1920
            override fun argb(x: Int, y: Int) = 0xFFFFFFFF.toInt()
        }
        val vision = VisionInputs(
            full = full,
            quarter = quarter,
            rgb = mockRgb,
            refUnit = 1f,
        )
        // Two columns inside this bubble at (400, 300)
        // GapX is 80px > 74px (VERTICAL_GAP_X_FACTOR * 40), so mergesBasic initially rejects merging them
        val lines = listOf(
            line("右側のセリフ", IntRect(440, 200, 480, 400)),
            line("左側", IntRect(320, 200, 360, 360)),
        )
        val outcome = SpeechBubbleClusterer.clusterWithVision(lines, config, vision)
        assertEquals(1, outcome.clusters.size)
        assertEquals("右側のセリフ左側", outcome.clusters.single().sourceText)
    }

    private fun line(text: String, box: IntRect, angle: Float = 0f) =
        OcrLine(text, box, confidence = 0.95f, angleDeg = angle, language = "ja", symbolBoxes = emptyList())
}
