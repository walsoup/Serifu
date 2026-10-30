package app.serifu.overlay

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.serifu.core.model.BubbleKind
import app.serifu.debug.PipelineTimings
import kotlin.math.roundToInt

/** Debug layers (plan.md 12.6), drawn on top of the lettering when enabled. */
class DebugOverlayPainter {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.CYAN
    }
    private val droppedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.DEBUG_GREY
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val clusterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.MAGENTA
    }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.DEBUG_RED
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = InkPaper.DEBUG_GREEN
        alpha = 51 // 20%
    }
    private val letteringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.DEBUG_GREEN
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = InkPaper.MAGENTA
    }
    private val timingBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = InkPaper.INK
        alpha = (0.70f * 255).roundToInt()
    }
    private val timingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = InkPaper.PAPER
        typeface = Typeface.MONOSPACE
    }
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        page: RenderPage,
        originX: Int,
        originY: Int,
        density: Float,
        timings: PipelineTimings? = null,
    ) {
        boxPaint.strokeWidth = density
        droppedPaint.strokeWidth = density
        clusterPaint.strokeWidth = 2f * density
        wallPaint.strokeWidth = density
        letteringPaint.strokeWidth = density
        labelPaint.textSize = 10f * density
        canvas.save()
        canvas.translate(-originX.toFloat(), -originY.toFloat())
        for (line in page.debugLines) {
            val box = line.box
            rect.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            canvas.drawRect(rect, boxPaint)
        }
        for (line in page.debugDropped) {
            val box = line.box
            rect.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            canvas.drawRect(rect, droppedPaint)
        }
        for (cluster in page.debugClusters) {
            val mask = cluster.bubbleMask
            if (mask != null) {
                val scale = mask.scale.toFloat()
                for (row in 0 until mask.height) {
                    if (mask.minX[row] < 0) continue
                    rect.set(
                        mask.minX[row] * scale,
                        (mask.y0 + row) * scale,
                        (mask.maxX[row] + 1) * scale,
                        (mask.y0 + row + 1) * scale,
                    )
                    canvas.drawRect(rect, maskPaint)
                }
            }
            val union = cluster.unionRect
            rect.set(union.left.toFloat(), union.top.toFloat(), union.right.toFloat(), union.bottom.toFloat())
            canvas.drawRect(rect, clusterPaint)
            val lettering = cluster.letteringRect
            rect.set(
                lettering.left.toFloat(),
                lettering.top.toFloat(),
                lettering.right.toFloat(),
                lettering.bottom.toFloat(),
            )
            canvas.drawRect(rect, letteringPaint)
            canvas.drawText(
                "${cluster.id}${kindSuffix(cluster.kind)}",
                union.left.toFloat(),
                (union.top - 2 * density).coerceAtLeast(0f),
                labelPaint,
            )
        }
        for ((a, b) in page.debugWalls) {
            canvas.drawLine(
                a.centerX.toFloat(), a.centerY.toFloat(),
                b.centerX.toFloat(), b.centerY.toFloat(),
                wallPaint,
            )
        }
        canvas.restore()
        if (timings != null) {
            drawTimingPanel(canvas, density, timings)
        }
    }

    private fun drawTimingPanel(canvas: Canvas, density: Float, timings: PipelineTimings) {
        timingTextPaint.textSize = 10f * density
        val lineHeight = 13f * density
        val padX = 8f * density
        val padY = 6f * density
        val corner = 4f * density

        val lines = listOf(
            "capture: ${timings.captureMs}ms",
            "convert: ${timings.convertMs}ms",
            "fingerprint: ${timings.fingerprintMs}ms",
            "ocr: ${timings.ocrMs}ms",
            "cluster: ${timings.clusterMs}ms",
            "translate(mlkit): ${timings.translateMlKitMs}ms",
            "layout: ${timings.layoutMs}ms",
            "total: ${timings.totalMs}ms",
            "cache: ${timings.cacheResult.ifEmpty { "miss" }}",
            "self discarded: ${timings.selfDiscarded}",
        )

        var maxWidth = 0f
        for (line in lines) {
            val w = timingTextPaint.measureText(line)
            if (w > maxWidth) maxWidth = w
        }

        val left = 12f * density
        val top = 36f * density
        val right = left + maxWidth + padX * 2
        val bottom = top + lines.size * lineHeight + padY * 2

        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, corner, corner, timingBgPaint)

        var y = top + padY + 10f * density
        for (line in lines) {
            canvas.drawText(line, left + padX, y, timingTextPaint)
            y += lineHeight
        }
    }

    private fun kindSuffix(kind: BubbleKind): String = when (kind) {
        BubbleKind.DIALOGUE -> "D"
        BubbleKind.CAPTION -> "C"
        BubbleKind.SFX -> "S"
        BubbleKind.FREE_TEXT -> "F"
    }
}
