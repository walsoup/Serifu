package app.walsoup.serifu.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.view.View

class TooltipView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaper.INK }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = InkPaper.PAPER
        textSize = sp(13f)
    }
    private val bubble = RectF()
    private val tail = Path()
    private var layout: StaticLayout? = null
    var tailOnEnd: Boolean = true

    fun setMessage(message: String) {
        val textWidth = dp(220)
        layout = StaticLayout.Builder.obtain(message, 0, message.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val text = layout
        val pad = dp(12)
        val tailPx = dp(8)
        val width = (text?.width ?: 0) + pad * 2 + tailPx
        val height = (text?.height ?: 0) + pad * 2
        setMeasuredDimension(width, height.coerceAtLeast(dp(36)))
    }

    override fun onDraw(canvas: Canvas) {
        val text = layout ?: return
        val tailPx = dpF(8f)
        val corner = dpF(6f)
        if (tailOnEnd) {
            bubble.set(0f, 0f, width - tailPx, height.toFloat())
        } else {
            bubble.set(tailPx, 0f, width.toFloat(), height.toFloat())
        }
        canvas.drawRoundRect(bubble, corner, corner, fill)
        tail.rewind()
        val mid = height / 2f
        if (tailOnEnd) {
            tail.moveTo(bubble.right - 1f, mid - tailPx)
            tail.lineTo(width.toFloat(), mid)
            tail.lineTo(bubble.right - 1f, mid + tailPx)
        } else {
            tail.moveTo(bubble.left + 1f, mid - tailPx)
            tail.lineTo(0f, mid)
            tail.lineTo(bubble.left + 1f, mid + tailPx)
        }
        tail.close()
        canvas.drawPath(tail, fill)
        canvas.save()
        canvas.translate(bubble.left + dp(12), (height - text.height) / 2f)
        text.draw(canvas)
        canvas.restore()
    }

    fun measuredWidthPx(): Int = if (measuredWidth > 0) measuredWidth else {
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        measuredWidth
    }

    fun measuredHeightPx(): Int = if (measuredHeight > 0) measuredHeight else {
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        measuredHeight
    }
}
