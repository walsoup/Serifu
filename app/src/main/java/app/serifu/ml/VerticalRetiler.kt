package app.serifu.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.serifu.capture.CapturedFrame
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import app.serifu.core.model.SourceLanguage
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.Orientation
import app.serifu.core.vision.charSizeOf
import app.serifu.core.vision.orientationOf
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Vertical re-tiling quality pass (plan.md 10.4.1): ML Kit's vertical
 * Japanese recognition is the weakest link. Low-confidence vertical columns
 * are split into glyph cells, laid out as horizontal strips, and re-OCR'd in
 * a single mosaic call. The better reading wins.
 */
class VerticalRetiler {
    suspend fun retile(
        lines: List<OcrLine>,
        frame: CapturedFrame,
        source: SourceLanguage,
        ocr: OcrEngine,
    ): List<OcrLine> {
        if (source == SourceLanguage.KO || lines.isEmpty()) return lines
        val candidates = lines.filter { needsRetile(it) }
        if (candidates.isEmpty()) return lines
        val strips = ArrayList<PlacedStrip>()
        try {
            for (line in candidates) {
                val strip = buildStrip(line, frame.bitmap, frame.luma.full) ?: continue
                strips.add(strip)
            }
            if (strips.isEmpty()) return lines
            val mosaic = buildMosaic(strips)
            try {
                val reOcred = ocr.recognize(mosaic.bitmap, source)
                val improved = assignToStrips(reOcred, strips, mosaic.tops, source)
                if (improved.all { it == null }) return lines
                val byCandidate = strips.zip(improved).toMap()
                return lines.map { line ->
                    val strip = strips.firstOrNull { it.candidate == line }
                    val scored = strip?.let { byCandidate[it] }
                    if (scored != null &&
                        scored.text.isNotBlank() &&
                        scored.confidence >= line.confidence + Tuning.VERTICAL_RETILE_CONFIDENCE_GAIN
                    ) {
                        line.copy(
                            text = scored.text,
                            confidence = scored.confidence,
                            symbolBoxes = emptyList(),
                        )
                    } else {
                        line
                    }
                }
            } finally {
                mosaic.bitmap.recycle()
            }
        } finally {
            for (strip in strips) {
                if (!strip.bitmap.isRecycled) strip.bitmap.recycle()
            }
        }
    }

    private fun needsRetile(line: OcrLine): Boolean {
        if (orientationOf(line) != Orientation.VERTICAL) return false
        if (line.confidence < Tuning.VERTICAL_RETILE_CONFIDENCE) return true
        val charSize = charSizeOf(line, Orientation.VERTICAL)
        if (charSize <= 0f) return false
        val expected = line.box.height / charSize
        val actual = line.text.codePointCount(0, line.text.length).toFloat()
        if (expected <= 0f) return false
        return kotlin.math.abs(expected - actual) / expected > Tuning.VERTICAL_RETILE_COUNT_MISMATCH
    }

    private data class PlacedStrip(
        val candidate: OcrLine,
        val bitmap: Bitmap,
        val bgLuma: Int,
    )

    private data class Scored(val text: String, val confidence: Float)

    private data class Mosaic(val bitmap: Bitmap, val tops: List<Int>)

