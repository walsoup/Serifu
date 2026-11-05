package app.walsoup.serifu.settings

import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode

data class Settings(
    val mode: TriggerMode = TriggerMode.AUTO,
    val sourceLanguage: SourceLanguage = SourceLanguage.JA,
    val targetLanguage: String = "en",
    val quality: QualityMode = QualityMode.FAST,
    val settleDelayMs: Int = Tuning.SETTLE_DELAY_DEFAULT_MS,
    val readerPackages: Set<String> = emptySet(),
    val letteringFont: LetteringFont = LetteringFont.COMIC_NEUE,
    val classicCaps: Boolean = true,
    val minFontSp: Int = Tuning.MIN_FONT_SP_DEFAULT,
    val maxFontSp: Int = Tuning.MAX_FONT_SP_DEFAULT,
    val sfxMode: SfxMode = SfxMode.COMPACT,
    val keepHonorifics: Boolean = true,
    val skipLatinText: Boolean = true,
    val pillSide: PillSide = PillSide.END,
    val pillYFraction: Float = Tuning.PILL_Y_FRACTION_DEFAULT,
    val pillIdleAlpha: Float = Tuning.PILL_IDLE_ALPHA_DEFAULT,
    val haptics: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val debugOverlay: Boolean = false,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
    val aiApiKey: String = "",
    val aiModel: String = "gpt-4o-mini",
    val aiEndpoint: String = "",
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
)
