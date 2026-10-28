package app.serifu.core.change

import app.serifu.core.image.LumaImage
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageCacheTest {
    private fun fingerprint(fill: Int): FrameFingerprint {
        val tiny = LumaImage(90, 200, ByteArray(90 * 200) { fill.toByte() })
        return FrameFingerprint.of(tiny, 1440, 3200, 80, 100)
    }

    private val fpA = fingerprint(200)
    private val fpB = fingerprint(40)

    @Test
    fun hitReturnsMostRecentValue() {
        val cache = PageCache<String>()
        cache.put(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-a")
        assertEquals("page-a", cache.get(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT))
    }

    @Test
    fun languageMismatchIsAMiss() {
        val cache = PageCache<String>()
        cache.put(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-a")
        assertNull(cache.get(fpA, 0, SourceLanguage.JA, "fr", EngineId.MLKIT))
        assertNull(cache.get(fpA, 0, SourceLanguage.KO, "en", EngineId.MLKIT))
    }

    @Test
    fun engineMismatchIsAMiss() {
        val cache = PageCache<String>()
        cache.put(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-a")
        assertNull(cache.get(fpA, 0, SourceLanguage.JA, "en", EngineId.GEMMA4_E2B))
    }

    @Test
    fun rotationMismatchIsAMiss() {
        val cache = PageCache<String>()
        cache.put(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-a")
        assertNull(cache.get(fpA, 1, SourceLanguage.JA, "en", EngineId.MLKIT))
    }

    @Test
    fun lruEvictsOldest() {
        val cache = PageCache<String>(maxSize = 3)
        val fps = listOf(fingerprint(0), fingerprint(80), fingerprint(160), fingerprint(240))
        fps.forEachIndexed { i, fp -> cache.put(fp, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "p$i") }
        assertEquals(3, cache.size())
        assertNull(cache.get(fps[0], 0, SourceLanguage.JA, "en", EngineId.MLKIT))
        assertEquals("p3", cache.get(fps[3], 0, SourceLanguage.JA, "en", EngineId.MLKIT))
    }

    @Test
    fun clearDropsEverything() {
        val cache = PageCache<String>()
        cache.put(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-a")
        cache.put(fpB, 0, SourceLanguage.JA, "en", EngineId.MLKIT, "page-b")
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get(fpA, 0, SourceLanguage.JA, "en", EngineId.MLKIT))
    }
}
