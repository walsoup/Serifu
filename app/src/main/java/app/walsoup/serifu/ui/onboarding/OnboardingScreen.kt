package app.walsoup.serifu.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.serifu.core.Tuning
import app.serifu.core.model.SourceLanguage
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ml.DownloadErrorKind
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.ui.components.label
import app.walsoup.serifu.settings.KnownReaders
import app.walsoup.serifu.ui.components.LaunchableApp
import app.walsoup.serifu.ui.components.rememberLaunchableApps
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.BrandLockup
import app.walsoup.serifu.ui.design.GroupLabel
import app.walsoup.serifu.ui.design.Hairline
import app.walsoup.serifu.ui.design.Pill
import app.walsoup.serifu.ui.design.SectionTitle
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.design.SerifuProgressBar
import app.walsoup.serifu.ui.design.SerifuSwitch
import app.walsoup.serifu.ui.design.SerifuTextField
import app.walsoup.serifu.ui.design.SettingLine
import app.walsoup.serifu.ui.design.SwitchLine
import app.walsoup.serifu.ui.design.TopBar
import app.walsoup.serifu.ui.design.targetName
import app.walsoup.serifu.ui.home.HomeViewModel
import app.walsoup.serifu.ui.testpage.TestPageActivity
import kotlinx.coroutines.launch

private const val SERVICE_CLASS = "app.walsoup.serifu.service.SerifuAccessibilityService"
private val PACKAGE_INSTALLERS = setOf(
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.shell",
)

@Composable
fun OnboardingScreen(
    step: Int,
    viewModel: HomeViewModel,
    onStep: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Serifu.colors
    val coroutineScope = rememberCoroutineScope()
    val clamped = step.coerceIn(1, Tuning.ONBOARDING_STEPS)
    val showRestricted = remember(context) { needsRestrictedSettings(context) }
    val totalSteps = if (showRestricted) 4 else 3
    val numbered = when (clamped) {
        2 -> 1
        3 -> 2
        4 -> if (showRestricted) 3 else 2
        5 -> if (showRestricted) 4 else 3
        else -> 0
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding(),
    ) {
        if (clamped > 1) {
            Box(Modifier.padding(horizontal = Serifu.margin)) {
                TopBar(title = "", onBack = onBack)
            }
        }
        when (clamped) {
            1 -> WelcomeStep(
                showRestricted = showRestricted,
                totalSteps = totalSteps,
                onNext = { onStep(2) },
            )
            2 -> StepScaffold(
                numbered = numbered,
                totalSteps = totalSteps,
                head = R.string.onboarding_source_head,
                body = R.string.onboarding_source_body,
                footer = {
                    ActionButton(
                        text = stringResource(R.string.continue_action),
                        onClick = { onStep(if (showRestricted) 3 else 4) },
                    )
                },
            ) {
                LanguagesStep(
                    source = settings.sourceLanguage,
                    targetTag = settings.targetLanguage,
                    onSource = { language -> viewModel.update { it.copy(sourceLanguage = language) } },
                    onTarget = { tag -> viewModel.update { it.copy(targetLanguage = tag) } },
                    wifiOnly = settings.wifiOnlyDownloads,
                    onWifi = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
                )
            }
            3 -> StepScaffold(
                numbered = numbered,
                totalSteps = totalSteps,
                head = R.string.onboarding_restricted_head,
                body = R.string.onboarding_restricted_body,
                footer = {
                    ActionButton(stringResource(R.string.open_app_info), onClick = { openAppInfo(context) })
                    Spacer(Modifier.height(8.dp))
                    ActionButton(stringResource(R.string.done_this), onClick = { onStep(4) }, style = ActionStyle.Tonal)
                    Spacer(Modifier.height(8.dp))
                    ActionButton(stringResource(R.string.skip), onClick = { onStep(4) }, style = ActionStyle.Ghost)
                },
            ) { RestrictedSettingsStep() }
            4 -> EnableServiceStep(
                numbered = numbered,
                totalSteps = totalSteps,
                showRestrictedHint = showRestricted,
                onOpen = { openAccessibilitySettings(context) },
                onBackToRestricted = { onStep(3) },
                onEnabled = { onStep(5) },
            )
            else -> TryItStep(
                numbered = numbered,
                totalSteps = totalSteps,
                viewModel = viewModel,
                onFinish = {
                    coroutineScope.launch {
                        viewModel.updateAndCommit { it.copy(onboardingDone = true) }
                        onHome()
                    }
                },
            )
        }
    }
}

