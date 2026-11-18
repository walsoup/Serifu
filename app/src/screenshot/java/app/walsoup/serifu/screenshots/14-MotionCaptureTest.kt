package app.walsoup.serifu.screenshots

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import app.serifu.core.Tuning
import app.serifu.core.model.PillSide
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.overlay.FloatingControlPillView
import app.walsoup.serifu.overlay.HudHost
import app.walsoup.serifu.overlay.HudModel
import app.walsoup.serifu.overlay.HudView
import app.walsoup.serifu.overlay.InkPaper
import app.walsoup.serifu.overlay.PillHost
import app.walsoup.serifu.overlay.PillPhase
import app.walsoup.serifu.overlay.ScreenGeometry
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records overlay motion as PNG frame sequences. The frames are assembled into
 * GIFs outside the repo, so each file is a plain render of the view at a fixed
 * 16 ms animation step: two pre-roll frames, the animation, then two settled
 * frames. Timings come from the production animation calls, never padding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = SerifuApp::class)
class MotionCaptureTest {
    @Test
    fun captureMotion() {
        val dir = File(File(requireProperty("serifu.screenshot.dir")).parentFile, "motion")
        dir.deleteRecursively()
        dir.mkdirs()
        capturePillPress(dir)
        captureHudAppear(dir)
        captureHudDismiss(dir)
        capturePillDock(dir)
    }

    private fun capturePillPress(dir: File) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pill = FloatingControlPillView(activity)
        pill.bind(WindowManager.LayoutParams(), NoopPillHost)
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        layoutPill(pill)
        val sequence = Sequence(dir, "pill-press")
        val frame = { drawView(pill) }
        sequence.preRoll(frame)

        val cx = pill.width / 2f
        val cy = pill.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
        pill.dispatchTouchEvent(down)
        sequence.animation(PRESS_DOWN_FRAMES, frame)

        val up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cx, cy, 0)
        pill.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        sequence.animation(PRESS_UP_FRAMES, frame)
        sequence.settle(frame)
    }

    private fun captureHudAppear(dir: File) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val hud = hud(activity)
        activity.setContentView(hud)
        shadowOf(Looper.getMainLooper()).idle()
        layoutHud(hud, hudWidthPx(activity))
        val sequence = Sequence(dir, "hud-appear")
        val frame = { drawView(hud) }

        hud.alpha = 0f
        hud.scaleX = 0.92f
        hud.scaleY = 0.92f
        hud.pivotX = hud.width / 2f
        hud.pivotY = hud.height / 2f
        sequence.preRoll(frame)
        hud.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(Tuning.HUD_ENTRANCE_MS.toLong())
            .setInterpolator(HudView.ENTRANCE)
            .start()
        sequence.animationFor(Tuning.HUD_ENTRANCE_MS.toLong(), frame)
        sequence.settle(frame)
    }

    private fun captureHudDismiss(dir: File) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val hud = hud(activity)
        activity.setContentView(hud)
        shadowOf(Looper.getMainLooper()).idle()
        layoutHud(hud, hudWidthPx(activity))
        val sequence = Sequence(dir, "hud-dismiss")
        val frame = { drawView(hud) }

        sequence.preRoll(frame)
        hud.animate()
            .alpha(0f)
            .setDuration(Tuning.HUD_EXIT_MS.toLong())
            .withEndAction { }
            .start()
        sequence.animationFor(Tuning.HUD_EXIT_MS.toLong(), frame)
        sequence.settle(frame)
    }

    private fun capturePillDock(dir: File) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val geometry = ScreenGeometry(1080, 1920, 0, 0)
        val params = WindowManager.LayoutParams()
        val pill = FloatingControlPillView(activity)
        pill.bind(params, NoopPillHost)
        pill.render("日", PillPhase.MANUAL, "Translate", 1f)
        layoutPill(pill)
        params.x = 700
        params.y = 800
        val sequence = Sequence(dir, "pill-dock")
        val frame = { drawOnScreen(pill, params, geometry.width, geometry.height) }
        sequence.preRoll(frame)

        val cx = params.x + pill.width / 2f
        val cy = params.y + pill.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
        pill.dispatchTouchEvent(down)
        val move = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, cx + 120f, cy, 0)
        pill.dispatchTouchEvent(move)
        val up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cx + 120f, cy, 0)
        pill.dispatchTouchEvent(up)
        down.recycle()
        move.recycle()
        up.recycle()

        var lastX = params.x
        var stable = 0
        var frames = 0
        while (frames < DOCK_MAX_FRAMES) {
            sequence.step()
            sequence.shoot(frame())
            frames += 1
            val x = params.x
            stable = if (x == lastX) stable + 1 else 0
            lastX = x
            if (stable >= 2) break
        }
        sequence.settle(frame)
    }

    private fun hud(activity: Activity): HudView {
        val view = HudView(activity)
        view.bind(NoopHudHost)
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

    private fun hudWidthPx(activity: Activity): Int =
        (Tuning.HUD_WIDTH_DP * activity.resources.displayMetrics.density).roundToInt()

    private fun layoutPill(pill: View) {
        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        pill.layout(0, 0, pill.measuredWidth, pill.measuredHeight)
    }

    private fun layoutHud(hud: View, widthPx: Int) {
        hud.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        hud.layout(0, 0, widthPx, hud.measuredHeight)
    }

    /** Draws the view at its current animated alpha, flattened onto the paper background. */
    private fun drawView(view: View): Bitmap {
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(InkPaper.PAPER)
        val alpha = (view.alpha * 255f).roundToInt().coerceIn(0, 255)
        val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alpha)
        view.draw(canvas)
        canvas.restoreToCount(layer)
        return bitmap
    }

    /** Draws the pill at its window position so the dock spring reads as motion. */
    private fun drawOnScreen(
        view: View,
        params: WindowManager.LayoutParams,
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(InkPaper.PAPER)
        val save = canvas.save()
        canvas.translate(params.x.toFloat(), params.y.toFloat())
        view.draw(canvas)
        canvas.restoreToCount(save)
        return bitmap
    }

    private fun requireProperty(key: String): String =
        System.getProperty(key) ?: error("Missing system property $key")

    private class Sequence(dir: File, private val name: String) {
        private val dir = dir
        private var index = 0

        fun step() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS.toLong()))
        }

        fun shoot(bitmap: Bitmap) {
            val file = File(dir, "$name-${index.toString().padStart(2, '0')}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            index += 1
        }

        fun preRoll(frame: () -> Bitmap) {
            animation(PRE_ROLL_FRAMES, frame)
        }

        fun settle(frame: () -> Bitmap) {
            animation(SETTLED_FRAMES, frame)
        }

        fun animation(frames: Int, frame: () -> Bitmap) {
            repeat(frames) {
                step()
                shoot(frame())
            }
        }

        fun animationFor(durationMs: Long, frame: () -> Bitmap) {
            animation(((durationMs + FRAME_MS - 1) / FRAME_MS).toInt(), frame)
        }
    }

    private companion object {
        const val FRAME_MS = 16
        const val PRE_ROLL_FRAMES = 2
        const val SETTLED_FRAMES = 2
        const val PRESS_DOWN_FRAMES = 6
        const val PRESS_UP_FRAMES = 11
        const val DOCK_MAX_FRAMES = 40
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
