package app.walsoup.serifu.ml

import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.TranslationCache
import app.walsoup.serifu.settings.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAiTranslationEngineTest {

    @Test
    fun unconfiguredEngineReportsUnsupported() = runBlocking {
        val settings = Settings(aiApiKey = "", aiEndpoint = "")
        val engine = RemoteAiTranslationEngine(settingsProvider = { settings })

        assertFalse(engine.isConfigured())
        val avail = engine.availability(SourceLanguage.JA, "en")
        assertTrue(avail is EngineAvailability.Unsupported)
    }

    @Test
    fun configuredEngineWithEndpointOnlyReportsReady() = runBlocking {
        val settings = Settings(aiApiKey = "", aiEndpoint = "http://localhost:11434")
        val engine = RemoteAiTranslationEngine(settingsProvider = { settings })

        assertTrue(engine.isConfigured())
        val avail = engine.availability(SourceLanguage.JA, "en")
        assertTrue(avail is EngineAvailability.Ready)
    }

    @Test
    fun configuredEngineReportsReady() = runBlocking {
        val settings = Settings(aiApiKey = "valid-key-xyz")
        val engine = RemoteAiTranslationEngine(settingsProvider = { settings })

        assertTrue(engine.isConfigured())
        val avail = engine.availability(SourceLanguage.FR, "en")
        assertTrue(avail is EngineAvailability.Ready)
        assertEquals(EngineId.AI_REMOTE, engine.id)
    }

    @Test
    fun cachedEntriesAreReturnedWithoutNetworkCalls() = runBlocking {
        val cache = TranslationCache()
        val settings = Settings(aiApiKey = "dummy-key")
        val engine = RemoteAiTranslationEngine(settingsProvider = { settings }, cache = cache)

        // Seed cache (keys carry the engine's non-secret config tag)
        cache.put(
            cache.key(EngineId.AI_REMOTE, "en", "${engine.cacheConfigTag(settings)}|fr|Bonjour!"),
            "Hello!",
        )

        val request = PageTranslationRequest(
            source = SourceLanguage.FR,
            target = "en",
            bubbles = listOf(
                app.serifu.core.translate.BubbleText(
                    id = 1,
                    kind = BubbleKind.DIALOGUE,
                    text = "Bonjour!",
                    charBudget = 20,
                ),
            ),
            previousPageLines = emptyList(),
            keepHonorifics = false,
        )

        val result = engine.translatePage(request)
        assertEquals(EngineId.AI_REMOTE, result.engine)
        assertEquals("Hello!", result.byId[1])
    }
}
