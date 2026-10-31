package app.serifu.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.serifu.core.Tuning
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import android.view.animation.PathInterpolator

data class HudModel(
    val source: SourceLanguage,
    val mode: TriggerMode,
    val paused: Boolean,
)

interface HudHost {
    fun onSource(language: SourceLanguage)
    fun onMode(mode: TriggerMode)
    fun onPauseToggle()
    fun onPeek(held: Boolean)
    fun onOpenApp()
    fun onDismiss()
}

class HudView(context: Context) : LinearLayout(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaper.PAPER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.INK
        strokeWidth = dpF(2f)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaper.INK }
    private val panel = RectF()
    private val shadowRect = RectF()
    private val corner = dpF(4f)
    private val shadowPx = dpF(3f)
    private var host: HudHost? = null
    var tailTowardEnd = false
    private val dismissRunnable = Runnable { host?.onDismiss() }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        isClickable = true
    }

    fun bind(host: HudHost) {
        this.host = host
    }

    fun render(model: HudModel) {
        removeAllViews()
        val gap = dp(8)
        val langScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(Tuning.HUD_ROW_HEIGHT_DP))
            SourceLanguage.entries.forEach { language ->
                val view = segment(language.nativeName, language == model.source, letterSpace = false, minWidthPx = dp(60)) {
                    host?.onSource(language)
                }
                addView(view)
                if (language == model.source) {
                    langScroll.post {
                        langScroll.smoothScrollTo((view.left - dp(16)).coerceAtLeast(0), 0)
                    }
                }
            }
        }
        langScroll.addView(langRow)
        addView(langScroll)
        addView(spacer(gap))
        addView(row(gap).apply {
            addView(segment("AUTO", model.mode == TriggerMode.AUTO, letterSpace = true) { host?.onMode(TriggerMode.AUTO) })
            addView(segment("MANUAL", model.mode == TriggerMode.MANUAL, letterSpace = true) { host?.onMode(TriggerMode.MANUAL) })
            addView(segment("OFF", model.mode == TriggerMode.OFF, letterSpace = true) { host?.onMode(TriggerMode.OFF) })
        })
        addView(spacer(gap))
        addView(row(gap).apply {
            addView(segment(if (model.paused) "Resume" else "Pause", selected = false, letterSpace = false) {
                host?.onPauseToggle()
            })
            addView(peekSegment())
            addView(segment("Open", selected = false, letterSpace = false) { host?.onOpenApp() })
        })
        applyPadding()
        poke()
    }

    fun poke() {
        removeCallbacks(dismissRunnable)
        postDelayed(dismissRunnable, Tuning.HUD_DISMISS_MS)
    }

    fun stopTimer() {
        removeCallbacks(dismissRunnable)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            host?.onDismiss()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) poke()
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        stopTimer()
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val inset = stroke.strokeWidth / 2f
        if (tailTowardEnd) {
            shadowRect.set(0f, shadowPx, width - shadowPx, height.toFloat())
            panel.set(shadowPx + inset, inset, width - inset, height - shadowPx - inset)
        } else {
            shadowRect.set(shadowPx, shadowPx, width.toFloat(), height.toFloat())
            panel.set(inset, inset, width - shadowPx - inset, height - shadowPx - inset)
        }
        canvas.drawRoundRect(shadowRect, corner, corner, shadow)
        canvas.drawRoundRect(panel, corner, corner, fill)
        canvas.drawRoundRect(panel, corner, corner, stroke)
        super.dispatchDraw(canvas)
    }

    private fun applyPadding() {
        val pad = dp(12)
        val shadow = dp(3)
        val start = if (tailTowardEnd) shadow + pad else pad
        val end = if (tailTowardEnd) pad else shadow + pad
        setPadding(start, pad, end, shadow + pad)
    }

    private fun row(gap: Int): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        showDividers = SHOW_DIVIDER_MIDDLE
        dividerPadding = 0
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(Tuning.HUD_ROW_HEIGHT_DP))
        this.tag = gap
    }

    private fun spacer(gap: Int): View = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, gap)
    }

    private fun segment(
        label: String,
        selected: Boolean,
        letterSpace: Boolean,
        minWidthPx: Int = 0,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 12f
        maxLines = 1
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(if (selected) InkPaper.ON_HANKO else InkPaper.INK)
        setBackgroundColor(if (selected) InkPaper.HANKO else InkPaper.TRANSPARENT)
        if (letterSpace) letterSpacing = 0.04f
        setOnClickListener { onClick() }
        val width = if (minWidthPx > 0) LayoutParams.WRAP_CONTENT else 0
        val weight = if (minWidthPx > 0) 0f else 1f
        layoutParams = LayoutParams(width, LayoutParams.MATCH_PARENT, weight).apply {
            marginEnd = dp(4)
        }
        if (minWidthPx > 0) minimumWidth = minWidthPx
        contentDescription = label
    }

    private fun peekSegment(): TextView = TextView(context).apply {
        text = "Peek"
        gravity = Gravity.CENTER
        textSize = 10f
        maxLines = 1
        setTextColor(InkPaper.INK)
        contentDescription = "Peek original"
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    host?.onPeek(true)
                    poke()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    host?.onPeek(false)
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        val ENTRANCE = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