/** One setup step: scrolling explanation on top, the actions pinned in the thumb zone. */
@Composable
private fun ColumnScope.StepScaffold(
    numbered: Int,
    totalSteps: Int,
    head: Int,
    body: Int,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Serifu.colors
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.step_of, numbered, totalSteps),
            style = Serifu.type.overline,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(head), style = Serifu.type.headline, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(body), style = Serifu.type.bodySecondary, color = c.textSecondary)
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Serifu.margin, vertical = 12.dp),
        content = footer,
    )
}

@Composable
private fun ColumnScope.WelcomeStep(
    showRestricted: Boolean,
    totalSteps: Int,
    onNext: () -> Unit,
) {
    val c = Serifu.colors
    val steps = buildList {
        add(stringResource(R.string.onboarding_note_languages) to stringResource(R.string.onboarding_note_lang_sub))
        if (showRestricted) {
            add(stringResource(R.string.onboarding_note_restricted) to stringResource(R.string.onboarding_note_restricted_sub))
        }
        add(stringResource(R.string.onboarding_note_service) to stringResource(R.string.onboarding_note_service_sub))
        add(stringResource(R.string.onboarding_note_readers) to stringResource(R.string.onboarding_note_readers_sub))
    }

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        Spacer(Modifier.height(44.dp))
        BrandLockup()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_lede_dynamic, totalSteps),
            style = Serifu.type.body,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        WelcomeIllustration(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        )
        Spacer(Modifier.height(20.dp))
        Hairline()
        steps.forEachIndexed { i, (title, subtitle) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "0${i + 1}",
                    style = Serifu.type.monoSmall,
                    color = c.muted,
                    modifier = Modifier.width(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = Serifu.type.body, color = c.text)
                    Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(16.dp))
    }
    Column(Modifier.padding(horizontal = Serifu.margin, vertical = 12.dp)) {
        ActionButton(text = stringResource(R.string.continue_action), onClick = onNext)
    }
}

@Composable
private fun LanguagesStep(
    source: SourceLanguage,
    targetTag: String,
    onSource: (SourceLanguage) -> Unit,
    onTarget: (String) -> Unit,
    wifiOnly: Boolean,
    onWifi: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val c = Serifu.colors
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    val errors by LanguageModelManager.errors.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
    }

    GroupLabel(stringResource(R.string.onboarding_source_scripts).uppercase())
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceLanguage.entries.forEach { lang ->
            val mark = if (lang.tag in downloadedCodes) " \u2713" else ""
            Pill(
                text = "${lang.englishName}$mark",
                selected = source == lang,
                onClick = { onSource(lang) },
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    GroupLabel(stringResource(R.string.onboarding_target_label).uppercase())
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageModelManager.POPULAR_TARGET_PACKS.forEach { pack ->
            Pill(
                text = pack.name,
                selected = targetTag == pack.code,
                onClick = { onTarget(pack.code) },
            )
        }
    }

    val needed = remember(source, targetTag) { listOf(source.tag, targetTag).distinct() }
    val missing = needed.filter { it !in downloadedCodes }
    val downloading = needed.any { it in inProgressCodes }
    val activeError = needed.firstNotNullOfOrNull { errors[it] }
    val totalMb = missing.sumOf { code ->
        LanguageModelManager.ALL_PACKS.firstOrNull { it.code == code }?.sizeMb ?: 30
    }
    val sourceName = source.englishName
    val targetLabel = targetName(targetTag)

    Spacer(Modifier.height(22.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Serifu.radiusSurface))
            .background(c.surface)
            .border(1.5.dp, if (missing.isNotEmpty() || activeError != null) c.primary else c.hairline, RoundedCornerShape(Serifu.radiusSurface))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_offline_model_title),
            style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
        )
        Spacer(Modifier.height(4.dp))
        when {
            downloading -> {
                Text(
                    text = stringResource(R.string.model_status_downloading, "$sourceName → $targetLabel"),
                    style = Serifu.type.caption,
                    color = c.primaryText,
                )
                Spacer(Modifier.height(10.dp))
                SerifuProgressBar(progress = null)
            }
            activeError != null -> {
                Text(text = activeError.label(), style = Serifu.type.caption, color = c.primaryText)
                Spacer(Modifier.height(10.dp))
                if (activeError.kind == DownloadErrorKind.WIFI_REQUIRED) {
                    ActionButton(
                        text = stringResource(R.string.download_on_mobile_data),
                        onClick = {
                            LanguageModelManager.startDownload(
                                app.appScope,
                                missing.ifEmpty { needed }.toSet(),
                                wifiOnly = false,
                                context = context,
                            )
                        },
                    )
                } else {
                    ActionButton(
                        text = stringResource(R.string.download_retry),
                        onClick = {
                            LanguageModelManager.startDownload(
                                app.appScope,
                                missing.ifEmpty { needed }.toSet(),
                                wifiOnly = wifiOnly,
                                context = context,
                            )
                        },
                    )
                }
            }
            missing.isEmpty() -> {
                Text(
                    text = stringResource(R.string.onboarding_offline_model_ready, sourceName, targetLabel),
                    style = Serifu.type.caption,
                    color = c.primaryText,
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.onboarding_offline_model_body, sourceName, targetLabel, totalMb),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
                val wifiBlocked = wifiOnly && !LanguageModelManager.isOnWifi(context)
                if (wifiBlocked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.wifi_required_error),
                        style = Serifu.type.caption,
                        color = c.primaryText,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (wifiBlocked) {
                    ActionButton(
                        text = stringResource(R.string.download_on_mobile_data),
                        onClick = {
                            LanguageModelManager.startDownload(
                                app.appScope,
                                missing.toSet(),
                                wifiOnly = false,
                                context = context,
                            )
                        },
                        style = ActionStyle.Tonal,
                    )
                } else {
                    ActionButton(
                        text = stringResource(R.string.model_download_now, totalMb),
                        onClick = {
                            LanguageModelManager.startDownload(
                                app.appScope,
                                missing.toSet(),
                                wifiOnly = wifiOnly,
                                context = context,
                            )
                        },
                        style = ActionStyle.Tonal,
                    )
                }
            }
        }
    }

    SectionTitle(stringResource(R.string.network_label))
    Hairline()
    SwitchLine(
        label = stringResource(R.string.wifi_only),
        checked = wifiOnly,
        onCheckedChange = onWifi,
        subtitle = stringResource(R.string.wifi_subtitle),
        showHairline = false,
    )
}

