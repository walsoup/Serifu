package app.walsoup.serifu.overlay

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import app.serifu.core.Tuning
import app.serifu.core.model.PillSide
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.ui.design.DarkPalette
import app.walsoup.serifu.ui.design.LightPalette
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OverlayUiTest {
    @Test
    fun hudLightAndDarkThemesDrawDifferentPixels() {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val widthPx = (Tuning.HUD_WIDTH_DP * density).roundToInt()
        val light = draw(hud(context(night = false)), widthPx)
        val dark = draw(hud(context(night = true)), widthPx)

        assertTrue(light.width > 0 && light.height > 0)
        assertTrue(dark.width > 0 && dark.height > 0)
        assertFalse("the two renders must not be identical", light.sameAs(dark))

        // Real background pixels: bottom-centre is inside the panel fill, away from text.
        val lightBg = light.getPixel(light.width / 2, light.height - 2)
        val darkBg = dark.getPixel(dark.width / 2, dark.height - 2)
        assertNotEquals("background must change with the theme", lightBg, darkBg)

        // Real text pixels: the status line and segments use the palette text token.
        val lightText = LightPalette.text.toArgb()
        val darkText = DarkPalette.text.toArgb()
        assertNotEquals("text token must change with the theme", lightText, darkText)
        assertTrue("light HUD must draw text pixels", containsColor(light, lightText))
        assertTrue("dark HUD must draw text pixels", containsColor(dark, darkText))

        // The rendered background is the palette surface token, not an ad-hoc colour.
        assertTrue("light HUD background is the light surface token", containsColor(light, LightPalette.surface.toArgb()))
        assertTrue("dark HUD background is the dark surface token", containsColor(dark, DarkPalette.surface.toArgb()))
    }

    @Test
    fun hudPauseTapFiresHostOnce() {
        var toggles = 0
        val view = hud(
            RuntimeEnvironment.getApplication(),
            object : HudHost {
                override fun onSource(language: SourceLanguage) = Unit
                override fun onMode(mode: TriggerMode) = Unit
                override fun onPauseToggle() { toggles++ }
                override fun onPeek(held: Boolean) = Unit
                override fun onOpenApp() = Unit
                override fun onDismiss() = Unit
            },
        )
        layout(view)
        val pause = findText(view, "Pause") ?: error("Pause control missing")
        tap(pause)
        assertEquals("one tap is one toggle", 1, toggles)
    }

    @Test
    fun pillDoubleTapIsANoOp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = FloatingControlPillView(activity)
        var taps = 0
        var longPresses = 0
        pill.bind(
            WindowManager.LayoutParams(),
            object : PillHost {
                override fun updateLayout(params: WindowManager.LayoutParams) = Unit
                override fun geometry() = ScreenGeometry(1080, 1920, 0, 0)
                override fun onTap() { taps++ }
                override fun onOutside() = Unit
                override fun onLongPress() { longPresses++ }
                override fun onDocked(side: PillSide, yFraction: Float) = Unit
                override fun hapticsEnabled() = false
            },
        )
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        activity.setContentView(pill)
        shadowOf(Looper.getMainLooper()).idle()
        tapAt(pill, 100f, 100f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        tapAt(pill, 100f, 100f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals("a double tap must not translate", 0, taps)
        assertEquals("a double tap must not open the HUD", 0, longPresses)
    }

    @Test
    fun pillLongPressOpensHud() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = FloatingControlPillView(activity)
        var taps = 0
        var longPresses = 0
        pill.bind(
            WindowManager.LayoutParams(),
            object : PillHost {
                override fun updateLayout(params: WindowManager.LayoutParams) = Unit
                override fun geometry() = ScreenGeometry(1080, 1920, 0, 0)
                override fun onTap() { taps++ }
                override fun onOutside() = Unit
                override fun onLongPress() { longPresses++ }
                override fun onDocked(side: PillSide, yFraction: Float) = Unit
                override fun hapticsEnabled() = false
            },
        )
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        activity.setContentView(pill)
        shadowOf(Looper.getMainLooper()).idle()
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        pill.dispatchTouchEvent(down)
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100),
        )
        assertEquals("long press opens the HUD", 1, longPresses)
        assertEquals("long press must not also translate", 0, taps)
        val up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 100f, 100f, 0)
        pill.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        assertEquals("release after a long press does not translate", 0, taps)
    }

    @Test
    fun pillLongPressFiresHaptic() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = pillForHaptics(activity, WindowManager.LayoutParams(), haptics = true)
        activity.setContentView(pill)
        shadowOf(Looper.getMainLooper()).idle()
        longPress(pill, activity)
        assertEquals(
            "long press fires the LONG_PRESS haptic",
            HapticFeedbackConstants.LONG_PRESS,
            shadowOf(pill).lastHapticFeedbackPerformed(),
        )
    }

    @Test
    fun pillDockFiresEdgeHaptic() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = pillForHaptics(activity, WindowManager.LayoutParams(), haptics = true)
        activity.setContentView(pill)
        shadowOf(Looper.getMainLooper()).idle()
        measurePill(pill)
        dragAndRelease(pill)
        assertEquals(
            "dock fires the sdk 33 edge tick",
            HapticFeedbackConstants.CLOCK_TICK,
            awaitHaptic(pill),
        )
    }

    @Test
    fun pillHapticsDisabledIsSilent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = pillForHaptics(activity, WindowManager.LayoutParams(), haptics = false)
        activity.setContentView(pill)
        shadowOf(Looper.getMainLooper()).idle()
        measurePill(pill)
        longPress(pill, activity)
        dragAndRelease(pill)
        repeat(125) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        assertEquals(
            "disabled haptics must stay silent",
            -1,
            shadowOf(pill).lastHapticFeedbackPerformed(),
        )
    }

    @Test
    fun hudDismissTimerCallsHostOnce() {
        var dismissals = 0
        val view = HudView(RuntimeEnvironment.getApplication())
        view.bind(object : HudHost {
            override fun onSource(language: SourceLanguage) = Unit
            override fun onMode(mode: TriggerMode) = Unit
            override fun onPauseToggle() = Unit
            override fun onPeek(held: Boolean) = Unit
            override fun onOpenApp() = Unit
            override fun onDismiss() { dismissals++ }
        })
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view)
        view.render(
            HudModel(
                source = SourceLanguage.JA,
                mode = TriggerMode.AUTO,
                paused = false,
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("HUD stays up before the timer", 0, dismissals)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Tuning.HUD_DISMISS_MS + 100))
        assertEquals("HUD dismisses exactly once", 1, dismissals)
    }

    @Test
    fun hudPeekPressShowsOriginalAndReleaseRestores() {
        var held = 0
        var released = 0
        val view = hud(
            RuntimeEnvironment.getApplication(),
            object : HudHost {
                override fun onSource(language: SourceLanguage) = Unit
                override fun onMode(mode: TriggerMode) = Unit
                override fun onPauseToggle() = Unit
                override fun onPeek(isHeld: Boolean) {
                    if (isHeld) held++ else released++
                }
                override fun onOpenApp() = Unit
                override fun onDismiss() = Unit
            },
        )
        layout(view)
        val peek = findText(view, "Hold to peek") ?: error("Peek control missing")
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, 1f, 1f, 0)
        peek.dispatchTouchEvent(down)
        assertEquals("pressing peek shows the original page", 1, held)
        assertEquals("the page stays original while held", 0, released)
        peek.dispatchTouchEvent(up)
        assertEquals("releasing peek restores the translation", 1, released)
        down.recycle()
        up.recycle()
    }

    @Test
    fun pillRendersPerPhase() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val widthPx = ((Tuning.PILL_SIZE_DP + 8) * activity.resources.displayMetrics.density).roundToInt()
        val bitmaps = PillPhase.entries.map { phase ->
            val pill = FloatingControlPillView(activity)
            pill.bind(WindowManager.LayoutParams(), NoopPillHost)
            pill.render("日", phase, phase.name, 1f)
            draw(pill, widthPx)
        }
        bitmaps.forEachIndexed { index, bitmap ->
            assertTrue("phase ${PillPhase.entries[index]} must draw pixels", bitmap.hasOpaquePixel())
        }
        assertTrue(
            "at least two phases must look different",
            bitmaps.any { first -> bitmaps.any { second -> first !== second && !first.sameAs(second) } },
        )
    }

    private fun context(night: Boolean): Context {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return base.createConfigurationContext(config)
    }

    private fun layout(view: View) {
        val width = (Tuning.HUD_WIDTH_DP * view.resources.displayMetrics.density).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)
    }

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findText(view.getChildAt(index), text)?.let { return it }
            }
        }
        return null
    }

    private fun tap(view: View) {
        tapAt(view, (view.width / 2f).coerceAtLeast(1f), (view.height / 2f).coerceAtLeast(1f))
    }

    private fun tapAt(view: View, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun pillForHaptics(
        activity: Activity,
        params: WindowManager.LayoutParams,
        haptics: Boolean,
    ): FloatingControlPillView {
        val pill = FloatingControlPillView(activity)
        pill.bind(
            params,
            object : PillHost {
                override fun updateLayout(params: WindowManager.LayoutParams) = Unit
                override fun geometry() = ScreenGeometry(1080, 1920, 0, 0)
                override fun onTap() = Unit
                override fun onOutside() = Unit
                override fun onLongPress() = Unit
                override fun onDocked(side: PillSide, yFraction: Float) = Unit
                override fun hapticsEnabled() = haptics
            },
        )
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        return pill
    }

    private fun measurePill(pill: View) {
        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        pill.layout(0, 0, pill.measuredWidth, pill.measuredHeight)
    }

    private fun longPress(pill: View, activity: Activity) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        pill.dispatchTouchEvent(down)
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100),
        )
        val up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 100f, 100f, 0)
        pill.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun dragAndRelease(pill: View) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val move = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 700f, 100f, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, 700f, 100f, 0)
        pill.dispatchTouchEvent(down)
        pill.dispatchTouchEvent(move)
        pill.dispatchTouchEvent(up)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    private fun awaitHaptic(view: View): Int {
        var elapsed = 0L
        while (shadowOf(view).lastHapticFeedbackPerformed() == -1 && elapsed < 2_000L) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            elapsed += 16
        }
        return shadowOf(view).lastHapticFeedbackPerformed()
    }

    private fun hud(context: Context, host: HudHost = NoopHudHost): HudView {
        val view = HudView(context)
        view.bind(host)
        view.render(
            HudModel(
                source = SourceLanguage.JA,
                mode = TriggerMode.AUTO,
                paused = false,
                phaseLabel = "Translating page 1",
            ),
        )
        return view
    }

    private fun draw(view: View, widthPx: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, widthPx, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(widthPx, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == color) return true
            }
        }
        return false
    }

    private fun Bitmap.hasOpaquePixel(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) != 0) return true
            }
        }
        return false
    }
}

private val NoopPillHost = object : PillHost {
    override fun updateLayout(params: WindowManager.LayoutParams) = Unit
    override fun geometry() = ScreenGeometry(1080, 1920, 0, 0)
    override fun onTap() = Unit
    override fun onOutside() = Unit
    override fun onLongPress() = Unit
    override fun onDocked(side: PillSide, yFraction: Float) = Unit
    override fun hapticsEnabled() = false
}

private val NoopHudHost = object : HudHost {
    override fun onSource(language: SourceLanguage) = Unit
    override fun onMode(mode: TriggerMode) = Unit
    override fun onPauseToggle() = Unit
    override fun onPeek(held: Boolean) = Unit
    override fun onOpenApp() = Unit
    override fun onDismiss() = Unit
}
