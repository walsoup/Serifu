package app.walsoup.serifu.settings

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

data class Settings(
    val mode: TriggerMode = TriggerMode.AUTO,
    val sourceLanguage: SourceLanguage = SourceLanguage.JA,
    val targetLanguage: String = "en",
    val quality: QualityMode = QualityMode.FAST,
    val onDeviceEngine: OnDeviceEngine = OnDeviceEngine.MLKIT,
    val bergamotVariant: ModelVariant = ModelVariant.LITE,
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
) {
    val apiConfigured: Boolean
        get() = aiApiKey.isNotBlank() || aiEndpoint.isNotBlank()

    /**
     * Translator the UI should treat as active. Progressive mode keeps the
     * selected on-device engine for the fast draft and still requires an API
     * key for the upgrade. API-only does not.
     */
    val activeEngine: ActiveEngine
        get() = ActiveEngine.from(this)
}

data class ActiveEngine(
    val kind: Kind,
    val apiConfigured: Boolean,
    val onDevice: OnDeviceEngine,
) {
    enum class Kind {
        ML_KIT,
        BERGAMOT,
        API_ONLY,
        API_PROGRESSIVE,
    }

    val usesApi: Boolean
        get() = kind == Kind.API_ONLY || kind == Kind.API_PROGRESSIVE

    val apiOnly: Boolean
        get() = kind == Kind.API_ONLY

    /** On-device Bergamot draft, including progressive mode. */
    val usingBergamot: Boolean
        get() = kind != Kind.API_ONLY && onDevice == OnDeviceEngine.BERGAMOT

    /** On-device ML Kit draft, including progressive mode. */
    val usingMlKit: Boolean
        get() = kind != Kind.API_ONLY && onDevice == OnDeviceEngine.MLKIT

    val needsApiKey: Boolean
        get() = usesApi && !apiConfigured

    companion object {
        fun from(settings: Settings): ActiveEngine {
            val kind = when (settings.quality) {
                QualityMode.BEST_ONLY -> Kind.API_ONLY
                QualityMode.BEST_PROGRESSIVE -> Kind.API_PROGRESSIVE
                QualityMode.FAST -> when (settings.onDeviceEngine) {
                    OnDeviceEngine.BERGAMOT -> Kind.BERGAMOT
                    OnDeviceEngine.MLKIT -> Kind.ML_KIT
                }
            }
            return ActiveEngine(kind, settings.apiConfigured, settings.onDeviceEngine)
        }
    }
}
