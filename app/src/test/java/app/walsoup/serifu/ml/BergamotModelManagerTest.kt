package app.walsoup.serifu.ml

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import app.serifu.core.Tuning
import app.serifu.core.model.ModelVariant
import app.serifu.core.translate.ModelFile
import app.serifu.core.translate.ModelFiles
import app.serifu.core.translate.ModelRoute
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BergamotModelManagerTest {
    @Before
    @After
    fun cleanModels() {
        File(RuntimeEnvironment.getApplication().filesDir, "bergamot").deleteRecursively()
    }

    @Test
    fun staleDiskCatalogIsUsedWhenRefreshFails() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "bergamot/catalog.json")
        file.parentFile?.mkdirs()
        file.writeText(CATALOG)
        val staleAt = System.currentTimeMillis() - Tuning.BERGAMOT_CATALOG_CACHE_MS - 60_000
        assertTrue(file.setLastModified(staleAt))
        assertTrue(System.currentTimeMillis() - file.lastModified() >= Tuning.BERGAMOT_CATALOG_CACHE_MS)
        var fetches = 0
        val manager = BergamotModelManager(context, fetchCatalogBody = {
            fetches += 1
            throw UnknownHostException("offline")
        })
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE)
        assertEquals(1, fetches)
        assertTrue(route is ModelRoute.Direct)
        assertEquals("ja", (route as ModelRoute.Direct).model.from)
        assertEquals("en", route.model.to)
    }

    @Test
    fun translatePathDoesNotFetchWhenDiskCatalogIsStale() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "bergamot/catalog.json")
        file.parentFile?.mkdirs()
        file.writeText(CATALOG)
        assertTrue(
            file.setLastModified(System.currentTimeMillis() - Tuning.BERGAMOT_CATALOG_CACHE_MS - 60_000),
        )
        var fetches = 0
        val manager = BergamotModelManager(context, fetchCatalogBody = {
            fetches += 1
            throw UnknownHostException("offline")
        })
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE, allowFetch = false)
        assertEquals(0, fetches)
        assertNotNull(route)
    }

    @Test
    fun installedFilesResolveWithoutCatalogOrNetwork() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "bergamot/ja-en/tiny")
        dir.mkdirs()
        File(dir, "model.jaen.bin").writeText("m")
        File(dir, "lex.jaen.bin").writeText("l")
        File(dir, "vocab.jaen.spm").writeText("v")
        val manager = BergamotModelManager(context, fetchCatalogBody = { error("network on translate path") })
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE, allowFetch = false)
        assertTrue(route is ModelRoute.Direct)
        assertTrue(manager.isRouteReady(route!!))
        assertEquals("tiny", (route as ModelRoute.Direct).model.architecture)
    }

    @Test
    fun hyphenatedLanguageTagsStayIntactWhenListingInstalls() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "bergamot/zh-Hans-en/tiny")
        dir.mkdirs()
        File(dir, "model.zhHans.bin").writeText("m")
        File(dir, "lex.zhHans.bin").writeText("l")
        File(dir, "vocab.zhHans.spm").writeText("v")
        val installed = BergamotModelManager(context).installed.value
        assertEquals(1, installed.size)
        assertEquals("zh-Hans", installed.single().from)
        assertEquals("en", installed.single().to)
    }

    @Test
    fun fetchedCatalogIsReusedAcrossProcesses() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var fetches = 0
        val manager = BergamotModelManager(context, fetchCatalogBody = {
            fetches += 1
            CATALOG
        })
        val first = manager.catalog()
        assertNotNull(first.pair("ja", "en"))
        manager.catalog()
        assertEquals(1, fetches)
        assertTrue(File(context.filesDir, "bergamot/catalog.json").isFile)

        val restarted = BergamotModelManager(context, fetchCatalogBody = { error("should read disk") })
        val cached = restarted.catalog(allowFetch = false)
        assertNotNull(cached.pair("ja", "en"))
    }

    @Test
    fun wifiOnlyRecordsWifiRequiredErrorWhenOffline() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        setActiveTransport(context, null, NetworkCapabilities.TRANSPORT_WIFI, unmetered = false)
        val manager = BergamotModelManager(context, fetchCatalogBody = { CATALOG })
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE) as ModelRoute.Direct
        var failed = false
        try {
            manager.downloadRoute(route, wifiOnly = true)
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        val key = manager.routeStorageKeys(route).single()
        val err = manager.downloadErrors.value[key]
        assertNotNull(err)
        assertEquals(DownloadErrorKind.WIFI_REQUIRED, err?.kind)
        manager.clearRouteErrors(route)
        assertTrue(manager.downloadErrors.value.isEmpty())
    }

    @Test
    fun meteredWifiIsNotAnUnmeteredDownloadNetwork() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        setActiveTransport(context, ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI, unmetered = false)
        assertFalse(NetworkStatus.isUnmetered(context))
        val manager = BergamotModelManager(context, { CATALOG }, { error("should not fetch") })
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE) as ModelRoute.Direct
        var failed = false
        try {
            manager.downloadRoute(route, wifiOnly = true)
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        val key = manager.routeStorageKeys(route).single()
        assertEquals(DownloadErrorKind.WIFI_REQUIRED, manager.downloadErrors.value[key]?.kind)
    }

    @Test
    fun unmeteredEthernetAndVpnOverWifiAllowDownloads() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        setActiveTransport(
            context,
            ConnectivityManager.TYPE_ETHERNET,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            unmetered = true,
        )
        assertTrue(NetworkStatus.isUnmetered(context))
        assertDownloadAttemptsNetwork(context)

        setVpnOverUnmeteredWifi(context)
        assertTrue(NetworkStatus.isUnmetered(context))
        assertDownloadAttemptsNetwork(context)
    }

    @Test
    fun checksumMismatchIsVerificationFailureWithoutHashText() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val manager = BergamotModelManager(
            context,
            fetchCatalogBody = { CATALOG },
            fetchFileBytes = { byteArrayOf(1, 2, 3, 4) },
        )
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE) as ModelRoute.Direct
        var failed = false
        try {
            manager.downloadRoute(route, wifiOnly = false)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        val err = manager.downloadErrors.value[manager.routeStorageKeys(route).single()]
        assertEquals(DownloadErrorKind.VERIFICATION_FAILED, err?.kind)
        assertFalse(err?.message.orEmpty().contains("a1"))
        assertFalse(err?.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun pivotProgressKeepsFinishedLeg() {
        val context = RuntimeEnvironment.getApplication()
        val manager = BergamotModelManager(context)
        val first = sampleModel("ja", "en", bytes = 10, fileCount = 2)
        val second = sampleModel("en", "fr", bytes = 30, fileCount = 2)
        val route = ModelRoute.Pivot(first, second)
        val keys = manager.routeStorageKeys(route)
        val progress = mapOf(
            keys[1] to BergamotDownloadProgress(
                key = keys[1],
                downloadedBytes = 5,
                totalBytes = 30,
                currentFileIndex = 1,
                totalFiles = 2,
                stage = DownloadStage.DOWNLOADING,
            ),
        )
        val combined = manager.routeProgress(route, progress, readyKeys = setOf(keys[0]))
        assertNotNull(combined)
        assertEquals(15L, combined?.downloadedBytes)
        assertEquals(40L, combined?.totalBytes)
        assertEquals(3, combined?.currentFileIndex)
        assertEquals(4, combined?.totalFiles)
    }

    private suspend fun assertDownloadAttemptsNetwork(context: android.content.Context) {
        var fetched = false
        val manager = BergamotModelManager(
            context,
            fetchCatalogBody = { CATALOG },
            fetchFileBytes = {
                fetched = true
                throw IOException("stopped")
            },
        )
        val route = manager.resolveRoute("ja", "en", ModelVariant.LITE) as ModelRoute.Direct
        try {
            manager.downloadRoute(route, wifiOnly = true)
        } catch (_: IOException) {
        }
        assertTrue(fetched)
        val err = manager.downloadErrors.value[manager.routeStorageKeys(route).single()]
        assertEquals(DownloadErrorKind.NETWORK, err?.kind)
    }

    private fun setActiveTransport(context: android.content.Context, type: Int?, transport: Int, unmetered: Boolean) {
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

    private fun setVpnOverUnmeteredWifi(context: android.content.Context) {
        setActiveTransport(
            context,
            ConnectivityManager.TYPE_WIFI,
            NetworkCapabilities.TRANSPORT_WIFI,
            unmetered = true,
        )
        setActiveTransport(context, ConnectivityManager.TYPE_VPN, NetworkCapabilities.TRANSPORT_VPN, unmetered = false)
    }

    private fun sampleModel(from: String, to: String, bytes: Long, fileCount: Int): ModelFiles {
        val each = bytes / fileCount
        val files = List(fileCount) { index ->
            ModelFile(
                name = "file$index",
                fileType = if (index == 0) "model" else "lex",
                location = "main/file$index",
                compressedSize = each,
                compressedHash = "",
                decompressedSize = each * 2,
                decompressedHash = "",
            )
        }
        return ModelFiles(from, to, "tiny", "1", files, files.sumOf { it.transferBytes })
    }

    private companion object {
        const val CATALOG = """
        {"data":[
          {"name":"model.jaen.bin","version":"1.0","fileType":"model","architecture":"tiny",
           "sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"aaa","decompressedSize":10,
           "attachment":{"hash":"a1","size":5,"filename":"m.zst","location":"main/m.zst","mimetype":"application/zstd"}},
          {"name":"lex.jaen.bin","version":"1.0","fileType":"lex","architecture":"tiny",
           "sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"bbb","decompressedSize":10,
           "attachment":{"hash":"b1","size":5,"filename":"l.zst","location":"main/l.zst","mimetype":"application/zstd"}},
          {"name":"vocab.jaen.spm","version":"1.0","fileType":"vocab","architecture":"tiny",
           "sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"ccc","decompressedSize":10,
           "attachment":{"hash":"c1","size":5,"filename":"v.zst","location":"main/v.zst","mimetype":"application/zstd"}}
        ]}
        """
    }
}
