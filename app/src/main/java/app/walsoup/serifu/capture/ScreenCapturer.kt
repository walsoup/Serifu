package app.walsoup.serifu.capture

import android.accessibilityservice.AccessibilityService
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SystemClock
import android.view.Display
import app.serifu.core.Tuning
import app.serifu.core.capture.ShotFailure
import app.walsoup.serifu.overlay.OverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

sealed interface CaptureOutcome {
    data class Ready(val frame: CapturedFrame) : CaptureOutcome
    data class Failed(val failure: ShotFailure) : CaptureOutcome
    data object Superseded : CaptureOutcome
}

class ScreenCapturer(
    private val service: AccessibilityService,
    private val overlays: OverlayManager,
    private val converter: FrameConverter,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val captureExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "serifu-capture")
    }

    var lastScreenshotRequestUptimeMs: Long = 0L
        private set

    val minIntervalMs: Long get() = Tuning.minScreenshotIntervalMs(sdkInt)

    fun close() {
        captureExecutor.shutdown()
    }

    suspend fun capture(notBefore: Long, stillCurrent: () -> Boolean): CaptureOutcome {
        val wait = notBefore - clock()
        if (wait > 0) delay(wait)
        if (!stillCurrent()) return CaptureOutcome.Superseded
        overlays.canvas.awaitOffScreen()
        overlays.dismissHudImmediately()
        if (!stillCurrent()) return CaptureOutcome.Superseded
        val shotStarted = clock()
        lastScreenshotRequestUptimeMs = shotStarted
        val outcome = suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val shotEnded = clock()
                    val captureMs = (shotEnded - shotStarted).coerceAtLeast(0)
                    val buffer = screenshot.hardwareBuffer
                    val convStarted = clock()
                    val frame = try {
                        converter.convert(
                            hardwareBuffer = buffer,
                            colorSpace = screenshot.colorSpace,
                            timestampUptime = screenshot.timestamp,
                            rotation = overlays.rotation(),
                            pillRect = overlays.pillRectOnScreen(),
                            systemBars = overlays.systemBars(),
                        )
                    } catch (t: Throwable) {
                        android.util.Log.e("SerifuCapture", "converter.convert failed", t)
                        null
                    } finally {
                        closeBuffer(buffer)
                    }
                    val convEnded = clock()
                    val convertMs = (convEnded - convStarted).coerceAtLeast(0)
                    if (frame != null) {
                        frame.captureMs = captureMs
                        frame.convertMs = convertMs
                    }
                    if (!cont.isActive) {
                        frame?.recycle()
                        return
                    }
                    if (frame == null) cont.resume(CaptureOutcome.Failed(ShotFailure.INTERNAL))
                    else cont.resume(CaptureOutcome.Ready(frame))
                }

                override fun onFailure(errorCode: Int) {
                    android.util.Log.e("SerifuCapture", "takeScreenshot onFailure errorCode=$errorCode")
                    if (cont.isActive) cont.resume(CaptureOutcome.Failed(failureOf(errorCode)))
                }
            }
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor, callback)
            } catch (t: Throwable) {
                android.util.Log.e("SerifuCapture", "service.takeScreenshot threw", t)
                if (cont.isActive) cont.resume(CaptureOutcome.Failed(ShotFailure.INTERNAL))
            }
        }
        return withContext(Dispatchers.Main.immediate) { outcome }
    }

    private fun failureOf(code: Int): ShotFailure = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> ShotFailure.INTERVAL_SHORT
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> ShotFailure.INTERNAL
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> ShotFailure.NO_ACCESS
        SECURE_WINDOW -> ShotFailure.SECURE_WINDOW
        else -> ShotFailure.OTHER
    }

    private fun closeBuffer(buffer: HardwareBuffer) {
        try {
            buffer.close()
        } catch (_: Throwable) {
            // Already closed by the platform on some builds.
        }
    }

    private companion object {
        const val SECURE_WINDOW = 6
    }
}
