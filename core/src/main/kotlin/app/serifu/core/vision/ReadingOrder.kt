package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.geometry.RectMath
import app.serifu.core.model.SourceLanguage

object ReadingOrder {
    fun majority(orientations: List<Orientation>, source: SourceLanguage): Orientation {
        var vertical = 0
        var horizontal = 0
        for (orientation in orientations) {
            when (orientation) {
                Orientation.VERTICAL -> vertical++
                Orientation.HORIZONTAL -> horizontal++
                Orientation.UNKNOWN -> Unit
            }
        }
        return when {
            vertical > horizontal -> Orientation.VERTICAL
            horizontal > vertical -> Orientation.HORIZONTAL
            source == SourceLanguage.JA -> Orientation.VERTICAL
            else -> Orientation.HORIZONTAL
        }
    }

    fun assemble(lines: List<OcrLine>, orientations: List<Orientation>, source: SourceLanguage): String {
        if (lines.isEmpty()) return ""
        val ordered = when (majority(orientations, source)) {
            Orientation.VERTICAL -> columns(lines).flatMap { column -> column.sortedBy { it.box.top } }
            else -> rows(lines).flatMap { row -> row.sortedBy { it.box.left } }
        }
        val separator = if (source == SourceLanguage.JA || source == SourceLanguage.ZH) "" else " "
        return TextNormalizer.normalize(ordered.joinToString(separator) { it.text.trim() }, source)
    }

    fun sort(clusters: List<SpeechBubbleCluster>, source: SourceLanguage, frameHeight: Int): List<SpeechBubbleCluster> {
        val band = (frameHeight * Tuning.READING_BAND_FRACTION).coerceAtLeast(1f)
        return if (source == SourceLanguage.JA) {
            clusters.sortedWith(
                compareBy<SpeechBubbleCluster> { (it.unionRect.top / band).toInt() }
                    .thenByDescending { it.unionRect.right },
            )
        } else {
            clusters.sortedWith(compareBy<SpeechBubbleCluster> { it.unionRect.top }.thenBy { it.unionRect.left })
        }
    }

    private fun columns(lines: List<OcrLine>): List<List<OcrLine>> =
        group(lines) { group, line ->
            val minW = minOf(group.width, line.box.width).coerceAtLeast(1)
            RectMath.overlapX(group, line.box).toFloat() / minW > Tuning.LINE_GROUP_OVERLAP
        }.sortedByDescending { column -> column.maxOf { it.box.centerX } }

    private fun rows(lines: List<OcrLine>): List<List<OcrLine>> =
        group(lines) { group, line ->
            val minH = minOf(group.height, line.box.height).coerceAtLeast(1)
            RectMath.overlapY(group, line.box).toFloat() / minH > Tuning.LINE_GROUP_OVERLAP
        }.sortedBy { row -> row.minOf { it.box.top } }

    private fun group(lines: List<OcrLine>, sameBand: (IntRect, OcrLine) -> Boolean): List<List<OcrLine>> {
        val groups = mutableListOf<MutableList<OcrLine>>()
        val bounds = mutableListOf<IntRect>()
        for (line in lines) {
            val index = bounds.indexOfFirst { sameBand(it, line) }
            if (index < 0) {
                groups.add(mutableListOf(line))
                bounds.add(line.box)
            } else {
                groups[index].add(line)
                bounds[index] = bounds[index].union(line.box)
            }
        }
        return groups
    }
}
