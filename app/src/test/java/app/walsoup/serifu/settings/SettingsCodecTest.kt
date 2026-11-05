package app.walsoup.serifu.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.ui.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )
        val prefs = mutablePreferencesOf()
        original.writeTo(prefs)
        assertEquals(
            original.copy(settleDelayMs = 500),
            prefs.toSettings(defaultReaders = setOf("should-not-apply")),
        )
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
