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

    @Test
    fun configurationChangesProduceDifferentCacheConfigTags() {
        val base = Settings(aiApiKey = "dummy-key", aiModel = "gpt-4o-mini", aiEndpoint = "https://api.openai.com/v1")
        val engine = RemoteAiTranslationEngine(settingsProvider = { base })

        val tagBase = engine.cacheConfigTag(base)
        val tagDiffModel = engine.cacheConfigTag(base.copy(aiModel = "gemini-1.5-flash"))
        val tagDiffEndpoint = engine.cacheConfigTag(base.copy(aiEndpoint = "https://custom.endpoint.com/v1"))
        val tagDiffEffort = engine.cacheConfigTag(base.copy(reasoningEffort = app.serifu.core.model.ReasoningEffort.HIGH))

        org.junit.Assert.assertNotEquals(tagBase, tagDiffModel)
        org.junit.Assert.assertNotEquals(tagBase, tagDiffEndpoint)
        org.junit.Assert.assertNotEquals(tagBase, tagDiffEffort)
    }

    @Test
    fun geminiKeyNeverAppearsInTheUrl() {
        val base = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        assertTrue(isGeminiEndpoint(base))
        // Auth travels in the x-goog-api-key header; the URL is returned untouched
        // so keys never land in server/proxy/CDN access logs.
        assertEquals(base, buildRemoteRequestUrl(base, "abc"))
        assertEquals("$base?alt=json", buildRemoteRequestUrl("$base?alt=json", "a&b+c"))
        assertEquals("$base#frag", buildRemoteRequestUrl("$base#frag", "abc"))
        assertEquals(base, buildRemoteRequestUrl(base, "  "))
    }

    @Test
    fun errorBodiesPassThroughInDebugBuilds() {
        // Release truncates to 200 chars; debug keeps the full body for diagnosis.
        assertEquals("status detail", redactedErrorBody("status detail"))
        assertEquals("", redactedErrorBody(""))
    }

    @Test
    fun openAiCompatibleUrlsKeepTheirPathRules() {
        assertFalse(isGeminiEndpoint("https://api.openai.com/v1"))
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            buildRemoteRequestUrl("", "sk-test"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            buildRemoteRequestUrl("https://api.openai.com/v1", "sk-test"),
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            buildRemoteRequestUrl("http://localhost:11434/v1/", " "),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            buildRemoteRequestUrl("https://example.com/v1/chat/completions", "sk-test"),
        )
    }
}
