package app.serifu.overlay

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import app.serifu.core.model.BubbleKind

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
    private val rect = RectF()

    fun draw(canvas: Canvas, page: RenderPage, originX: Int, originY: Int, density: Float) {
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
    }

    private fun kindSuffix(kind: BubbleKind): String = when (kind) {
        BubbleKind.DIALOGUE -> "D"
        BubbleKind.CAPTION -> "C"
        BubbleKind.SFX -> "S"
        BubbleKind.FREE_TEXT -> "F"
    }
}
