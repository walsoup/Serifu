package app.serifu.core.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureRetryTest {
    @Test
    fun intervalRetriesOnceAtThePlatformCooldown() {
        assertEquals(1050L, CaptureRetry.retryDelayMs(ShotFailure.INTERVAL_SHORT, retriesUsed = 0, sdkInt = 31))
        assertEquals(350L, CaptureRetry.retryDelayMs(ShotFailure.INTERVAL_SHORT, retriesUsed = 0, sdkInt = 34))
        assertNull(CaptureRetry.retryDelayMs(ShotFailure.INTERVAL_SHORT, retriesUsed = 1, sdkInt = 34))
    }

    @Test
    fun internalRetriesOnceThenGivesUp() {
        assertEquals(500L, CaptureRetry.retryDelayMs(ShotFailure.INTERNAL, retriesUsed = 0, sdkInt = 34))
        assertNull(CaptureRetry.retryDelayMs(ShotFailure.INTERNAL, retriesUsed = 1, sdkInt = 34))
    }

    @Test
    fun permissionAndSecureDoNotRetry() {
        assertNull(CaptureRetry.retryDelayMs(ShotFailure.NO_ACCESS, retriesUsed = 0, sdkInt = 34))
        assertNull(CaptureRetry.retryDelayMs(ShotFailure.SECURE_WINDOW, retriesUsed = 0, sdkInt = 34))
        assertNull(CaptureRetry.retryDelayMs(ShotFailure.OTHER, retriesUsed = 0, sdkInt = 30))
    }
}
