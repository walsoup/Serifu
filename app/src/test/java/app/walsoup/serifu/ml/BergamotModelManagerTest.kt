package app.walsoup.serifu.ml

import app.serifu.core.Tuning
import app.serifu.core.model.ModelVariant
import app.serifu.core.translate.ModelRoute
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
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
        val manager = BergamotModelManager(context) {
            fetches += 1
            throw UnknownHostException("offline")
        }
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
        val manager = BergamotModelManager(context) {
            fetches += 1
            throw UnknownHostException("offline")
        }
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
        val manager = BergamotModelManager(context) { error("network on translate path") }
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
        val manager = BergamotModelManager(context) {
            fetches += 1
            CATALOG
        }
        val first = manager.catalog()
        assertNotNull(first.pair("ja", "en"))
        manager.catalog()
        assertEquals(1, fetches)
        assertTrue(File(context.filesDir, "bergamot/catalog.json").isFile)

        val restarted = BergamotModelManager(context) { error("should read disk") }
        val cached = restarted.catalog(allowFetch = false)
        assertNotNull(cached.pair("ja", "en"))
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
