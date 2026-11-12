package app.walsoup.serifu.ml

import app.serifu.core.model.BubbleKind
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.BubbleText
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OnDeviceTranslationEngineTest {
    @Before
    @After
    fun cleanModels() {
        File(RuntimeEnvironment.getApplication().filesDir, "bergamot").deleteRecursively()
    }

    @Test
    fun prefersMlKitWhenBergamotNotSelected() {
        val context = RuntimeEnvironment.getApplication()
        val models = BergamotModelManager(context)
        val mlKit = MlKitTranslationEngine(wifiOnly = { false })
        val bergamot = BergamotTranslationEngine(models = models)
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.MLKIT },
        )
        assertEquals(EngineId.MLKIT, router.resolveEngineId(SourceLanguage.JA, "en"))
        assertFalse(router.canUseBergamot(SourceLanguage.JA, "en"))
        router.close()
    }

    @Test
    fun fallsBackWhenBergamotSelectedButModelMissing() {
        val context = RuntimeEnvironment.getApplication()
        val models = BergamotModelManager(context)
        val mlKit = MlKitTranslationEngine(wifiOnly = { false })
        val bergamot = BergamotTranslationEngine(models = models)
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
        )
        assertEquals(EngineId.MLKIT, router.resolveEngineId(SourceLanguage.JA, "en"))
        router.close()
    }

    @Test
    fun fallsBackToMlKitWhenBergamotThrows() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        installJaEn(context)
        val models = BergamotModelManager(context)
        val mlKit = RecordingEngine(EngineId.MLKIT)
        val bergamot = ScriptedBergamot(models) {
            throw IllegalStateException("bergamot failed")
        }
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
            nativeAvailable = { true },
        )
        try {
            assertEquals(EngineId.BERGAMOT, router.resolveEngineId(SourceLanguage.JA, "en"))
            val result = router.translatePage(page())
            assertEquals(EngineId.MLKIT, result.engine)
            assertEquals("translated", result.byId[1])
            assertEquals(1, mlKit.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun bergamotCancellationDoesNotFallBack() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        installJaEn(context)
        val models = BergamotModelManager(context)
        val mlKit = RecordingEngine(EngineId.MLKIT)
        val bergamot = ScriptedBergamot(models) {
            throw CancellationException("stopped")
        }
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
            nativeAvailable = { true },
        )
        try {
            router.translatePage(page())
            fail("expected cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals(0, mlKit.calls)
            assertEquals("stopped", cancelled.message)
        } finally {
            router.close()
        }
    }

    @Test
    fun bergamotTimeoutFallsBackToMlKit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        installJaEn(context)
        val models = BergamotModelManager(context)
        val mlKit = RecordingEngine(EngineId.MLKIT)
        val bergamot = ScriptedBergamot(models) {
            kotlinx.coroutines.withTimeout(1) {
                kotlinx.coroutines.delay(50)
            }
            error("timeout should have thrown")
        }
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
            nativeAvailable = { true },
        )
        try {
            val result = router.translatePage(page())
            assertEquals(EngineId.MLKIT, result.engine)
            assertEquals(1, mlKit.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun partialFolderIsNotInstalled() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "bergamot/ja-en/tiny")
        dir.mkdirs()
        File(dir, "model.jaen.bin").writeText("m")
        val models = BergamotModelManager(context)
        val mlKit = RecordingEngine(EngineId.MLKIT)
        val bergamot = BergamotTranslationEngine(models)
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
            nativeAvailable = { true },
        )
        assertFalse(router.canUseBergamot(SourceLanguage.JA, "en"))
        assertEquals(EngineId.MLKIT, router.resolveEngineId(SourceLanguage.JA, "en"))
        router.close()
    }

    @Test
    fun availabilityFallsBackToMlKitWhenBergamotNeedsDownload() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val models = BergamotModelManager(context)
        val mlKit = RecordingEngine(EngineId.MLKIT)
        val bergamot = BergamotTranslationEngine(models)
        val router = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = models,
            preferred = { OnDeviceEngine.BERGAMOT },
            nativeAvailable = { true },
        )
        // Bergamot has no models downloaded, so availability should fall back to ML Kit (Ready in RecordingEngine)
        val avail = router.availability(SourceLanguage.JA, "en")
        assertEquals(EngineAvailability.Ready, avail)
        router.close()
    }

    private fun installJaEn(context: android.content.Context) {
        val dir = File(context.filesDir, "bergamot/ja-en/tiny")
        dir.mkdirs()
        File(dir, "model.jaen.bin").writeText("m")
        File(dir, "lex.jaen.bin").writeText("l")
        File(dir, "vocab.jaen.spm").writeText("v")
        File(dir, "version.txt").writeText("1.0")
    }

    private fun page() = PageTranslationRequest(
        source = SourceLanguage.JA,
        target = "en",
        bubbles = listOf(BubbleText(1, BubbleKind.DIALOGUE, "こんにちは", 20)),
        previousPageLines = emptyList(),
        keepHonorifics = true,
    )

    private class RecordingEngine(override val id: EngineId) : TranslationEngine {
        var calls = 0
        override suspend fun availability(source: SourceLanguage, target: String): EngineAvailability =
            EngineAvailability.Ready

        override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult {
            calls += 1
            return PageTranslationResult(id, mapOf(request.bubbles.first().id to "translated"))
        }

        override fun close() = Unit
    }

    private class ScriptedBergamot(
        models: BergamotModelManager,
        private val translate: suspend (PageTranslationRequest) -> PageTranslationResult,
    ) : BergamotTranslationEngine(models) {
        override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult = translate(request)
    }
}
