package app.walsoup.serifu.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import org.junit.Assert.assertEquals
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
            llmDiskCache = false,
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
}
