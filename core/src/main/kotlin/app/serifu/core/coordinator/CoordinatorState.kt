package app.serifu.core.coordinator

import app.serifu.core.Tuning
import app.serifu.core.model.TriggerMode

enum class ErrorKind {
    CAPTURE_FAILED,
    PERMISSION_LOST,
    SECURE_WINDOW,
}

sealed interface Phase {
    data object Off : Phase
    data object Idle : Phase
    data object Interacting : Phase
    data class Settling(val deadline: Long) : Phase
    data object Capturing : Phase
    data class Processing(val frameId: Long) : Phase
    data class Showing(val pageId: Long) : Phase
    data class Error(val kind: ErrorKind) : Phase
}

data class CoordinatorState(
    val phase: Phase = Phase.Off,
    val generation: Long = 0,
    val captureRetries: Int = 0,
    val emptyRetries: Int = 0,
    val emptySinceMs: Long = 0,
)

data class ReducerConfig(
    val mode: TriggerMode,
    val paused: Boolean = false,
    val obscured: Boolean = false,
    val readerForeground: Boolean = true,
    val screenOn: Boolean = true,
    val hudVisible: Boolean = false,
    val settleDelayMs: Long = Tuning.SETTLE_DELAY_DEFAULT_MS.toLong(),
    val minIntervalMs: Long = Tuning.MIN_SCREENSHOT_INTERVAL_API_33_MS,
    val lastScreenshotUptimeMs: Long = 0,
) {
    fun canAuto(ignoreHud: Boolean = false): Boolean =
        mode == TriggerMode.AUTO &&
            !paused &&
            !obscured &&
            readerForeground &&
            screenOn &&
            (ignoreHud || !hudVisible)
}
