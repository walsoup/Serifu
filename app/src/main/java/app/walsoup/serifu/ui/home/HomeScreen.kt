package app.walsoup.serifu.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.QualityMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.BrandLockup
import app.walsoup.serifu.ui.design.Hairline
import app.walsoup.serifu.ui.design.Pill
import app.walsoup.serifu.ui.design.ScriptCell
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.design.SettingLine
import app.walsoup.serifu.ui.design.fullName
import app.walsoup.serifu.ui.design.hint
import app.walsoup.serifu.ui.design.label
import app.walsoup.serifu.ui.design.targetName
import app.walsoup.serifu.ui.nav.Routes
import app.walsoup.serifu.ui.testpage.TestPageActivity
import app.walsoup.serifu.ui.versionName

/**
 * Home answers three questions in order: is it working, what is it translating,
 * and how do I change how it behaves. The one primary action lives in the thumb zone.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? SerifuApp
    val isServiceActive by (app?.isServiceActive ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val readers = settings.readerPackages
    val version = remember(context.packageName) { context.versionName() }
    val targetName = targetName(settings.targetLanguage)

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
            Spacer(Modifier.height(32.dp))
            BrandLockup()

            if (settings.onboardingDone && !isServiceActive) {
                Spacer(Modifier.height(16.dp))
                AttentionBanner(
                    title = stringResource(R.string.a11y_off),
                    body = stringResource(R.string.a11y_off_body),
                    onClick = { SerifuApp.openAccessibilitySettings(context) },
                )
            }

            Spacer(Modifier.height(24.dp))
            Hairline()

            // Hero: the current direction. Tapping it opens the language settings.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(R.string.change_languages),
                        onClick = { onNavigate(Routes.Languages) },
                    )
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = settings.sourceLanguage.nativeName,
                    style = Serifu.type.hero,
                    color = c.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u2193",
                    style = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    color = c.primaryText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = targetName.uppercase(),
                    style = Serifu.type.monoSmall.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Medium),
                    color = c.textSecondary,
                )
            }
            Hairline()
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.script_label))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceLanguage.entries.forEach { lang ->
                    ScriptCell(
                        glyph = when (lang) {
                            SourceLanguage.JA -> "\u3042"
                            SourceLanguage.ZH -> "\u4E2D"
                            SourceLanguage.KO -> "\uD55C"
                            else -> lang.seal
                        },
                        name = lang.englishName,
                        selected = settings.sourceLanguage == lang,
                        onClick = { viewModel.update { it.copy(sourceLanguage = lang) } },
                        modifier = Modifier.width(96.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

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
                    TriggerMode.entries.forEach { mode ->
                        Pill(
                            text = mode.label(),
                            selected = settings.mode == mode,
                            onClick = { viewModel.update { it.copy(mode = mode) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(settings.mode.hint(), style = Serifu.type.caption, color = c.textSecondary)

            Spacer(Modifier.height(28.dp))

            SectionLabel(stringResource(R.string.config_label))
            Hairline()
            SettingLine(
                label = stringResource(R.string.languages_label),
                value = stringResource(R.string.direction_format, settings.sourceLanguage.englishName, targetName),
                onClick = { onNavigate(Routes.Languages) },
            )
            SettingLine(
                label = stringResource(R.string.lettering_label),
                value = settings.letteringFont.fullName,
                subtitle = stringResource(
                    R.string.lettering_summary,
                    settings.minFontSp,
                    settings.maxFontSp,
                    settings.sfxMode.label,
                ),
                onClick = { onNavigate(Routes.Lettering) },
            )
            SettingLine(
                label = stringResource(R.string.reader_apps_label),
                value = if (readers.isEmpty()) {
                    stringResource(R.string.none_selected)
                } else {
                    pluralStringResource(R.plurals.apps_count, readers.size, readers.size)
                },
                warning = readers.isEmpty() && settings.onboardingDone,
                onClick = { onNavigate(Routes.Readers) },
            )
            SettingLine(
                label = stringResource(R.string.behaviour_label),
                value = "${settings.settleDelayMs} ms",
                mono = true,
                onClick = { onNavigate(Routes.Behaviour) },
            )
            val engineValue = when (settings.quality) {
                QualityMode.FAST -> when (settings.onDeviceEngine) {
                    OnDeviceEngine.BERGAMOT -> stringResource(R.string.engine_on_device_bergamot)
                    OnDeviceEngine.MLKIT -> stringResource(R.string.engine_on_device_mlkit)
                }
                QualityMode.BEST_PROGRESSIVE, QualityMode.BEST_ONLY -> stringResource(R.string.engine_api)
            }
            SettingLine(
                label = stringResource(R.string.engine_label),
                value = engineValue,
                onClick = { onNavigate(Routes.Engine) },
            )
            SettingLine(
                label = stringResource(R.string.debug_label),
                value = if (settings.debugOverlay) stringResource(R.string.active_value) else stringResource(R.string.off_value),
                onClick = { onNavigate(Routes.Debug) },
            )
            SettingLine(
                label = stringResource(R.string.about_label),
                value = "v$version",
                mono = true,
                onClick = { onNavigate(Routes.About) },
                showHairline = false,
            )

            Spacer(Modifier.height(100.dp))
        }

        // Thumb zone: exactly one primary action, chosen by what is blocking the user.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(horizontal = Serifu.margin, vertical = 12.dp),
        ) {
            when {
                !settings.onboardingDone -> ActionButton(
                    text = stringResource(R.string.begin_setup),
                    onClick = { onNavigate(Routes.onboarding(1)) },
                )
                !isServiceActive -> ActionButton(
                    text = stringResource(R.string.a11y_off_cta),
                    onClick = { SerifuApp.openAccessibilitySettings(context) },
                )
                settings.mode == TriggerMode.OFF -> ActionButton(
                    text = stringResource(R.string.turn_on),
                    onClick = { viewModel.update { it.copy(mode = TriggerMode.AUTO) } },
                )
                else -> ActionButton(
                    text = stringResource(R.string.open_test_page),
                    onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                    style = ActionStyle.Tonal,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = Serifu.type.overline, color = Serifu.colors.textSecondary)
    Spacer(Modifier.height(10.dp))
}

/** Something blocks translation: say what, and make the whole banner the fix. */
@Composable
private fun AttentionBanner(title: String, body: String, onClick: () -> Unit) {
    val c = Serifu.colors
    val shape = RoundedCornerShape(Serifu.radiusControl)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, c.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.primaryText)
        Spacer(Modifier.height(2.dp))
        Text(body, style = Serifu.type.caption, color = c.textSecondary)
    }
}
