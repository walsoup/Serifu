package app.serifu.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import app.serifu.capture.VerticalInsets
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.model.PillSide
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode

interface OverlayEvents {
    fun onPillTap()
    fun onPillDoubleTap()
    fun onPillOutside()
    fun onPillLongPress()
    fun onPillDocked(side: PillSide, yFraction: Float)
    fun hapticsEnabled(): Boolean
    fun onSource(language: SourceLanguage)
    fun onMode(mode: TriggerMode)
    fun onPauseToggle()
    fun onPeek(held: Boolean)
    fun onOpenApp()
    fun onHudDismissed()
}

class OverlayManager(
    private val service: AccessibilityService,
) : PillHost, HudHost {
    private val wm = service.getSystemService(WindowManager::class.java)
    val canvas = TranslationCanvasView(service)
    val pill = FloatingControlPillView(service)
    private val hud = HudView(service)
    private val tooltip = TooltipView(service)
    private val tooltipDismiss = Runnable { dismissTooltip() }
    private var attached = false
    var events: OverlayEvents? = null

    val isHudShowing: Boolean get() = hud.parent != null

    fun attach() {
        if (attached) return
        val pillLayout = window(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH or
                FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
        )
        pill.bind(pillLayout, this)
        hud.bind(this)
        wm.addView(canvas, window(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
        ))
        wm.addView(pill, pillLayout)
        canvas.hideImmediately()
        attached = true
    }

    fun detach() {
        if (!attached) return
        dismissTooltip()
        dismissHudImmediately()
        remove(canvas)
        remove(pill)
        attached = false
    }

    fun applyPill(
        glyph: String,
        phase: PillPhase,
        description: String,
        idleAlpha: Float,
        side: PillSide,
        yFraction: Float,
    ) {
        pill.render(glyph, phase, description, idleAlpha)
        pill.moveTo(side, yFraction)
    }

    fun setPillVisible(visible: Boolean) {
        pill.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) dismissHud(animated = false)
    }

    fun showHud(model: HudModel) {
        if (isHudShowing) {
            dismissHud(animated = true)
            return
        }
        hud.render(model)
        val geo = geometry()
        val pillRect = pillRectOnScreen() ?: return
        val width = service.dp(Tuning.HUD_WIDTH_DP) + service.dp(3)
        hud.tailTowardEnd = pillRect.centerX > geo.width / 2
        hud.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = hud.measuredHeight.coerceAtLeast(service.dp(44))
        val params = window(width, height, PILL_FLAGS)
        val gap = service.dp(8)
        params.x = if (hud.tailTowardEnd) pillRect.left - width - gap else pillRect.right + gap
        params.y = (pillRect.centerY - height / 2).coerceIn(geo.insetTop, (geo.height - height).coerceAtLeast(geo.insetTop))
        hud.alpha = 0f
        hud.scaleX = 0.92f
        hud.scaleY = 0.92f
        wm.addView(hud, params)
        hud.pivotX = if (hud.tailTowardEnd) width.toFloat() else 0f
        hud.pivotY = height / 2f
        hud.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(Tuning.HUD_ENTRANCE_MS.toLong())
            .setInterpolator(HudView.ENTRANCE)
            .start()
    }

    fun dismissHud(animated: Boolean) {
        if (hud.parent == null) return
        hud.stopTimer()
        if (!animated) {
            remove(hud)
            events?.onHudDismissed()
            return
        }
        hud.animate().alpha(0f).setDuration(Tuning.HUD_EXIT_MS.toLong()).withEndAction {
            remove(hud)
            events?.onHudDismissed()
        }.start()
    }

    fun dismissHudImmediately() {
        if (hud.parent == null) return
        hud.animate().cancel()
        hud.stopTimer()
        remove(hud)
    }

    fun showTooltip(message: String) {
        tooltip.setMessage(message)
        val pillRect = pillRectOnScreen() ?: return
        val geo = geometry()
        tooltip.tailOnEnd = pillRect.centerX >= geo.width / 2
        val width = tooltip.measuredWidthPx()
        val height = tooltip.measuredHeightPx()
        val params = window(
            width,
            height,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
        )
        val gap = service.dp(8)
        params.x = if (tooltip.tailOnEnd) pillRect.left - width - gap else pillRect.right + gap
        params.y = (pillRect.centerY - height / 2).coerceIn(
            geo.insetTop,
            (geo.height - geo.insetBottom - height).coerceAtLeast(geo.insetTop),
        )
        if (tooltip.parent == null) wm.addView(tooltip, params) else wm.updateViewLayout(tooltip, params)
        tooltip.removeCallbacks(tooltipDismiss)
        tooltip.postDelayed(tooltipDismiss, Tuning.TOOLTIP_DISMISS_MS)
    }

    fun dismissTooltip() {
        tooltip.removeCallbacks(tooltipDismiss)
        remove(tooltip)
    }

    fun reposition(side: PillSide, yFraction: Float) {
        dismissHudImmediately()
        pill.moveTo(side, yFraction)
    }

    fun pillRectOnScreen(): IntRect? {
        if (!attached || pill.visibility != View.VISIBLE || pill.width == 0) return null
        val location = IntArray(2)
        pill.getLocationOnScreen(location)
        return IntRect(location[0], location[1], location[0] + pill.width, location[1] + pill.height)
    }

    fun systemBars(): VerticalInsets {
        val insets = canvas.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            ?: pill.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        return VerticalInsets(insets?.top ?: 0, insets?.bottom ?: 0)
    }

    /**
     * AccessibilityService is not a visual context on Android 11+, so `service.display`
     * throws. Ask DisplayManager for the default display instead.
     */
    fun rotation(): Int {
        val display = service.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        return display?.rotation ?: Surface.ROTATION_0
    }

    override fun geometry(): ScreenGeometry {
        val bounds = wm.currentWindowMetrics.bounds
        val bars = systemBars()
        return ScreenGeometry(bounds.width(), bounds.height(), bars.top, bars.bottom)
    }

    override fun updateLayout(params: WindowManager.LayoutParams) {
        if (pill.parent != null) wm.updateViewLayout(pill, params)
    }

    override fun onTap() = events?.onPillTap() ?: Unit

    override fun onDoubleTap() = events?.onPillDoubleTap() ?: Unit

    override fun onOutside() = events?.onPillOutside() ?: Unit

    override fun onLongPress() = events?.onPillLongPress() ?: Unit

    override fun onDocked(side: PillSide, yFraction: Float) {
        events?.onPillDocked(side, yFraction)
    }

    override fun hapticsEnabled(): Boolean = events?.hapticsEnabled() == true

    override fun onSource(language: SourceLanguage) {
        events?.onSource(language)
    }

    override fun onMode(mode: TriggerMode) {
        events?.onMode(mode)
    }

    override fun onPauseToggle() {
        events?.onPauseToggle()
    }

    override fun onPeek(held: Boolean) {
        events?.onPeek(held)
    }

    override fun onOpenApp() {
        events?.onOpenApp()
    }

    override fun onDismiss() {
        dismissHud(animated = true)
    }

    private fun window(width: Int, height: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }

    private fun remove(view: View) {
        if (view.parent != null) {
            try {
                wm.removeView(view)
            } catch (_: IllegalArgumentException) {
                // Already detached.
            }
        }
    }

    private companion object {
        const val FLAG_NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        const val FLAG_NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        const val FLAG_NOT_TOUCH_MODAL = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        const val FLAG_WATCH_OUTSIDE_TOUCH = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        const val FLAG_LAYOUT_IN_SCREEN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val FLAG_LAYOUT_NO_LIMITS = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        const val PILL_FLAGS = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH or
            FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS
    }
}
