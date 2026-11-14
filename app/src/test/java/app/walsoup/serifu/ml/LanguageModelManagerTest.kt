package app.walsoup.serifu.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LanguageModelManagerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        LanguageModelManager.resetForTest()
        LanguageModelManager.probeDownloaded = { emptySet() }
        LanguageModelManager.recoveryDelayMs = 60_000
    }

    @After
    fun tearDown() {
        LanguageModelManager.resetForTest()
    }

    @Test
    fun cancellingSomeCodesLeavesTheOthersRunning() = runBlocking {
        val entered = mutableSetOf<String>()
        val release = CompletableDeferred<Unit>()
        LanguageModelManager.fetchPack = { code, _ ->
            synchronized(entered) { entered += code }
            release.await()
        }
        val job = LanguageModelManager.startDownload(this, setOf("en", "ja", "de"), wifiOnly = false, context)
        withTimeout(3_000) {
            while (synchronized(entered) { entered.size } < 3) delay(10)
        }
        LanguageModelManager.cancelDownload(setOf("en", "ja"))
        assertFalse("en" in LanguageModelManager.inProgressCodes.value)
        assertFalse("ja" in LanguageModelManager.inProgressCodes.value)
        assertTrue("de" in LanguageModelManager.inProgressCodes.value)
        release.complete(Unit)
        job.join()
        assertTrue(LanguageModelManager.inProgressCodes.value.isEmpty())
    }

    @Test
    fun cancelledFinallyDoesNotClearARestartedDownload() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        LanguageModelManager.fetchPack = { _, _ ->
            entered.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                withContext(NonCancellable) { delay(80) }
            }
        }
        val first = LanguageModelManager.startDownload(this, setOf("en"), wifiOnly = false, context)
        withTimeout(3_000) { entered.await() }
        LanguageModelManager.cancelDownload(setOf("en"))
        val hold = CompletableDeferred<Unit>()
        LanguageModelManager.fetchPack = { _, _ -> hold.await() }
        val second = LanguageModelManager.startDownload(this, setOf("en"), wifiOnly = false, context)
        delay(150)
        assertTrue("en" in LanguageModelManager.inProgressCodes.value)
        hold.complete(Unit)
        second.join()
        first.join()
        assertFalse("en" in LanguageModelManager.inProgressCodes.value)
    }

    @Test
    fun timeoutMapsToTimeoutAndRecoversWhenThePackAppears() = runBlocking {
        var available = false
        LanguageModelManager.probeDownloaded = { if (available) setOf("en") else emptySet() }
        LanguageModelManager.downloadTimeoutMs = 30
        LanguageModelManager.fetchPack = { _, _ -> delay(5_000) }
        val job = LanguageModelManager.startDownload(this, setOf("en"), wifiOnly = false, context)
        job.join()
        val error = LanguageModelManager.errors.value["en"]
        assertEquals(DownloadErrorKind.TIMEOUT, error?.kind)
        assertFalse(error?.message.orEmpty().contains("SHA"))
        assertEquals(1, LanguageModelManager.recoveriesScheduled)
        assertTrue("en" !in LanguageModelManager.downloadedCodes.value)
        available = true
        LanguageModelManager.recoverTimedOutPack("en")
        assertNull(LanguageModelManager.errors.value["en"])
        assertTrue("en" in LanguageModelManager.downloadedCodes.value)
    }

    @Test
    fun failuresDoNotExposeExceptionText() = runBlocking {
        LanguageModelManager.fetchPack = { _, _ ->
            throw IllegalStateException("SHA-256 mismatch expected abcdef got 1234")
        }
        val job = LanguageModelManager.startDownload(this, setOf("de"), wifiOnly = false, context)
        job.join()
        val error = LanguageModelManager.errors.value["de"]
        assertEquals(DownloadErrorKind.NETWORK, error?.kind)
        assertFalse(error?.message.orEmpty().contains("SHA-256"))
        assertFalse(error?.message.orEmpty().contains("abcdef"))
    }

    @Test
    fun wifiOnlyBlocksMeteredWifiAndAllowsUnmeteredEthernet() = runBlocking {
        var fetches = 0
        LanguageModelManager.fetchPack = { _, _ -> fetches += 1 }
        setActiveTransport(null, NetworkCapabilities.TRANSPORT_WIFI, unmetered = false)
        LanguageModelManager.startDownload(this, setOf("ja"), wifiOnly = true, context).join()
        assertEquals(0, fetches)
        assertEquals(DownloadErrorKind.WIFI_REQUIRED, LanguageModelManager.errors.value["ja"]?.kind)
        assertFalse("ja" in LanguageModelManager.inProgressCodes.value)

        LanguageModelManager.resetForTest()
        LanguageModelManager.probeDownloaded = { emptySet() }
        LanguageModelManager.fetchPack = { _, _ -> fetches += 1 }
        setActiveTransport(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI, unmetered = false)
        LanguageModelManager.startDownload(this, setOf("ja"), wifiOnly = true, context).join()
        assertEquals(0, fetches)
        assertEquals(DownloadErrorKind.WIFI_REQUIRED, LanguageModelManager.errors.value["ja"]?.kind)

        LanguageModelManager.resetForTest()
        LanguageModelManager.probeDownloaded = { emptySet() }
        LanguageModelManager.fetchPack = { _, _ -> fetches += 1 }
        setActiveTransport(ConnectivityManager.TYPE_ETHERNET, NetworkCapabilities.TRANSPORT_ETHERNET, unmetered = true)
        assertTrue(LanguageModelManager.isOnWifi(context))
        LanguageModelManager.startDownload(this, setOf("ja"), wifiOnly = true, context).join()
        assertEquals(1, fetches)
        assertNull(LanguageModelManager.errors.value["ja"])
    }

    private fun setActiveTransport(type: Int?, transport: Int, unmetered: Boolean) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(cm)
        if (type == null) {
            shadow.setActiveNetworkInfo(null)
            return
        }
        val info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, true)
        shadow.setActiveNetworkInfo(info)
        val network = cm.activeNetwork ?: error("missing active network")
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(transport)
        Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (unmetered) {
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        shadow.setNetworkCapabilities(network, caps)
    }
}
