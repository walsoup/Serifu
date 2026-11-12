package app.serifu.core.translate

import app.serifu.core.model.ModelVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BergamotCatalogTest {
    @Test
    fun parseGroupsByPairAndPicksLatestVersion() {
        val catalog = BergamotCatalog.parse(SAMPLE)
        val jaEn = catalog.pair("ja", "en")
        assertNotNull(jaEn)
        assertTrue("base" in jaEn.architectures)
        assertTrue("base-memory" in jaEn.architectures)
        assertEquals("3.1", jaEn.variants["base-memory"]!!.version)
        assertEquals(3, jaEn.variants["base-memory"]!!.files.size)
    }

    @Test
    fun litePrefersTinyThenFallsBack() {
        val catalog = BergamotCatalog.parse(SAMPLE)
        val jaEn = catalog.pair("ja", "en")!!
        // tiny absent for ja-en: lite falls back to base.
        assertEquals("base", BergamotCatalog.resolveArchitecture(ModelVariant.LITE, jaEn.architectures))
        assertEquals("tiny", BergamotCatalog.resolveArchitecture(ModelVariant.LITE, setOf("tiny", "base")))
        assertEquals(
            "base-memory",
            BergamotCatalog.resolveArchitecture(ModelVariant.FULL, jaEn.architectures),
        )
        assertNull(
            BergamotCatalog.resolveArchitecture(ModelVariant.FULL, setOf("tiny"), allowFallback = false),
        )
        assertEquals(
            "tiny",
            BergamotCatalog.resolveArchitecture(ModelVariant.FULL, setOf("tiny"), allowFallback = true),
        )
        assertTrue(BergamotCatalog.hasPreferredArchitecture(ModelVariant.FULL, jaEn.architectures))
        assertTrue(!BergamotCatalog.hasPreferredArchitecture(ModelVariant.LITE, jaEn.architectures))
    }

    @Test
    fun chineseMapsToZhHans() {
        assertEquals("zh-Hans", BergamotCatalog.catalogLanguage("zh"))
        val catalog = BergamotCatalog.parse(SAMPLE)
        val route = BergamotCatalog.resolveRoute(catalog, "zh", "en", ModelVariant.FULL)
        assertIs<ModelRoute.Direct>(route)
        assertEquals("zh-Hans", route.model.from)
    }

    @Test
    fun pivotWhenNoDirectPair() {
        val catalog = BergamotCatalog.parse(SAMPLE)
        val route = BergamotCatalog.resolveRoute(catalog, "ja", "fr", ModelVariant.FULL)
        assertIs<ModelRoute.Pivot>(route)
        assertEquals("ja", route.first.from)
        assertEquals("en", route.first.to)
        assertEquals("en", route.second.from)
        assertEquals("fr", route.second.to)
        assertTrue(route.totalBytes > route.first.totalBytes)
    }

    @Test
    fun splitVocabPairsAreAccepted() {
        val catalog = BergamotCatalog.parse(SAMPLE)
        val enJa = catalog.pair("en", "ja")
        assertNotNull(enJa)
        val files = enJa.variants["base-memory"]!!
        assertEquals(4, files.files.size)
        assertTrue(files.files.any { it.fileType == "srcvocab" })
        assertTrue(files.files.any { it.fileType == "trgvocab" })
    }

    @Test
    fun versionOrderPrefersNewerMinor() {
        assertTrue(BergamotCatalog.compareVersion("3.1", "3.0") > 0)
        assertTrue(BergamotCatalog.compareVersion("3.0", "2.9") > 0)
        assertTrue(BergamotCatalog.compareVersion("1.0.0", "1.0.0-beta1") > 0)
        assertTrue(BergamotCatalog.compareVersion("1.0.0-beta2", "1.0.0-beta1") > 0)
        assertTrue(BergamotCatalog.compareVersion("1.10", "1.9") > 0)
    }

    private companion object {
        val SAMPLE = """
        {
          "data": [
            {
              "name": "model.jaen.intgemm.alphas.bin",
              "version": "3.0",
              "fileType": "model",
              "architecture": "base",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "aaa",
              "decompressedSize": 40000000,
              "attachment": {
                "hash": "a1",
                "size": 20000000,
                "filename": "model.jaen.intgemm.alphas.bin.zst",
                "location": "main/jaen-model.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "lex.50.50.jaen.s2t.bin",
              "version": "3.0",
              "fileType": "lex",
              "architecture": "base",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "bbb",
              "decompressedSize": 2000000,
              "attachment": {
                "hash": "b1",
                "size": 900000,
                "filename": "lex.jaen.bin.zst",
                "location": "main/jaen-lex.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "vocab.jaen.spm",
              "version": "3.0",
              "fileType": "vocab",
              "architecture": "base",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "ccc",
              "decompressedSize": 900000,
              "attachment": {
                "hash": "c1",
                "size": 300000,
                "filename": "vocab.jaen.spm.zst",
                "location": "main/jaen-vocab.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "model.jaen.intgemm.alphas.bin",
              "version": "3.1",
              "fileType": "model",
              "architecture": "base-memory",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "ddd",
              "decompressedSize": 30000000,
              "attachment": {
                "hash": "d1",
                "size": 18000000,
                "filename": "model.jaen.zst",
                "location": "main/jaen-model-bm.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "lex.50.50.jaen.s2t.bin",
              "version": "3.1",
              "fileType": "lex",
              "architecture": "base-memory",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "eee",
              "decompressedSize": 2000000,
              "attachment": {
                "hash": "e1",
                "size": 900000,
                "filename": "lex.jaen.zst",
                "location": "main/jaen-lex-bm.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "vocab.jaen.spm",
              "version": "3.1",
              "fileType": "vocab",
              "architecture": "base-memory",
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "decompressedHash": "fff",
              "decompressedSize": 900000,
              "attachment": {
                "hash": "f1",
                "size": 300000,
                "filename": "vocab.jaen.zst",
                "location": "main/jaen-vocab-bm.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "model.zhHans-en.bin",
              "version": "3.0",
              "fileType": "model",
              "architecture": "base-memory",
              "sourceLanguage": "zh-Hans",
              "targetLanguage": "en",
              "decompressedHash": "g1",
              "decompressedSize": 30000000,
              "attachment": {
                "hash": "g2",
                "size": 18000000,
                "filename": "model.zst",
                "location": "main/zh-model.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "lex.zhHans-en.bin",
              "version": "3.0",
              "fileType": "lex",
              "architecture": "base-memory",
              "sourceLanguage": "zh-Hans",
              "targetLanguage": "en",
              "decompressedHash": "h1",
              "decompressedSize": 2000000,
              "attachment": {
                "hash": "h2",
                "size": 900000,
                "filename": "lex.zst",
                "location": "main/zh-lex.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "vocab.zhHans-en.spm",
              "version": "3.0",
              "fileType": "vocab",
              "architecture": "base-memory",
              "sourceLanguage": "zh-Hans",
              "targetLanguage": "en",
              "decompressedHash": "i1",
              "decompressedSize": 900000,
              "attachment": {
                "hash": "i2",
                "size": 300000,
                "filename": "vocab.zst",
                "location": "main/zh-vocab.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "model.enfr.bin",
              "version": "3.0",
              "fileType": "model",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "fr",
              "decompressedHash": "j1",
              "decompressedSize": 30000000,
              "attachment": {
                "hash": "j2",
                "size": 18000000,
                "filename": "model.zst",
                "location": "main/enfr-model.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "lex.enfr.bin",
              "version": "3.0",
              "fileType": "lex",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "fr",
              "decompressedHash": "k1",
              "decompressedSize": 2000000,
              "attachment": {
                "hash": "k2",
                "size": 900000,
                "filename": "lex.zst",
                "location": "main/enfr-lex.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "vocab.enfr.spm",
              "version": "3.0",
              "fileType": "vocab",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "fr",
              "decompressedHash": "l1",
              "decompressedSize": 900000,
              "attachment": {
                "hash": "l2",
                "size": 300000,
                "filename": "vocab.zst",
                "location": "main/enfr-vocab.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "model.enja.bin",
              "version": "3.0",
              "fileType": "model",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "ja",
              "decompressedHash": "m1",
              "decompressedSize": 30000000,
              "attachment": {
                "hash": "m2",
                "size": 18000000,
                "filename": "model.zst",
                "location": "main/enja-model.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "lex.enja.bin",
              "version": "3.0",
              "fileType": "lex",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "ja",
              "decompressedHash": "n1",
              "decompressedSize": 2000000,
              "attachment": {
                "hash": "n2",
                "size": 900000,
                "filename": "lex.zst",
                "location": "main/enja-lex.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "srcvocab.enja.spm",
              "version": "3.0",
              "fileType": "srcvocab",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "ja",
              "decompressedHash": "o1",
              "decompressedSize": 500000,
              "attachment": {
                "hash": "o2",
                "size": 200000,
                "filename": "srcvocab.zst",
                "location": "main/enja-src.zst",
                "mimetype": "application/zstd"
              }
            },
            {
              "name": "trgvocab.enja.spm",
              "version": "3.0",
              "fileType": "trgvocab",
              "architecture": "base-memory",
              "sourceLanguage": "en",
              "targetLanguage": "ja",
              "decompressedHash": "p1",
              "decompressedSize": 500000,
              "attachment": {
                "hash": "p2",
                "size": 200000,
                "filename": "trgvocab.zst",
                "location": "main/enja-trg.zst",
                "mimetype": "application/zstd"
              }
            }
          ]
        }
        """.trimIndent()
    }
}
