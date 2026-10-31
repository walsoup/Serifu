package app.serifu.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import app.serifu.core.Tuning
import android.view.animation.PathInterpolator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class TranslationCanvasView(context: Context) : View(context) {
    private val painter = DebugOverlayPainter()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val strokeRect = RectF()
    private val location = IntArray(2)
    private var page: RenderPage? = null
    private var debug = false
    private var timings: app.serifu.debug.PipelineTimings? = null
    private var originX = 0
    private var originY = 0
    var hiddenSinceUptimeMs: Long = SystemClock.uptimeMillis()
        private set

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setWillNotDraw(false)
    }

    fun hideImmediately() {
        animate().cancel()
        alpha = 0f
        visibility = INVISIBLE
        hiddenSinceUptimeMs = SystemClock.uptimeMillis()
    }

    fun clearPage() {
        page = null
        debug = false
        timings = null
        hideImmediately()
    }

    fun updateTimings(timings: app.serifu.debug.PipelineTimings) {
        this.timings = timings
        if (debug && visibility == VISIBLE) invalidate()
    }

    fun showPage(page: RenderPage, debug: Boolean) {
        this.page = page
        this.debug = debug
        refreshOrigin()
        visibility = VISIBLE
        animate().cancel()
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(Tuning.SHOW_FADE_MS.toLong())
            .setInterpolator(SHOW_INTERPOLATOR)
            .start()
        invalidate()
    }

    /** Per-bubble upgrade crossfade (plan.md 12.5): identical bubbles do not animate. */
    fun crossfadeTo(next: RenderPage, debug: Boolean) {
        val current = page
        if (current == null || current.bubbles.isEmpty()) {
            showPage(next, debug)
            return
        }
        val currentTexts = current.bubbles.associate { it.id to it.layout.text.toString() }
        val changed = next.bubbles.any { currentTexts[it.id] != it.layout.text.toString() }
        if (!changed) {
            // Same text everywhere: just refresh debug flags without animating.
            this.page = next
            this.debug = debug
            invalidate()
            return
        }
        this.page = next
        this.debug = debug
        refreshOrigin()
        visibility = VISIBLE
        animate().cancel()
        alpha = 0.35f
        animate()
            .alpha(1f)
            .setDuration(Tuning.UPGRADE_CROSSFADE_MS.toLong())
            .setInterpolator(SHOW_INTERPOLATOR)
            .start()
        invalidate()
    }

    fun peek(held: Boolean) {
        if (visibility != VISIBLE && !held) return
        animate().cancel()
        animate()
            .alpha(if (held) 0f else 1f)
            .setDuration((if (held) Tuning.PEEK_HIDE_MS else Tuning.PEEK_SHOW_MS).toLong())
            .start()
    }

    suspend fun awaitOffScreen() {
        val alreadyHidden = visibility == INVISIBLE &&
            SystemClock.uptimeMillis() - hiddenSinceUptimeMs >= Tuning.OFFSCREEN_SAFE_MS
        if (alreadyHidden) return
        hideImmediately()
        withTimeoutOrNull(Tuning.FRAME_COMMIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val observer = viewTreeObserver
                val callback = Runnable {
                    if (cont.isActive) cont.resume(Unit)
                }
                if (!observer.isAlive) {
                    cont.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                observer.registerFrameCommitCallback(callback)
                cont.invokeOnCancellation {
                    if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                }
                invalidate()
            }
        }
        awaitNextVsync()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        refreshOrigin()
    }

    override fun onDraw(canvas: Canvas) {
        val drawn = page ?: return
        if (drawn.bubbles.isEmpty() && (!debug || drawn.debugLines.isEmpty() && drawn.debugClusters.isEmpty())) return
        val density = resources.displayMetrics.density
        val edgePx = Tuning.PATCH_EDGE_STROKE_DP * density
        val outlinePx = Tuning.OVERLAP_OUTLINE_DP * density
        canvas.save()
        canvas.translate(-originX.toFloat(), -originY.toFloat())
        for (bubble in drawn.bubbles) {
            if (bubble.fillColor == 0) continue
            canvas.save()
            if (bubble.rotationDeg != 0f) {
                canvas.rotate(bubble.rotationDeg, bubble.patch.centerX(), bubble.patch.centerY())
            }
            fillPaint.color = bubble.fillColor
            val clean = bubble.cleanPath
            if (clean != null) {
                canvas.drawPath(clean, fillPaint)
                if (bubble.seamless) {
                    strokePaint.strokeWidth = Tuning.CLEAN_FEATHER_PX
                    strokePaint.color = bubble.fillColor
                    strokePaint.alpha = Tuning.PATCH_EDGE_STROKE_ALPHA
                    canvas.drawPath(clean, strokePaint)
                }
            } else {
                canvas.drawRoundRect(bubble.patch, bubble.patchCornerPx, bubble.patchCornerPx, fillPaint)
                if (bubble.seamless) {
                    strokePaint.strokeWidth = edgePx
                    strokePaint.color = bubble.fillColor
                    strokePaint.alpha = Tuning.PATCH_EDGE_STROKE_ALPHA
                    val grow = edgePx / 2f
                    strokeRect.set(bubble.patch)
                    strokeRect.inset(-grow, -grow)
                    canvas.drawRoundRect(
                        strokeRect,
                        bubble.patchCornerPx + grow,
                        bubble.patchCornerPx + grow,
                        strokePaint,
                    )
                }
            }
            if (bubble.outlineColor != 0) {
                strokePaint.strokeWidth = outlinePx
                strokePaint.color = bubble.outlineColor
                if (clean != null) {
                    canvas.drawPath(clean, strokePaint)
                } else {
                    canvas.drawRoundRect(bubble.patch, bubble.patchCornerPx, bubble.patchCornerPx, strokePaint)
                }
            }
            canvas.restore()
        }
        for (bubble in drawn.bubbles) {
            canvas.save()
            if (bubble.rotationDeg != 0f) {
                canvas.rotate(bubble.rotationDeg, bubble.patch.centerX(), bubble.patch.centerY())
            }
            if (bubble.kind != app.serifu.core.model.BubbleKind.SFX && bubble.fillColor != 0) {
                // Lettering never leaves the box it was detected in.
                canvas.clipRect(bubble.patch)
            }
            canvas.translate(bubble.layoutOrigin.x, bubble.layoutOrigin.y)
            if (bubble.kind == app.serifu.core.model.BubbleKind.SFX || bubble.fillColor == 0) {
                drawOutlinedLayout(canvas, bubble.layout, density)
            } else {
                bubble.layout.draw(canvas)
            }
            canvas.restore()
        }
        canvas.restore()
        if (debug) painter.draw(canvas, drawn, originX, originY, density, timings)
    }

    private fun drawOutlinedLayout(canvas: Canvas, layout: android.text.StaticLayout, density: Float) {
        val paint = layout.paint
        val origStyle = paint.style
        val origColor = paint.color
        val origStrokeWidth = paint.strokeWidth
        val origStrokeJoin = paint.strokeJoin
        val origStrokeCap = paint.strokeCap

        val strokeWidthPx = Tuning.SFX_OUTLINE_STROKE_DP * density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidthPx
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Tuning.INK_ARGB

        layout.draw(canvas)

        paint.style = Paint.Style.FILL
        paint.color = Tuning.PAPER_ARGB
        layout.draw(canvas)

        paint.style = origStyle
        paint.color = origColor
        paint.strokeWidth = origStrokeWidth
        paint.strokeJoin = origStrokeJoin
        paint.strokeCap = origStrokeCap
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isVisibleToUser = false
    }

    private fun refreshOrigin() {
        if (!isAttachedToWindow) return
        getLocationOnScreen(location)
        originX = location[0]
        originY = location[1]
    }

    private suspend fun awaitNextVsync() {
        suspendCancellableCoroutine { cont ->
            val choreographer = Choreographer.getInstance()
            val callback = Choreographer.FrameCallback {
                if (cont.isActive) cont.resume(Unit)
            }
            choreographer.postFrameCallback(callback)
            cont.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        }
    }

    private companion object {
        val SHOW_INTERPOLATOR = PathInterpolator(0f, 0f, 0.2f, 1f)
    }
}
