package app.serifu.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.serifu.core.Tuning
import app.serifu.core.model.PillSide
import kotlin.math.hypot
import kotlin.math.roundToInt

data class ScreenGeometry(
    val width: Int,
    val height: Int,
    val insetTop: Int,
    val insetBottom: Int,
)

interface PillHost {
    fun updateLayout(params: WindowManager.LayoutParams)
    fun geometry(): ScreenGeometry
    fun onTap()
    fun onDoubleTap()
    fun onOutside()
    fun onLongPress()
    fun onDocked(side: PillSide, yFraction: Float)
    fun hapticsEnabled(): Boolean
}

class FloatingControlPillView(context: Context) : View(context) {
    private val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaper.PAPER
        alpha = (0.35f * 255).roundToInt()
    }
    private val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = InkPaper.PAPER
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
        maskFilter = BlurMaskFilter(dpF(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val sealRect = RectF()
    private val ringRect = RectF()
    private var params: WindowManager.LayoutParams? = null
    private var host: PillHost? = null
    private var phase = PillPhase.AUTO_READY
    private var glyph = "日"
    private var idleAlpha = Tuning.PILL_IDLE_ALPHA_DEFAULT
    private var dockedEnd = true
    private var dragging = false
    private var springing = false
    private var longPressed = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var grabDx = 0f
    private var grabDy = 0f
    private var ringAlpha = 1f
    private var spin = 0f
    private var dash: DashPathEffect? = null
    private var spring: SpringAnimation? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
    private val doubleTapTimeout = 250L

    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private var isPotentialDoubleTap = false

    private var currentScale = 1.0f
    private var scaleAnimator: ValueAnimator? = null

    private fun animateScale(target: Float, durationMs: Long, overshoot: Boolean = false) {
        scaleAnimator?.cancel()
        val start = currentScale
        scaleAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs
            interpolator = if (overshoot) OvershootInterpolator(1.8f) else DecelerateInterpolator()
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateStamp() {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(1.0f, 0.85f, 1.06f, 1.0f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private val singleTapRunnable = Runnable {
        lastTapUpTime = 0L
        isPotentialDoubleTap = false
        host?.onTap()
    }

    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressed = true
            isPotentialDoubleTap = false
            lastTapUpTime = 0L
            removeCallbacks(singleTapRunnable)
            haptic(Haptic.LONG_PRESS)
            host?.onLongPress()
        }
    }
    private val idleRunnable = Runnable {
        animate().alpha(idleAlpha).setDuration(Tuning.PILL_ALPHA_FADE_MS.toLong()).start()
    }
    private val stopBreathe = Runnable {
        breathe.cancel()
        ringAlpha = 1f
        invalidate()
    }
    private val breathe = ValueAnimator.ofFloat(0.6f, 1f).apply {
        duration = Tuning.PILL_BREATHE_PERIOD_MS.toLong() / 2
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            ringAlpha = it.animatedValue as Float
            invalidate()
        }
    }
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = Tuning.PILL_SPINNER_TURN_MS.toLong()
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spin = it.animatedValue as Float
            invalidate()
        }
    }

    val isInteracting: Boolean get() = dragging || springing

    init {
        isClickable = true
        glyphPaint.textSize = sp(14f)
        hairline.strokeWidth = dpF(1f)
        ringPaint.strokeWidth = dpF(Tuning.PILL_RING_DP)
        val density = resources.displayMetrics.density
        dash = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }

    fun bind(params: WindowManager.LayoutParams, host: PillHost) {
        this.params = params
        this.host = host
    }

    fun render(glyph: String, phase: PillPhase, description: String, idleAlpha: Float) {
        val phaseChanged = this.phase != phase
        val glyphChanged = this.glyph != glyph
        this.glyph = glyph
        glyphPaint.textSize = sp(if (glyph.length > 1) 11.5f else 14f)
        this.phase = phase
        this.idleAlpha = idleAlpha
        contentDescription = description
        if (phaseChanged) syncAnimations()
        if (glyphChanged && isAttachedToWindow) animateStamp() else invalidate()
    }

    fun moveTo(side: PillSide, yFraction: Float) {
        if (isInteracting) return
        val layout = params ?: return
        val geo = host?.geometry() ?: return
        if (width == 0) {
            post { moveTo(side, yFraction) }
            return
        }
        dockedEnd = side == PillSide.END
        layout.x = dockedX(side, geo.width)
        layout.y = yFor(yFraction, geo)
        pushLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(Tuning.PILL_SIZE_DP)
        val pad = dp(4)
        setMeasuredDimension(size + pad * 2, size + pad * 2)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimations()
        wake()
    }

    override fun onDetachedFromWindow() {
        breathe.cancel()
        spinner.cancel()
        scaleAnimator?.cancel()
        spring?.cancel()
        removeCallbacks(longPressRunnable)
        removeCallbacks(singleTapRunnable)
        removeCallbacks(idleRunnable)
        removeCallbacks(stopBreathe)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                isPotentialDoubleTap = false
                lastTapUpTime = 0L
                removeCallbacks(singleTapRunnable)
                animateScale(1.0f, 120L)
                host?.onOutside()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                wake()
                spring?.cancel()
                springing = false
                dragging = false
                longPressed = false
                animateScale(0.92f, 80L)
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = event.eventTime
                val layout = params
                if (layout != null) {
                    grabDx = event.rawX - layout.x
                    grabDy = event.rawY - layout.y
                }
                val now = SystemClock.uptimeMillis()
                if (lastTapUpTime > 0L && (now - lastTapUpTime) < doubleTapTimeout &&
                    hypot(event.rawX - lastTapUpX, event.rawY - lastTapUpY) < doubleTapSlop
                ) {
                    isPotentialDoubleTap = true
                    removeCallbacks(singleTapRunnable)
                } else {
                    isPotentialDoubleTap = false
                }
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.rawX - downRawX, event.rawY - downRawY)
                if (!dragging && distance > slop) {
                    dragging = true
                    scaleAnimator?.cancel()
                    currentScale = 1.0f
                    isPotentialDoubleTap = false
                    lastTapUpTime = 0L
                    removeCallbacks(singleTapRunnable)
                    removeCallbacks(longPressRunnable)
                    setLayerType(LAYER_TYPE_SOFTWARE, null)
                }
                if (dragging) {
                    val layout = params ?: return true
                    val geo = host?.geometry()
                    layout.x = (event.rawX - grabDx).roundToInt()
                    val proposed = (event.rawY - grabDy).roundToInt()
                    layout.y = if (geo == null) proposed else proposed.coerceIn(minY(geo), maxY(geo))
                    pushLayout()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    dragging = false
                    isPotentialDoubleTap = false
                    lastTapUpTime = 0L
                    removeCallbacks(singleTapRunnable)
                    setLayerType(LAYER_TYPE_NONE, null)
                    springToNearestEdge()
                } else if (!longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                    animateScale(1.0f, 150L, overshoot = true)
                    if (isPotentialDoubleTap) {
                        isPotentialDoubleTap = false
                        lastTapUpTime = 0L
                        removeCallbacks(singleTapRunnable)
                        haptic(Haptic.CONFIRM)
                        host?.onDoubleTap()
                    } else {
                        lastTapUpTime = SystemClock.uptimeMillis()
                        lastTapUpX = event.rawX
                        lastTapUpY = event.rawY
                        removeCallbacks(singleTapRunnable)
                        postDelayed(singleTapRunnable, doubleTapTimeout)
                    }
                } else {
                    animateScale(1.0f, 150L, overshoot = true)
                    isPotentialDoubleTap = false
                    lastTapUpTime = 0L
                    removeCallbacks(singleTapRunnable)
                }
                wake()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = dpF(Tuning.PILL_SIZE_DP / 2f)
        sealRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val activeScale = if (dragging) Tuning.PILL_DRAG_SCALE else currentScale
        val hasScale = activeScale != 1f

        if (dragging) {
            canvas.drawCircle(cx, cy + dpF(3f), radius + dpF(2f), shadowPaint)
        }
        if (hasScale) {
            canvas.save()
            canvas.scale(activeScale, activeScale, cx, cy)
        }

        sealPaint.color = if (phase == PillPhase.OFF) InkPaper.SCREENTONE else InkPaper.HANKO
        canvas.drawCircle(cx, cy, radius, sealPaint)

        val inset = hairline.strokeWidth / 2f
        canvas.drawCircle(cx, cy, radius - inset, hairline)

        drawRing(canvas, cx, cy, radius)

        glyphPaint.color = InkPaper.PAPER
        val baseline = cy - (glyphPaint.ascent() + glyphPaint.descent()) / 2f
        canvas.drawText(glyph, cx, baseline, glyphPaint)

        if (hasScale) canvas.restore()
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val ringRadius = radius - dpF(Tuning.PILL_RING_INSET_DP.toFloat()) - ringPaint.strokeWidth / 2f
        ringRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        ringPaint.pathEffect = null
        ringPaint.alpha = 255
        when (phase) {
            PillPhase.AUTO_READY -> {
                ringPaint.color = InkPaper.PAPER
                ringPaint.alpha = (ringAlpha * 255).roundToInt().coerceIn(0, 255)
                canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
            }
            PillPhase.MANUAL -> {
                ringPaint.color = InkPaper.PAPER
                canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
            }
            PillPhase.PAUSED -> {
                ringPaint.color = InkPaper.PAPER
                ringPaint.pathEffect = dash
                canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
            }
            PillPhase.PROCESSING -> {
                ringPaint.color = InkPaper.PAPER
                canvas.drawArc(ringRect, spin, 90f, false, ringPaint)
            }
            PillPhase.ERROR -> {
                ringPaint.color = InkPaper.AMBER
                canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
            }
            PillPhase.OFF -> {
                ringPaint.color = InkPaper.PAPER
                ringPaint.alpha = 90
                canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
            }
        }
    }

    private fun syncAnimations() {
        if (phase == PillPhase.AUTO_READY) {
            if (!breathe.isRunning) {
                breathe.start()
                removeCallbacks(stopBreathe)
                postDelayed(stopBreathe, Tuning.PILL_BREATHE_STOP_MS)
            }
        } else {
            breathe.cancel()
            removeCallbacks(stopBreathe)
            ringAlpha = 1f
        }
        if (phase == PillPhase.PROCESSING) {
            if (!spinner.isRunning) spinner.start()
        } else {
            spinner.cancel()
        }
    }

    private fun springToNearestEdge() {
        val layout = params ?: return
        val geo = host?.geometry() ?: return
        val side = if (layout.x + width / 2 < geo.width / 2) PillSide.START else PillSide.END
        dockedEnd = side == PillSide.END
        val target = dockedX(side, geo.width).toFloat()
        springing = true
        spring?.cancel()
        val anim = SpringAnimation(FloatValueHolder(layout.x.toFloat())).apply {
            spring = SpringForce(target).apply {
                stiffness = Tuning.PILL_SPRING_STIFFNESS
                dampingRatio = Tuning.PILL_SPRING_DAMPING
            }
            addUpdateListener { _, value, _ ->
                layout.x = value.roundToInt()
                pushLayout()
            }
            addEndListener { _, canceled, _, _ ->
                springing = false
                if (!canceled) {
                    layout.x = target.roundToInt()
                    pushLayout()
                    haptic(Haptic.EDGE)
                    val fraction = if (geo.height == 0) Tuning.PILL_Y_FRACTION_DEFAULT else {
                        layout.y.toFloat() / geo.height
                    }
                    host?.onDocked(side, fraction.coerceIn(0f, 1f))
                    invalidate()
                }
            }
        }
        spring = anim
        anim.start()
    }

    private fun dockedX(side: PillSide, screenWidth: Int): Int {
        val off = dp(Tuning.PILL_OFFSCREEN_DP)
        return if (side == PillSide.END) screenWidth - width + off else -off
    }

    private fun yFor(fraction: Float, geo: ScreenGeometry): Int {
        val proposed = (fraction * geo.height).roundToInt()
        return proposed.coerceIn(minY(geo), maxY(geo))
    }

    private fun minY(geo: ScreenGeometry) = geo.insetTop

    private fun maxY(geo: ScreenGeometry) = (geo.height - geo.insetBottom - height).coerceAtLeast(minY(geo))

    private fun pushLayout() {
        val layout = params ?: return
        try {
            host?.updateLayout(layout)
        } catch (_: IllegalArgumentException) {
            // Window was removed between the touch and the layout update.
        }
    }

    private fun wake() {
        animate().cancel()
        animate().alpha(1f).setDuration(Tuning.PILL_ALPHA_FADE_MS.toLong()).start()
        removeCallbacks(idleRunnable)
        postDelayed(idleRunnable, Tuning.PILL_IDLE_DELAY_MS)
    }

    private fun haptic(kind: Haptic) {
        if (host?.hapticsEnabled() != true) return
        val constant = when (kind) {
            Haptic.EDGE -> if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            Haptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            Haptic.CONFIRM -> HapticFeedbackConstants.CONFIRM
        }
        performHapticFeedback(constant)
    }

    fun confirmHaptic() = haptic(Haptic.CONFIRM)

    private enum class Haptic { EDGE, LONG_PRESS, CONFIRM }
}
