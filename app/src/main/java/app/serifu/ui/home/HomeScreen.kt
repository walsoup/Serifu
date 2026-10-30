package app.serifu.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.serifu.R
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.serifu.ui.design.ActionButton
import app.serifu.ui.design.ActionStyle
import app.serifu.ui.design.Pill
import app.serifu.ui.design.ScriptCell
import app.serifu.ui.design.SealMark
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import app.serifu.SerifuApp
import app.serifu.ui.design.Serifu
import app.serifu.ui.design.SettingsCard
import app.serifu.ui.design.SettingLine
import app.serifu.ui.nav.Routes
import app.serifu.ui.testpage.TestPageActivity

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? SerifuApp
    val isServiceActive by (app?.isServiceActive ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val readers = settings.readerPackages.sorted()

    val heroKanji = when (settings.sourceLanguage) {
        SourceLanguage.JA -> "日本語"
        SourceLanguage.KO -> "한국어"
        SourceLanguage.ZH -> "中文"
    }
    val heroGlyph = when (settings.sourceLanguage) {
        SourceLanguage.JA -> "あ"
        SourceLanguage.KO -> "한"
        SourceLanguage.ZH -> "中"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Serifu.margin),
        ) {
            Spacer(Modifier.height(40.dp))

            // Brand header with filled hanko seal mark
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Serifu",
                    style = Serifu.type.brand,
                    color = c.text,
                )
                Spacer(Modifier.width(12.dp))
                SealMark(size = 30.dp)
            }

            if (!isServiceActive) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.primary.copy(alpha = 0.08f))
                        .border(1.dp, c.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { SerifuApp.openAccessibilitySettings(context) }
                        .padding(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Service Inactive",
                                style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = c.primary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Tap to enable Serifu in Android settings to translate screen text.",
                                style = Serifu.type.caption,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            text = "Enable",
                            selected = true,
                            onClick = { SerifuApp.openAccessibilitySettings(context) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            HairlineInCard()

            // Hero: current translation direction between hairlines
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = heroKanji,
                    style = Serifu.type.hero,
                    color = c.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "↓",
                    style = TextStyle(fontSize = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = c.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.target_default_english).uppercase(),
                    style = Serifu.type.monoSmall.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Medium),
                    color = c.textSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))
            HairlineInCard()

            // Script grid
            SectionLabel(stringResource(R.string.script_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val langs = listOf(
                    Triple("あ", "Japanese", SourceLanguage.JA),
                    Triple("한", "Korean", SourceLanguage.KO),
                    Triple("中", "Chinese", SourceLanguage.ZH),
                )
                langs.forEach { (glyph, name, lang) ->
                    ScriptCell(
                        glyph = glyph,
                        name = name,
                        selected = settings.sourceLanguage == lang,
                        onClick = { viewModel.update { it.copy(sourceLanguage = lang) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Trigger control
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.trigger_label),
                    style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf("Auto" to TriggerMode.AUTO, "Manual" to TriggerMode.MANUAL, "Off" to TriggerMode.OFF)
                    modes.forEach { (label, mode) ->
                        Pill(
                            text = label,
                            selected = settings.mode == mode,
                            onClick = { viewModel.update { it.copy(mode = mode) } },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Configuration
            SectionLabel(stringResource(R.string.config_label))
            SettingsCard {
                SettingLine(
                    label = stringResource(R.string.typeface_label),
                    value = fontName(settings.letteringFont),
                    onClick = { onNavigate(Routes.Lettering) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.font_size_range),
                    value = "${settings.minFontSp}–${settings.maxFontSp} sp",
                    onClick = { onNavigate(Routes.Lettering) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.sfx_label),
                    value = settings.sfxMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { onNavigate(Routes.Lettering) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.reader_apps_label),
                    value = if (readers.isEmpty()) {
                        stringResource(R.string.none_selected)
                    } else {
                        pluralStringResource(R.plurals.apps_count, readers.size, readers.size)
                    },
                    onClick = { onNavigate(Routes.Readers) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.engine_label),
                    value = stringResource(R.string.engine_value),
                    showChevron = true,
                    onClick = { onNavigate(Routes.Engine) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.settle_label_short),
                    value = "${settings.settleDelayMs} ms",
                    onClick = { onNavigate(Routes.Behaviour) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.debug_label),
                    value = if (settings.debugOverlay) stringResource(R.string.active_value) else stringResource(R.string.off_value),
                    onClick = { onNavigate(Routes.Debug) },
                    showHairline = true,
                )
                SettingLine(
                    label = stringResource(R.string.about_label),
                    value = "v0.1.0",
                    onClick = { onNavigate(Routes.About) },
                    showHairline = !settings.onboardingDone,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // Thumb-zone actions
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(horizontal = Serifu.margin, vertical = 12.dp),
        ) {
            if (!settings.onboardingDone) {
                ActionButton(
                    text = stringResource(R.string.begin_setup),
                    onClick = { onNavigate(Routes.onboarding(1)) },
                    style = ActionStyle.Primary,
                )
            } else if (!isServiceActive) {
                ActionButton(
                    text = "Enable Accessibility Service",
                    onClick = { SerifuApp.openAccessibilitySettings(context) },
                    style = ActionStyle.Primary,
                )
            } else if (settings.mode == TriggerMode.OFF) {
                ActionButton(
                    text = stringResource(R.string.turn_on),
                    onClick = { viewModel.update { it.copy(mode = TriggerMode.AUTO) } },
                    style = ActionStyle.Primary,
                )
            } else {
                ActionButton(
                    text = stringResource(R.string.open_test_page),
                    onClick = {
                        context.startActivity(Intent(context, TestPageActivity::class.java))
                    },
                    style = ActionStyle.Primary,
                )
            }
        }
    }
}

private fun fontName(font: app.serifu.core.model.LetteringFont): String = when (font) {
    app.serifu.core.model.LetteringFont.COMIC_NEUE -> "Comic Neue Bold"
    app.serifu.core.model.LetteringFont.ATKINSON -> "Atkinson Hyperlegible"
    app.serifu.core.model.LetteringFont.PATRICK_HAND -> "Patrick Hand"
}

@Composable
private fun SectionLabel(text: String) {
    val c = Serifu.colors
    Text(text, style = Serifu.type.overline, color = c.textSecondary)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HairlineInCard() {
    val c = Serifu.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hairline),
    )
}
