package app.serifu.core.vision

import app.serifu.core.geometry.IntRect
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingOrderTest {
    private fun cluster(id: Int, box: IntRect) = SpeechBubbleCluster(
        id = id,
        kind = BubbleKind.DIALOGUE,
        sourceText = "text $id",
        orientation = Orientation.VERTICAL,
        unionRect = box,
        patchRect = box,
        letteringRect = box,
        fillColor = 0,
        textColor = 0,
        charSizePx = 20f,
        angleDeg = 0f,
        members = emptyList(),
    )

    @Test
    fun zhUsesBandedRightToLeftLikeJa() {
        val left = cluster(0, IntRect(10, 100, 100, 200))
        val right = cluster(1, IntRect(200, 100, 290, 200))
        val clusters = listOf(left, right)
        assertEquals(listOf(1, 0), ReadingOrder.sort(clusters, SourceLanguage.JA, 1000).map { it.id })
        assertEquals(listOf(1, 0), ReadingOrder.sort(clusters, SourceLanguage.ZH, 1000).map { it.id })
    }

    @Test
    fun latinTargetsStayLeftToRight() {
        val left = cluster(0, IntRect(10, 100, 100, 200))
        val right = cluster(1, IntRect(200, 100, 290, 200))
        val clusters = listOf(left, right)
        assertEquals(listOf(0, 1), ReadingOrder.sort(clusters, SourceLanguage.KO, 1000).map { it.id })
        assertEquals(listOf(0, 1), ReadingOrder.sort(clusters, SourceLanguage.FR, 1000).map { it.id })
    }
}
