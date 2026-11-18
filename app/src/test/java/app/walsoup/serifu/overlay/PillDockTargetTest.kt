package app.walsoup.serifu.overlay

import android.app.Activity
import android.os.Looper
import android.view.WindowManager
import app.serifu.core.Tuning
import app.serifu.core.model.PillSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import app.walsoup.serifu.SerifuApp
import org.robolectric.annotation.GraphicsMode

/**
 * Docked pill touch target: the seal keeps its deliberate edge exposure
 * (PILL_OFFSCREEN_DP off screen) while the on-screen touchable width stays
 * at or above 48dp thanks to the transparent extension strip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = SerifuApp::class)
class PillDockTargetTest {

    private val host = object : PillHost {
        override fun updateLayout(params: WindowManager.LayoutParams) = Unit
        override fun geometry() = ScreenGeometry(1080, 1920, 0, 0)
        override fun onTap() = Unit
        override fun onOutside() = Unit
        override fun onLongPress() = Unit
        override fun onDocked(side: PillSide, yFraction: Float) = Unit
        override fun hapticsEnabled() = false
    }

    private fun dockedPill(side: PillSide): Pair<FloatingControlPillView, WindowManager.LayoutParams> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val params = WindowManager.LayoutParams()
        val pill = FloatingControlPillView(activity)
        pill.bind(params, host)
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        pill.measure(0, 0)
        pill.layout(0, 0, pill.measuredWidth, pill.measuredHeight)
        pill.moveTo(side, 0.5f)
        shadowOf(Looper.getMainLooper()).idle()
        return pill to params
    }

    @Test
    fun dockedEndKeeps48dpOnScreenTarget() {
        val (pill, params) = dockedPill(PillSide.END)
        val density = pill.resources.displayMetrics.density
        val onScreenPx = 1080 - params.x
        assertTrue(
            "on-screen target ${onScreenPx / density}dp must be >= 48dp",
            onScreenPx >= (48 * density).toInt(),
        )
    }

    @Test
    fun dockedStartKeeps48dpOnScreenTarget() {
        val (pill, params) = dockedPill(PillSide.START)
        val density = pill.resources.displayMetrics.density
        val onScreenPx = params.x + pill.width
        assertTrue(
            "on-screen target ${onScreenPx / density}dp must be >= 48dp",
            onScreenPx >= (48 * density).toInt(),
        )
    }

    @Test
    fun dockedEndKeepsEdgeExposureUnchanged() {
        val (pill, params) = dockedPill(PillSide.END)
        val density = pill.resources.displayMetrics.density
        val offScreenPx = params.x + pill.width - 1080
        assertEquals((Tuning.PILL_OFFSCREEN_DP * density).toInt(), offScreenPx)
    }

    @Test
    fun dockedStartKeepsEdgeExposureUnchanged() {
        val (pill, params) = dockedPill(PillSide.START)
        val density = pill.resources.displayMetrics.density
        assertEquals(-(Tuning.PILL_OFFSCREEN_DP * density).toInt(), params.x)
    }
}
