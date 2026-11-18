package app.walsoup.serifu.ui.onboarding

import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Leave/return state decisions for onboarding, driven by controlled
 * accessibility-service states instead of a real system round trip.
 * Covers: grant, denial, unchanged state, and unrelated-service changes.
 * The real system UI portion (the actual Settings screens) stays device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnboardingServiceStateTest {

    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetServiceState() {
        app.walsoup.serifu.service.SerifuAccessibilityService.isRunning = false
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            null,
        )
    }

    @Test
    fun grantViaSecureSettingsEnablesService() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "app.walsoup.serifu/app.walsoup.serifu.service.SerifuAccessibilityService",
        )
        assertTrue(isServiceEnabled(context))
    }

    @Test
    fun denialLeavesServiceDisabled() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "",
        )
        assertFalse(isServiceEnabled(context))
    }

    @Test
    fun unchangedUnsetStateLeavesServiceDisabled() {
        // Setting untouched (null): a resume alone must not flip the state.
        assertFalse(isServiceEnabled(context))
    }

    @Test
    fun unrelatedServiceChangeDoesNotEnableOurs() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example.other/.OtherAccessibilityService",
        )
        assertFalse(isServiceEnabled(context))
    }

    @Test
    fun grantAmongOtherServicesEnablesOurs() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example.other/.OtherAccessibilityService" +
                ":app.walsoup.serifu/.service.SerifuAccessibilityService",
        )
        assertTrue(isServiceEnabled(context))
    }

    @Test
    fun runningServiceFlagCountsAsEnabled() {
        app.walsoup.serifu.service.SerifuAccessibilityService.isRunning = true
        try {
            assertTrue(isServiceEnabled(context))
        } finally {
            app.walsoup.serifu.service.SerifuAccessibilityService.isRunning = false
        }
    }
}