@Composable
private fun RestrictedSettingsStep() {
    val c = Serifu.colors
    RestrictedIllustration(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    )
    Spacer(Modifier.height(16.dp))
    val instructions = listOf(
        "1" to stringResource(R.string.onboarding_restricted_step1),
        "2" to stringResource(R.string.onboarding_restricted_step2),
        "3" to stringResource(R.string.onboarding_restricted_step3),
    )
    Hairline()
    instructions.forEach { (num, step) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(num, style = Serifu.type.monoSmall, color = c.primaryText, modifier = Modifier.width(28.dp))
            Text(step, style = Serifu.type.body, color = c.text)
        }
        Hairline()
    }
}

@Composable
private fun ColumnScope.EnableServiceStep(
    numbered: Int,
    totalSteps: Int,
    showRestrictedHint: Boolean,
    onOpen: () -> Unit,
    onBackToRestricted: () -> Unit,
    onEnabled: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isServiceEnabled(context)) }
    // Only advance on a false -> true change seen here. If the service was already on when
    // the user arrived (for example by pressing Back), stay put so Back is never a trap.
    val alreadyEnabled = remember { enabled }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = isServiceEnabled(context)
            }
        }
        val life = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isServiceEnabled(context)
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        lifecycle.addObserver(life)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(life)
        }
    }

    LaunchedEffect(enabled) {
        if (enabled && !alreadyEnabled) onEnabled()
    }

    StepScaffold(
        numbered = numbered,
        totalSteps = totalSteps,
        head = R.string.onboarding_service_head,
        body = R.string.onboarding_service_body,
        footer = {
            ActionButton(
                text = if (enabled) stringResource(R.string.continue_action) else stringResource(R.string.open_accessibility),
                onClick = if (enabled) onEnabled else onOpen,
            )
            if (!enabled && showRestrictedHint) {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = stringResource(R.string.onboarding_service_restricted_hint),
                    onClick = onBackToRestricted,
                    style = ActionStyle.Ghost,
                )
            }
        },
    ) {
        EnableIllustration(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
        )
        Spacer(Modifier.height(14.dp))
        Hairline()
        SettingLine(
            label = stringResource(R.string.a11y_service_label),
            value = if (enabled) stringResource(R.string.onboarding_service_state_on) else stringResource(R.string.onboarding_service_state_off),
            subtitle = if (enabled) stringResource(R.string.onboarding_service_ready) else stringResource(R.string.onboarding_service_waiting),
            warning = !enabled,
            emphasis = enabled,
            showChevron = false,
            onClick = onOpen,
        )
    }
}

