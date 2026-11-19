package app.walsoup.serifu.service

import android.content.ComponentCallbacks2
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SerifuAccessibilityServiceTrimTest {
    @Test
    fun onTrimMemoryCriticalKeepsOcrInstanceStable() {
        val controller = Robolectric.buildService(SerifuAccessibilityService::class.java).create()
        val service = controller.get()
        val ocrBefore = service.ocrEngine

        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        val ocrAfter = service.ocrEngine
        assertSame(
            "OcrEngine instance must be retained across TRIM_MEMORY_RUNNING_CRITICAL",
            ocrBefore,
            ocrAfter,
        )
    }

    @Test
    fun imePackageIsCachedAndRefreshedOnBroadcastOnly() {
        val app = RuntimeEnvironment.getApplication()
        Settings.Secure.putString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, "com.example.ime/.InputMethodService")

        val controller = Robolectric.buildService(SerifuAccessibilityService::class.java).create()
        val service = controller.get()
        service.connectForTest()

        // First lookup resolves and caches.
        assertEquals("com.example.ime", service.imePackageForTest("reader.app"))

        // Change the provider setting without sending the broadcast: cache must NOT update.
        Settings.Secure.putString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, "com.new.ime/.NewInputMethodService")
        assertEquals("com.example.ime", service.imePackageForTest("reader.app"))

        // Broadcast ACTION_INPUT_METHOD_CHANGED: now it refreshes.
        service.sendBroadcast(Intent(Intent.ACTION_INPUT_METHOD_CHANGED))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("com.new.ime", service.imePackageForTest("reader.app"))
    }
}
