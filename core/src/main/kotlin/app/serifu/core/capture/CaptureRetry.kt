package app.serifu.core.capture

import app.serifu.core.Tuning

enum class ShotFailure {
    INTERVAL_SHORT,
    INTERNAL,
    NO_ACCESS,
    SECURE_WINDOW,
    OTHER,
}

object CaptureRetry {
    /**
     * Delay before another attempt in this generation, or null when the failure is terminal.
     * [retriesUsed] counts attempts already spent after the first failure.
     */
    fun retryDelayMs(failure: ShotFailure, retriesUsed: Int, sdkInt: Int): Long? {
        if (retriesUsed >= Tuning.CAPTURE_MAX_RETRIES) return null
        return when (failure) {
            ShotFailure.INTERVAL_SHORT -> Tuning.minScreenshotIntervalMs(sdkInt)
            ShotFailure.INTERNAL -> Tuning.CAPTURE_INTERNAL_ERROR_RETRY_MS
            ShotFailure.NO_ACCESS, ShotFailure.SECURE_WINDOW, ShotFailure.OTHER -> null
        }
    }
}
