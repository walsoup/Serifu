package app.walsoup.serifu.coordinator

import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.TranslationEngine
import app.walsoup.serifu.capture.FrameConverter
import app.walsoup.serifu.capture.ScreenCapturer
import app.walsoup.serifu.ml.OcrEngine
import app.walsoup.serifu.overlay.LetteringRenderer
import app.walsoup.serifu.overlay.OverlayManager
import app.walsoup.serifu.service.EventRouter
import app.walsoup.serifu.service.SerifuAccessibilityService
import app.walsoup.serifu.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30])
class TranslationCoordinatorTest {

    private fun dummyTranslator() = object : TranslationEngine {
        override val id: EngineId = EngineId.MLKIT
        override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult =
            PageTranslationResult(EngineId.MLKIT, emptyMap())
        override suspend fun availability(source: SourceLanguage, target: String): EngineAvailability =
            EngineAvailability.Ready
        override fun close() {}
    }

    @Test
    fun hideForGestureUpdatesTimingsSafely() = runBlocking {
        val service = Robolectric.buildService(SerifuAccessibilityService::class.java).create().get()
        val overlays = OverlayManager(service)
        val capturer = ScreenCapturer(service, overlays, FrameConverter())
        val ocr = OcrEngine()
        val renderer = LetteringRenderer(service)
        val pipeline = PagePipeline(service, dummyTranslator(), renderer, ocr)

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main)

        var publishedTimingsCount = 0
        val coordinator = TranslationCoordinator(
            scope = scope,
            context = service,
            overlays = overlays,
            capturer = capturer,
            ocr = ocr,
            pipeline = pipeline,
            presence = { EventRouter.State(reader = true) },
            writeSettings = {},
            clearObscuredFlag = {},
            openApp = {},
            notifyPermissionLost = {},
            publishTimings = { publishedTimingsCount++ },
        )

        coordinator.start()
        coordinator.pushSettings(Settings(mode = TriggerMode.AUTO))

        coordinator.hideForGesture()
        assertTrue(publishedTimingsCount >= 1)
        assertTrue(coordinator.timings.hideSamples >= 1)

        scope.cancel()
    }

    @Test
    fun screenOffStopsProcessingAndCancelsGeneration() = runBlocking {
        val service = Robolectric.buildService(SerifuAccessibilityService::class.java).create().get()
        val overlays = OverlayManager(service)
        val capturer = ScreenCapturer(service, overlays, FrameConverter())
        val ocr = OcrEngine()
        val renderer = LetteringRenderer(service)
        val pipeline = PagePipeline(service, dummyTranslator(), renderer, ocr)

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main)

        val coordinator = TranslationCoordinator(
            scope = scope,
            context = service,
            overlays = overlays,
            capturer = capturer,
            ocr = ocr,
            pipeline = pipeline,
            presence = { EventRouter.State(reader = true) },
            writeSettings = {},
            clearObscuredFlag = {},
            openApp = {},
            notifyPermissionLost = {},
            publishTimings = {},
        )

        coordinator.start()
        coordinator.pushSettings(Settings(mode = TriggerMode.AUTO))

        assertTrue(coordinator.screenIsOn)
        coordinator.onScreenOff()
        assertFalse(coordinator.screenIsOn)

        coordinator.onScreenOn()
        assertTrue(coordinator.screenIsOn)

        scope.cancel()
    }
}
