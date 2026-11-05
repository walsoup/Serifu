package app.walsoup.serifu.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode

internal object SettingsKeys {
    val mode = stringPreferencesKey("mode")
    val sourceLanguage = stringPreferencesKey("sourceLanguage")
    val targetLanguage = stringPreferencesKey("targetLanguage")
    val quality = stringPreferencesKey("quality")
    val settleDelayMs = intPreferencesKey("settleDelayMs")
    val readerPackages = stringSetPreferencesKey("readerPackages")
    val letteringFont = stringPreferencesKey("letteringFont")
    val classicCaps = booleanPreferencesKey("classicCaps")
    val minFontSp = intPreferencesKey("minFontSp")
    val maxFontSp = intPreferencesKey("maxFontSp")
    val sfxMode = stringPreferencesKey("sfxMode")
    val keepHonorifics = booleanPreferencesKey("keepHonorifics")
    val skipLatinText = booleanPreferencesKey("skipLatinText")
    val pillSide = stringPreferencesKey("pillSide")
    val pillYFraction = floatPreferencesKey("pillYFraction")
    val pillIdleAlpha = floatPreferencesKey("pillIdleAlpha")
    val haptics = booleanPreferencesKey("haptics")
    val wifiOnlyDownloads = booleanPreferencesKey("wifiOnlyDownloads")
    val debugOverlay = booleanPreferencesKey("debugOverlay")
    val dynamicColor = booleanPreferencesKey("dynamicColor")
    val onboardingDone = booleanPreferencesKey("onboardingDone")
    val aiApiKey = stringPreferencesKey("aiApiKey")
    val aiModel = stringPreferencesKey("aiModel")
    val aiEndpoint = stringPreferencesKey("aiEndpoint")
    val reasoningEffort = stringPreferencesKey("reasoningEffort")
}

internal fun Settings.writeTo(prefs: MutablePreferences) {
    prefs[SettingsKeys.mode] = mode.name
    prefs[SettingsKeys.sourceLanguage] = sourceLanguage.name
    prefs[SettingsKeys.targetLanguage] = targetLanguage
    prefs[SettingsKeys.quality] = quality.name
    prefs[SettingsKeys.settleDelayMs] = Tuning.snapSettleDelay(settleDelayMs)
    prefs[SettingsKeys.readerPackages] = readerPackages
    prefs[SettingsKeys.letteringFont] = letteringFont.name
    prefs[SettingsKeys.classicCaps] = classicCaps
    prefs[SettingsKeys.minFontSp] = Tuning.snapMinFontSp(minFontSp)
    prefs[SettingsKeys.maxFontSp] = Tuning.snapMaxFontSp(maxFontSp)
    prefs[SettingsKeys.sfxMode] = sfxMode.name
    prefs[SettingsKeys.keepHonorifics] = keepHonorifics
    prefs[SettingsKeys.skipLatinText] = skipLatinText
    prefs[SettingsKeys.pillSide] = pillSide.name
    prefs[SettingsKeys.pillYFraction] = Tuning.snapPillYFraction(pillYFraction)
    prefs[SettingsKeys.pillIdleAlpha] = Tuning.snapPillIdleAlpha(pillIdleAlpha)
    prefs[SettingsKeys.haptics] = haptics
    prefs[SettingsKeys.wifiOnlyDownloads] = wifiOnlyDownloads
    prefs.remove(booleanPreferencesKey("llmDiskCache"))
    prefs[SettingsKeys.debugOverlay] = debugOverlay
    prefs[SettingsKeys.dynamicColor] = dynamicColor
    prefs[SettingsKeys.onboardingDone] = onboardingDone
    prefs[SettingsKeys.aiApiKey] = aiApiKey
    prefs[SettingsKeys.aiModel] = aiModel
    prefs[SettingsKeys.aiEndpoint] = aiEndpoint
    prefs[SettingsKeys.reasoningEffort] = reasoningEffort.name
}

internal fun Preferences.toSettings(defaultReaders: Set<String>): Settings {
    val defaults = Settings()
    return Settings(
        mode = enumOr(this[SettingsKeys.mode], defaults.mode),
        sourceLanguage = enumOr(this[SettingsKeys.sourceLanguage], defaults.sourceLanguage),
        targetLanguage = this[SettingsKeys.targetLanguage] ?: defaults.targetLanguage,
        quality = enumOr(this[SettingsKeys.quality], defaults.quality),
        settleDelayMs = Tuning.snapSettleDelay(this[SettingsKeys.settleDelayMs] ?: defaults.settleDelayMs),
        readerPackages = this[SettingsKeys.readerPackages] ?: defaultReaders,
        letteringFont = enumOr(this[SettingsKeys.letteringFont], defaults.letteringFont),
        classicCaps = this[SettingsKeys.classicCaps] ?: defaults.classicCaps,
        minFontSp = Tuning.snapMinFontSp(this[SettingsKeys.minFontSp] ?: defaults.minFontSp),
        maxFontSp = Tuning.snapMaxFontSp(this[SettingsKeys.maxFontSp] ?: defaults.maxFontSp),
        sfxMode = enumOr(this[SettingsKeys.sfxMode], defaults.sfxMode),
        keepHonorifics = this[SettingsKeys.keepHonorifics] ?: defaults.keepHonorifics,
        skipLatinText = this[SettingsKeys.skipLatinText] ?: defaults.skipLatinText,
        pillSide = enumOr(this[SettingsKeys.pillSide], defaults.pillSide),
        pillYFraction = Tuning.snapPillYFraction(this[SettingsKeys.pillYFraction] ?: defaults.pillYFraction),
        pillIdleAlpha = Tuning.snapPillIdleAlpha(this[SettingsKeys.pillIdleAlpha] ?: defaults.pillIdleAlpha),
        haptics = this[SettingsKeys.haptics] ?: defaults.haptics,
        wifiOnlyDownloads = this[SettingsKeys.wifiOnlyDownloads] ?: defaults.wifiOnlyDownloads,
        debugOverlay = this[SettingsKeys.debugOverlay] ?: defaults.debugOverlay,
        dynamicColor = this[SettingsKeys.dynamicColor] ?: defaults.dynamicColor,
        onboardingDone = this[SettingsKeys.onboardingDone] ?: defaults.onboardingDone,
        aiApiKey = this[SettingsKeys.aiApiKey] ?: defaults.aiApiKey,
        aiModel = this[SettingsKeys.aiModel] ?: defaults.aiModel,
        aiEndpoint = this[SettingsKeys.aiEndpoint] ?: defaults.aiEndpoint,
        reasoningEffort = enumOr(this[SettingsKeys.reasoningEffort], defaults.reasoningEffort),
    )
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