    private fun buildStrip(line: OcrLine, bitmap: Bitmap, full: LumaImage): PlacedStrip? {
        val charSize = charSizeOf(line, Orientation.VERTICAL)
        if (charSize <= 0f) return null
        val box = line.box.intersect(IntRect(0, 0, bitmap.width, bitmap.height))
        if (box.isEmpty || box.width <= 0 || box.height <= 0) return null
        val bg = backgroundLevel(full, box)
        val cells = projectionCells(full, box, charSize) ?: fixedCells(box, charSize)
        if (cells.size < 2) return null
        val spacing = (Tuning.VERTICAL_RETILE_CELL_SPACING * charSize).roundToInt().coerceAtLeast(1)
        val stripH = cells.maxOf { it.height }.coerceAtLeast(1)
        val stripW = cells.sumOf { it.width } + spacing * (cells.size - 1)
        if (stripW <= 0 || stripH <= 0 || stripW > 4096 || stripH > 4096) return null
        val strip = Bitmap.createBitmap(stripW, stripH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(grey(bg))
        var x = 0
        for (cell in cells) {
            val src = Rect(cell.left, cell.top, cell.right, cell.bottom)
            val top = (stripH - cell.height) / 2
            val dst = Rect(x, top, x + cell.width, top + cell.height)
            canvas.drawBitmap(bitmap, src, dst, null)
            x += cell.width + spacing
        }
        return PlacedStrip(line, strip, bg)
    }

    private fun buildMosaic(strips: List<PlacedStrip>): Mosaic {
        val gap = 24
        val width = strips.maxOf { it.bitmap.width }.coerceAtLeast(1)
        val tops = ArrayList<Int>(strips.size)
        var height = 0
        for (strip in strips) {
            tops.add(height)
            height += strip.bitmap.height + gap
        }
        height = (height - gap).coerceAtLeast(1)
        val mosaic = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mosaic)
        val paint = Paint()
        for (i in strips.indices) {
            val strip = strips[i]
            val top = tops[i]
            paint.color = grey(strip.bgLuma)
            canvas.drawRect(0f, top.toFloat(), width.toFloat(), (top + strip.bitmap.height).toFloat(), paint)
            canvas.drawBitmap(strip.bitmap, 0f, top.toFloat(), null)
        }
        return Mosaic(mosaic, tops)
    }

    private fun assignToStrips(
        reOcred: List<OcrLine>,
        strips: List<PlacedStrip>,
        tops: List<Int>,
        source: SourceLanguage,
    ): List<Scored?> {
        val perStrip = List(strips.size) { mutableListOf<OcrLine>() }
        for (line in reOcred) {
            val cy = line.box.centerY
            for (i in strips.indices) {
                val top = tops.getOrNull(i) ?: continue
                if (cy in top until top + strips[i].bitmap.height) {
                    perStrip[i].add(line)
                    break
                }
            }
        }
        val separator = if (source == SourceLanguage.KO) " " else ""
        return perStrip.map { group ->
            if (group.isEmpty()) {
                null
            } else {
                val ordered = group.sortedBy { it.box.left }
                val text = ordered.joinToString(separator) { it.text.trim() }
                val confidence = ordered.map { it.confidence }.average().toFloat()
                Scored(text, confidence)
            }
        }
    }

    private fun projectionCells(full: LumaImage, box: IntRect, charSize: Float): List<IntRect>? {
        val w = box.width
        val h = box.height
        if (w <= 0 || h <= 0) return null
        val cells = ArrayList<IntRect>()
        var runTop = -1
        for (relY in 0 until h) {
            val y = box.top + relY
            var dark = 0
            if (y in 0 until full.height) {
                for (relX in 0 until w) {
                    val x = box.left + relX
                    if (x !in 0 until full.width) continue
                    if (full[x, y] < 128) dark++
                }
            }
            val gap = dark.toFloat() / w.toFloat() < Tuning.VERTICAL_RETILE_GAP_FRACTION
            if (!gap) {
                if (runTop < 0) runTop = relY
            } else if (runTop >= 0) {
                cells.add(IntRect(box.left, box.top + runTop, box.right, box.top + relY))
                runTop = -1
            }
        }
        if (runTop >= 0) cells.add(IntRect(box.left, box.top + runTop, box.right, box.bottom))
        val minH = (charSize * 0.3f).roundToInt().coerceAtLeast(1)
        val kept = cells.filter { it.height >= minH }
        return if (kept.size >= 2) kept else null
    }

    private fun fixedCells(box: IntRect, charSize: Float): List<IntRect> {
        val step = charSize.roundToInt().coerceAtLeast(1)
        val cells = ArrayList<IntRect>()
        var top = box.top
        while (top < box.bottom) {
            val bottom = min(top + step, box.bottom)
            cells.add(IntRect(box.left, top, box.right, bottom))
            top = bottom
        }
        return cells
    }

    private fun backgroundLevel(full: LumaImage, box: IntRect): Int {
        val samples = ArrayList<Int>(128)
        for (y in box.top until box.bottom step 2) {
            if (y !in 0 until full.height) continue
            for (x in box.left until box.right step 2) {
                if (x !in 0 until full.width) continue
                samples.add(full[x, y])
            }
        }
        if (samples.isEmpty()) return 255
        samples.sort()
        return samples[((samples.size - 1) * 0.7f).toInt().coerceIn(0, samples.size - 1)]
    }

    private fun grey(luma: Int): Int {
        val v = luma.coerceIn(0, 255)
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}
