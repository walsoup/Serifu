package app.walsoup.serifu.overlay

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
import app.walsoup.serifu.R
import android.view.animation.PathInterpolator

data class HudModel(
    val source: SourceLanguage,
    val mode: TriggerMode,
    val paused: Boolean,
    val phaseLabel: String? = null,
)

interface HudHost {
    fun onSource(language: SourceLanguage)
    fun onMode(mode: TriggerMode)
    fun onPauseToggle()
    fun onPeek(held: Boolean)
    fun onOpenApp()
    fun onDismiss()
}

/**
 * Overlay tokens mirrored from `DarkPalette` in `ui/design/SerifuUi.kt`.
 * Overlay Views are not Compose and cannot resolve theme tokens, so the exact
 * palette values live here — no ad-hoc colours, no cream paper.
 */
private object HudColors {
    const val SURFACE = 0xFF1E1B15.toInt()
    const val TEXT = 0xFFF3EEE3.toInt()
    const val TEXT_SECONDARY = 0xFF998F80.toInt()
    const val HAIRLINE = 0xFF2A261E.toInt()
    const val PRIMARY = 0xFFC8322B.toInt()
    const val ON_PRIMARY = 0xFFF3EEE3.toInt()
    const val PRIMARY_PRESSED = 0x33C8322B
}

class HudView(context: Context) : LinearLayout(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HudColors.SURFACE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = HudColors.HAIRLINE
        strokeWidth = dpF(1f)
    }
    private val panel = RectF()
    private val corner = dpF(14f)
    private var host: HudHost? = null
    var tailTowardEnd = false
    private val dismissRunnable = Runnable { host?.onDismiss() }
    private var statusContainer: LinearLayout? = null
    private var statusText: TextView? = null

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
        statusContainer = null
        statusText = null
        val gap = dp(8)
        addView(statusRow(gap, model.phaseLabel))
        val langScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(Tuning.HUD_ROW_HEIGHT_DP))
            SourceLanguage.entries.forEach { language ->
                val view = segment(language.nativeName, language == model.source, letterSpace = false, minWidthPx = dp(56)) {
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
            addView(segment(if (model.paused) "Resume" else "Pause", selected = model.paused, letterSpace = false) {
                host?.onPauseToggle()
            })
            addView(peekSegment())
            addView(segment("Open", selected = false, letterSpace = false) { host?.onOpenApp() })
        })
        applyPadding()
        poke()
    }

    /** Updates the status/error line at the top of the HUD without rebuilding the controls. */
    fun setStatus(message: String?) {
        val container = statusContainer ?: return
        val text = statusText ?: return
        val has = !message.isNullOrBlank()
        container.visibility = if (has) VISIBLE else GONE
        text.visibility = if (has) VISIBLE else GONE
        text.text = message.orEmpty()
        if (has) poke()
    }

    fun poke() {
        removeCallbacks(dismissRunnable)
        postDelayed(dismissRunnable, Tuning.HUD_DISMISS_MS)
    }

    fun stopTimer() {
        removeCallbacks(dismissRunnable)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                host?.onDismiss()
                return true
            }
            // Stay open for as long as a finger is down, dragging, or holding Peek.
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> stopTimer()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> poke()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        stopTimer()
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        panel.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(panel, corner, corner, fill)
        val inset = stroke.strokeWidth / 2f
        panel.inset(inset, inset)
        canvas.drawRoundRect(panel, corner, corner, stroke)
        super.dispatchDraw(canvas)
    }

    private fun applyPadding() {
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
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

    private fun statusRow(gap: Int, label: String?): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val text = statusLabel(label.orEmpty())
        container.addView(text)
        container.addView(spacer(gap))
        val has = !label.isNullOrBlank()
        container.visibility = if (has) VISIBLE else GONE
        text.visibility = if (has) VISIBLE else GONE
        statusContainer = container
        statusText = text
        return container
    }

    private fun statusLabel(value: String): TextView = TextView(context).apply {
        text = value
        textSize = 11f
        maxLines = 3
        setEllipsize(android.text.TextUtils.TruncateAt.END)
        setTextColor(HudColors.TEXT)
        setPadding(dp(6), dp(6), dp(6), dp(6))
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
        textSize = if (label.length > 5 && minWidthPx == 0) 10.5f else 11.5f
        maxLines = 1
        setPadding(dp(6), 0, dp(6), 0)
        setTextColor(if (selected) HudColors.ON_PRIMARY else HudColors.TEXT)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpF(7f)
            if (selected) {
                setColor(HudColors.PRIMARY)
            } else {
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(dp(1), HudColors.HAIRLINE)
            }
        }
        if (letterSpace) letterSpacing = 0.02f
        setOnClickListener { onClick() }
        val width = if (minWidthPx > 0) LayoutParams.WRAP_CONTENT else 0
        val weight = if (minWidthPx > 0) 0f else 1f
        layoutParams = LayoutParams(width, LayoutParams.MATCH_PARENT, weight).apply {
            marginEnd = dp(4)
        }
        if (minWidthPx > 0) minimumWidth = minWidthPx
        contentDescription = label
    }

    /** Peek is hold-only, so the segment names the gesture instead of leaving it to discover. */
    private fun peekSegment(): TextView = TextView(context).apply {
        text = context.getString(R.string.hud_peek)
        gravity = Gravity.CENTER
        textSize = 10.5f
        maxLines = 1
        setPadding(dp(6), 0, dp(6), 0)
        setTextColor(HudColors.TEXT)
        contentDescription = context.getString(R.string.hud_peek_description)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpF(7f)
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(dp(1), HudColors.HAIRLINE)
        }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    (v.background as? android.graphics.drawable.GradientDrawable)?.setColor(HudColors.PRIMARY_PRESSED)
                    host?.onPeek(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (v.background as? android.graphics.drawable.GradientDrawable)?.setColor(android.graphics.Color.TRANSPARENT)
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
