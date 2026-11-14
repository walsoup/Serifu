package app.walsoup.serifu.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.ui.components.ActiveModelStatusCard
import app.walsoup.serifu.ui.components.LaunchableApp
import app.walsoup.serifu.ui.components.rememberBergamotRoute
import app.walsoup.serifu.ui.components.rememberLaunchableApps
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.BrandLockup
import app.walsoup.serifu.ui.design.Hairline
import app.walsoup.serifu.ui.design.Pill
import app.walsoup.serifu.ui.design.ScriptCell
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.design.SettingLine
import app.walsoup.serifu.ui.design.StatusBadge
import app.walsoup.serifu.ui.design.engineSummaryLabel
import app.walsoup.serifu.ui.design.fullName
import app.walsoup.serifu.ui.design.hint
import app.walsoup.serifu.ui.design.label
import app.walsoup.serifu.ui.design.targetName
import app.walsoup.serifu.ui.nav.Routes
import app.walsoup.serifu.ui.testpage.TestPageActivity
import app.walsoup.serifu.ui.versionName

/**
 * Home answers three questions in order: is it working, what is it translating,
 * and how do I change how it behaves. The primary action lives in the thumb zone.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? SerifuApp
    val isServiceActive by (app?.isServiceActive ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    val bergamotReadyKeys by (app?.bergamotModels?.readyKeys ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet()))
        .collectAsStateWithLifecycle()
    val c = Serifu.colors
    val readers = settings.readerPackages
    val version = remember(context.packageName) { context.versionName() }
    val targetName = targetName(settings.targetLanguage)

    val launchableApps = rememberLaunchableApps()
    val selectedLaunchableReaders = remember(launchableApps, readers) {
        launchableApps.filter { it.packageName in readers }
    }
    val installedReaders = selectedLaunchableReaders
    var showReaderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
        app?.bergamotModels?.refreshInstalled()
    }

    val neededCodes = remember(settings.sourceLanguage, settings.targetLanguage) {
        listOf(settings.sourceLanguage.tag, settings.targetLanguage).distinct()
    }
    val missingMlKitCodes = neededCodes.filter { it !in downloadedCodes }
    val mlKitDownloading = neededCodes.any { it in inProgressCodes }
    val missingMlKitMb = missingMlKitCodes.sumOf { code ->
        LanguageModelManager.ALL_PACKS.firstOrNull { it.code == code }?.sizeMb ?: 30
    }

    val active = settings.activeEngine
    val usingApiOnly = active.apiOnly
    val usingBergamot = active.usingBergamot
    val apiConfigured = active.apiConfigured

    val resolvedBergamot = if (usingBergamot && app != null) {
        rememberBergamotRoute(
            bergamotModels = app.bergamotModels,
            sourceTag = settings.sourceLanguage.tag,
            targetTag = settings.targetLanguage,
            variant = settings.bergamotVariant,
        )
    } else {
        null
    }
    val bergamotReady = remember(resolvedBergamot, bergamotReadyKeys) {
        val route = resolvedBergamot?.route
        app != null && route != null && app.bergamotModels.isRouteReady(route)
    }
    val bergamotRouteKeys = remember(resolvedBergamot, app) {
        val route = resolvedBergamot?.route
        if (app != null && route != null) app.bergamotModels.routeStorageKeys(route) else emptyList()
    }
    val bergamotInProgress by (app?.bergamotModels?.inProgressKeys ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet()))
        .collectAsStateWithLifecycle()
    val bergamotDownloading = bergamotRouteKeys.any { it in bergamotInProgress }

    if (showReaderPicker) {
        ReaderPickerDialog(
            readers = selectedLaunchableReaders,
            onPick = { picked ->
                showReaderPicker = false
                launchPackage(context, picked.packageName)
            },
            onManageReaders = {
                showReaderPicker = false
                onNavigate(Routes.Readers)
            },
            onDismiss = { showReaderPicker = false },
        )
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
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandLockup()
                if (settings.onboardingDone && isServiceActive && settings.mode != TriggerMode.OFF) {
                    StatusBadge(
                        text = settings.mode.label(),
                        highlighted = true,
                    )
                }
            }

            if (settings.onboardingDone && isServiceActive && settings.mode != TriggerMode.OFF) {
                Spacer(Modifier.height(10.dp))
                val watchingText = when {
                    installedReaders.isEmpty() -> stringResource(R.string.home_status_no_readers)
                    installedReaders.size == 1 -> stringResource(
                        R.string.home_status_watching_one,
                        installedReaders.first().label,
                        settings.mode.label(),
                    )
                    else -> stringResource(
                        R.string.home_status_watching,
                        installedReaders.size,
                        settings.mode.label(),
                    )
                }
                Text(
                    text = watchingText,
                    style = Serifu.type.caption,
                    color = if (installedReaders.isEmpty()) c.primaryText else c.textSecondary,
                )
            }

            if (settings.onboardingDone && !isServiceActive) {
                Spacer(Modifier.height(16.dp))
                AttentionBanner(
                    title = stringResource(R.string.a11y_off),
                    body = stringResource(R.string.a11y_off_body),
                    onClick = { SerifuApp.openAccessibilitySettings(context) },
                )
            }

            Spacer(Modifier.height(20.dp))
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

            val showStatusCard = app != null
            if (showStatusCard && app != null) {
                ActiveModelStatusCard(
                    settings = settings,
                    onOpenEngine = { onNavigate(Routes.Engine) },
                    onOpenLanguages = { onNavigate(Routes.Languages) },
                    showReadyState = false,
                    resolvedRoute = resolvedBergamot,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

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
            val showMlKitWarning = active.usingMlKit && missingMlKitCodes.isNotEmpty()
            SettingLine(
                label = stringResource(R.string.languages_label),
                value = stringResource(R.string.direction_format, settings.sourceLanguage.englishName, targetName),
                subtitle = if (showMlKitWarning) stringResource(R.string.home_pack_missing_short) else null,
                warning = showMlKitWarning,
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
                value = if (installedReaders.isEmpty()) {
                    stringResource(R.string.none_selected)
                } else {
                    pluralStringResource(R.plurals.apps_count, installedReaders.size, installedReaders.size)
                },
                warning = installedReaders.isEmpty() && settings.onboardingDone,
                onClick = { onNavigate(Routes.Readers) },
            )
            SettingLine(
                label = stringResource(R.string.behaviour_label),
                value = "${settings.settleDelayMs} ms",
                mono = true,
                onClick = { onNavigate(Routes.Behaviour) },
            )
            val engineValue = engineSummaryLabel(settings, bergamotReady = bergamotReady)
            val engineNeedsAttention = (usingBergamot && !bergamotReady) || active.needsApiKey
            SettingLine(
                label = stringResource(R.string.engine_label),
                value = engineValue,
                warning = engineNeedsAttention,
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

            Spacer(Modifier.height(120.dp))
        }

        // Thumb zone: primary action resolves the top blocker, or opens a reader directly when ready.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(horizontal = Serifu.margin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    text = stringResource(R.string.turn_on_auto),
                    onClick = { viewModel.update { it.copy(mode = TriggerMode.AUTO) } },
                )
                active.needsApiKey -> {
                    ActionButton(
                        text = stringResource(R.string.home_configure_api_cta),
                        onClick = { onNavigate(Routes.Engine) },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
                active.usingMlKit && missingMlKitCodes.isNotEmpty() && !mlKitDownloading && app != null -> {
                    ActionButton(
                        text = stringResource(R.string.home_download_pack_cta, missingMlKitMb),
                        onClick = {
                            LanguageModelManager.startDownload(
                                app.appScope,
                                missingMlKitCodes.toSet(),
                                settings.wifiOnlyDownloads,
                                context,
                            )
                        },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
                usingBergamot && !bergamotReady && !bergamotDownloading && app != null && resolvedBergamot?.route != null -> {
                    val route = resolvedBergamot.route
                    ActionButton(
                        text = stringResource(R.string.home_download_pack_cta, route.sizeMb),
                        onClick = {
                            app.bergamotModels.startDownloadRoute(
                                app.appScope,
                                route,
                                settings.wifiOnlyDownloads,
                            )
                        },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
                installedReaders.isEmpty() -> {
                    ActionButton(
                        text = stringResource(R.string.home_select_readers_cta),
                        onClick = { onNavigate(Routes.Readers) },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
                selectedLaunchableReaders.size == 1 -> {
                    val reader = selectedLaunchableReaders.first()
                    ActionButton(
                        text = stringResource(R.string.home_open_reader, reader.label),
                        onClick = { launchPackage(context, reader.packageName) },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
                selectedLaunchableReaders.size > 1 -> {
                    ActionButton(
                        text = stringResource(R.string.home_choose_reader),
                        onClick = { showReaderPicker = true },
                    )
                    ActionButton(
                        text = stringResource(R.string.open_test_page),
                        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                        style = ActionStyle.Ghost,
                    )
                }
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
private fun ReaderPickerDialog(
    readers: List<LaunchableApp>,
    onPick: (LaunchableApp) -> Unit,
    onManageReaders: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Serifu.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_reader_picker_title), style = Serifu.type.title, color = c.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_reader_picker_subtitle),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Hairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    readers.forEach { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable { onPick(app) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = Serifu.type.body, color = c.text, modifier = Modifier.weight(1f))
                            Text("\u203A", style = Serifu.type.body.copy(fontSize = 18.sp), color = c.muted)
                        }
                        Hairline()
                    }
                }
            }
        },
        confirmButton = {
            ActionButton(
                text = stringResource(R.string.reader_apps_label),
                onClick = onManageReaders,
                style = ActionStyle.Ghost,
            )
        },
        dismissButton = {
            ActionButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                style = ActionStyle.Tonal,
            )
        },
        shape = RoundedCornerShape(Serifu.radiusSurface),
        containerColor = c.surface,
        tonalElevation = 0.dp,
    )
}

private fun launchPackage(context: android.content.Context, packageName: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(launch) }
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