@Composable
private fun ColumnScope.TryItStep(
    numbered: Int,
    totalSteps: Int,
    viewModel: HomeViewModel,
    onFinish: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apps = rememberLaunchableApps()
    val c = Serifu.colors
    var query by rememberSaveable { mutableStateOf("") }

    // First visit with nothing chosen: switch on the known readers that are installed.
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(apps) {
        if (!seeded && apps.isNotEmpty()) {
            seeded = true
            if (settings.readerPackages.isEmpty()) {
                val known = apps.map { it.packageName }.filter { it in KnownReaders.packages }.toSet()
                if (known.isNotEmpty()) viewModel.update { it.copy(readerPackages = known) }
            }
        }
    }

    val knownApps = remember(apps, query) {
        apps.filter { it.packageName in KnownReaders.packages }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }
    val otherApps = remember(apps, query) {
        apps.filter { it.packageName !in KnownReaders.packages }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }

    StepScaffold(
        numbered = numbered,
        totalSteps = totalSteps,
        head = R.string.onboarding_readers_head,
        body = R.string.try_body,
        footer = {
            ActionButton(
                text = if (settings.readerPackages.isEmpty()) {
                    stringResource(R.string.onboarding_readers_empty_warn)
                } else {
                    stringResource(R.string.finish_setup)
                },
                onClick = onFinish,
                style = if (settings.readerPackages.isEmpty()) ActionStyle.Tonal else ActionStyle.Primary,
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                text = stringResource(R.string.onboarding_try_sample),
                onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
                style = ActionStyle.Ghost,
            )
        },
    ) {
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_apps_loading),
                style = Serifu.type.caption,
                color = c.textSecondary,
            )
        } else {
            SerifuTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.find_app),
            )
            if (knownApps.isNotEmpty()) {
                SectionTitle(stringResource(R.string.onboarding_readers_recommended))
                Text(
                    text = stringResource(R.string.onboarding_readers_browser_note),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Hairline()
                knownApps.forEach { app ->
                    OnboardingAppRow(
                        app = app,
                        selected = app.packageName in settings.readerPackages,
                        onToggle = { isChecked ->
                            viewModel.update { current ->
                                val next = current.readerPackages.toMutableSet()
                                if (isChecked) next.add(app.packageName) else next.remove(app.packageName)
                                current.copy(readerPackages = next)
                            }
                        },
                    )
                }
            }
            if (otherApps.isNotEmpty()) {
                SectionTitle(stringResource(R.string.onboarding_readers_other))
                Hairline()
                otherApps.forEach { app ->
                    OnboardingAppRow(
                        app = app,
                        selected = app.packageName in settings.readerPackages,
                        onToggle = { isChecked ->
                            viewModel.update { current ->
                                val next = current.readerPackages.toMutableSet()
                                if (isChecked) next.add(app.packageName) else next.remove(app.packageName)
                                current.copy(readerPackages = next)
                            }
                        },
                    )
                }
            }
            if (knownApps.isEmpty() && otherApps.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.no_apps_match),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun OnboardingAppRow(
    app: LaunchableApp,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val c = Serifu.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = selected,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(app.label, style = Serifu.type.body, color = c.text)
            Text(app.packageName, style = Serifu.type.monoTiny, color = c.textSecondary)
        }
        SerifuSwitch(checked = selected)
    }
    Hairline()
}

private fun needsRestrictedSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    return installer == null || installer in PACKAGE_INSTALLERS
}

private fun isServiceEnabled(context: Context): Boolean {
    if (app.walsoup.serifu.service.SerifuAccessibilityService.isRunning) return true
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
    if (enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name.endsWith(".SerifuAccessibilityService")
    }) return true

    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any { entry ->
        val unflattened = ComponentName.unflattenFromString(entry)
        unflattened?.packageName == context.packageName &&
            (unflattened.className == SERVICE_CLASS || unflattened.shortClassName == ".service.SerifuAccessibilityService")
    }
}

private fun openAppInfo(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {}
    }
}

private fun openAccessibilitySettings(context: Context) {
    app.walsoup.serifu.SerifuApp.openAccessibilitySettings(context)
}
