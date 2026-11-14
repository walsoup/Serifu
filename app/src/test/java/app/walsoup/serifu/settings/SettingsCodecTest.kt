package app.walsoup.serifu.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.ModelVariant
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.ui.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun roundTripKeepsEveryField() {
        val original = Settings(
            mode = TriggerMode.MANUAL,
            sourceLanguage = SourceLanguage.KO,
            targetLanguage = "fr",
            quality = QualityMode.BEST_ONLY,
            settleDelayMs = 480,
            readerPackages = setOf("app.mihon"),
            letteringFont = LetteringFont.ATKINSON,
            classicCaps = false,
            minFontSp = 11,
            maxFontSp = 18,
            sfxMode = SfxMode.OFF,
            keepHonorifics = false,
            skipLatinText = false,
            pillSide = PillSide.START,
            pillYFraction = 0.2f,
            pillIdleAlpha = 0.4f,
            haptics = false,
            wifiOnlyDownloads = false,
            debugOverlay = true,
            dynamicColor = true,
            onboardingDone = true,
            aiApiKey = "test-api-key-12345",
            aiModel = "gemini-1.5-flash",
            aiEndpoint = "https://custom.endpoint.com/v1",
            reasoningEffort = ReasoningEffort.LOW,
            onDeviceEngine = OnDeviceEngine.BERGAMOT,
            bergamotVariant = ModelVariant.FULL,
        )
        val prefs = mutablePreferencesOf()
        original.writeTo(prefs)
        assertEquals(
            original.copy(settleDelayMs = 500),
            prefs.toSettings(defaultReaders = setOf("should-not-apply")),
        )
    }

    @Test
    fun bergamotEngineAndVariantRoundTrip() {
        val original = Settings(
            onDeviceEngine = OnDeviceEngine.BERGAMOT,
            bergamotVariant = ModelVariant.FULL,
        )
        val prefs = mutablePreferencesOf()
        original.writeTo(prefs)
        val decoded = prefs.toSettings(defaultReaders = emptySet())
        assertEquals(OnDeviceEngine.BERGAMOT, decoded.onDeviceEngine)
        assertEquals(ModelVariant.FULL, decoded.bergamotVariant)
    }

    @Test
    fun missingReaderKeyUsesInstalledDefault() {
        val decoded = mutablePreferencesOf().toSettings(defaultReaders = setOf("app.mihon"))
        assertEquals(setOf("app.mihon"), decoded.readerPackages)
        assertEquals(Settings().mode, decoded.mode)
    }

    @Test
    fun freshInstallStartsOnboarding() {
        val fresh = mutablePreferencesOf().toSettings(defaultReaders = emptySet())
        assertFalse(Settings().onboardingDone)
        assertFalse(fresh.onboardingDone)
        assertEquals(Routes.onboarding(1), Routes.start(fresh.onboardingDone))
    }

    @Test
    fun completedOnboardingOpensHome() {
        val prefs = mutablePreferencesOf()
        Settings(onboardingDone = true).writeTo(prefs)
        val decoded = prefs.toSettings(defaultReaders = emptySet())
        assertTrue(decoded.onboardingDone)
        assertEquals(Routes.Home, Routes.start(decoded.onboardingDone))
        assertTrue(Routes.allows(Routes.Engine))
        assertTrue(Routes.allows(Routes.onboarding(Tuning.ONBOARDING_STEPS)))
        assertFalse(Routes.allows("invalid_route"))
        assertFalse(Routes.allows("onboarding/0"))
        assertFalse(Routes.allows("onboarding/99"))
        assertNull(Routes.allowed("invalid_route"))
    }

    @Test
    fun activeEngineKeepsOnDeviceDraftForProgressiveMode() {
        val progressive = Settings(
            quality = QualityMode.BEST_PROGRESSIVE,
            onDeviceEngine = OnDeviceEngine.BERGAMOT,
            aiApiKey = "",
        )
        assertEquals(ActiveEngine.Kind.API_PROGRESSIVE, progressive.activeEngine.kind)
        assertTrue(progressive.activeEngine.usingBergamot)
        assertTrue(progressive.activeEngine.needsApiKey)
        val configured = progressive.copy(aiApiKey = "sk-test")
        assertFalse(configured.activeEngine.needsApiKey)
        assertTrue(configured.activeEngine.usingBergamot)
        val apiOnly = progressive.copy(quality = QualityMode.BEST_ONLY, aiEndpoint = "http://localhost")
        assertTrue(apiOnly.activeEngine.apiOnly)
        assertFalse(apiOnly.activeEngine.usingBergamot)
        assertFalse(apiOnly.activeEngine.needsApiKey)
        val fast = Settings(quality = QualityMode.FAST, onDeviceEngine = OnDeviceEngine.MLKIT)
        assertEquals(ActiveEngine.Kind.ML_KIT, fast.activeEngine.kind)
        assertTrue(fast.activeEngine.usingMlKit)
        assertFalse(fast.activeEngine.usesApi)
    }

    @Test
    fun legacyDiskCachePreferenceIsDropped() {
        val prefs = mutablePreferencesOf()
        prefs[booleanPreferencesKey("llmDiskCache")] = false
        val decoded = prefs.toSettings(defaultReaders = emptySet())
        decoded.writeTo(prefs)
        assertFalse(prefs.contains(booleanPreferencesKey("llmDiskCache")))
    }
}
