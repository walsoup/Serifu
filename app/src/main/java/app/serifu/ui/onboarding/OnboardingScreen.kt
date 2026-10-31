package app.serifu.ui.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.serifu.R
import app.serifu.core.Tuning
import app.serifu.core.model.SourceLanguage
import app.serifu.settings.KnownReaders
import app.serifu.ui.components.LaunchableApp
import app.serifu.ui.components.rememberLaunchableApps
import app.serifu.ui.home.HomeViewModel
import app.serifu.ui.testpage.TestPageActivity
import app.serifu.ui.design.ActionButton
import app.serifu.ui.design.ActionStyle
import app.serifu.ui.design.GroupLabel
import app.serifu.ui.design.Hairline
import app.serifu.ui.design.Pill
import app.serifu.ui.design.Serifu
import app.serifu.ui.design.SectionTitle
import app.serifu.ui.design.SettingLine
import app.serifu.ui.design.SerifuSwitch
import app.serifu.ui.design.SwitchLine
import app.serifu.ui.design.BrandLockup
import app.serifu.ui.design.TopBar

private const val SERVICE_CLASS = "app.serifu.service.SerifuAccessibilityService"
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
    val clamped = step.coerceIn(1, Tuning.ONBOARDING_STEPS)

    // Step 1 is the welcome page; 2..5 are the four numbered steps.
    val numbered = clamped - 1

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
            1 -> WelcomeStep(onNext = { onStep(2) })
            2 -> StepScaffold(
                numbered, R.string.onboarding_source_head, R.string.onboarding_source_body,
                footer = {
                    ActionButton(
                        text = stringResource(R.string.continue_action),
                        onClick = { onStep(if (needsRestrictedSettings(context)) 3 else 4) },
                    )
                },
            ) {
                LanguagesStep(
                    source = settings.sourceLanguage,
                    onSource = { language -> viewModel.update { it.copy(sourceLanguage = language) } },
                    wifiOnly = settings.wifiOnlyDownloads,
                    onWifi = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
                )
            }
            3 -> StepScaffold(
                numbered, R.string.onboarding_restricted_head, R.string.onboarding_restricted_body,
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
                onOpen = { openAccessibilitySettings(context) },
                onEnabled = { onStep(5) },
            )
            else -> TryItStep(
                numbered = numbered,
                viewModel = viewModel,
                onFinish = {
                    viewModel.update { it.copy(onboardingDone = true) }
                    onHome()
                },
            )
        }
    }
}

/** One setup step: scrolling explanation on top, the actions pinned in the thumb zone. */
@Composable
private fun ColumnScope.StepScaffold(
    numbered: Int,
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
            stringResource(R.string.step_of, numbered, Tuning.ONBOARDING_STEPS - 1),
            style = Serifu.type.overline,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(head), style = Serifu.type.headline, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(body), style = Serifu.type.bodySecondary, color = c.textSecondary)
        Spacer(Modifier.height(24.dp))
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
private fun ColumnScope.WelcomeStep(onNext: () -> Unit) {
    val c = Serifu.colors
    val steps = listOf(
        stringResource(R.string.onboarding_note_languages) to stringResource(R.string.onboarding_note_lang_sub),
        stringResource(R.string.onboarding_note_restricted) to stringResource(R.string.onboarding_note_restricted_sub),
        stringResource(R.string.onboarding_note_service) to stringResource(R.string.onboarding_note_service_sub),
        stringResource(R.string.onboarding_note_readers) to stringResource(R.string.onboarding_note_readers_sub),
    )

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        Spacer(Modifier.height(56.dp))
        BrandLockup()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_lede),
            style = Serifu.type.body,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(32.dp))
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
    onSource: (SourceLanguage) -> Unit,
    wifiOnly: Boolean,
    onWifi: (Boolean) -> Unit,
) {
    GroupLabel(stringResource(R.string.onboarding_source_scripts).uppercase())
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceLanguage.entries.forEach { lang ->
            Pill(
                text = lang.englishName,
                selected = source == lang,
                onClick = { onSource(lang) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionTitle(stringResource(R.string.onboarding_downloads))
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
private fun ColumnScope.EnableServiceStep(numbered: Int, onOpen: () -> Unit, onEnabled: () -> Unit) {
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
        numbered, R.string.onboarding_service_head, R.string.onboarding_service_body,
        footer = {
            ActionButton(
                text = if (enabled) stringResource(R.string.continue_action) else stringResource(R.string.open_accessibility),
                onClick = if (enabled) onEnabled else onOpen,
            )
        },
    ) {
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
private fun ColumnScope.TryItStep(numbered: Int, viewModel: HomeViewModel, onFinish: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apps = rememberLaunchableApps()
    val c = Serifu.colors

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

    StepScaffold(
        numbered, R.string.onboarding_readers_head, R.string.try_body,
        footer = {
            ActionButton(stringResource(R.string.finish_setup), onClick = onFinish)
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
            Hairline()
            apps.sortedWith(
                compareByDescending<LaunchableApp> { it.packageName in KnownReaders.packages }
                    .thenBy { it.label.lowercase() },
            ).forEach { app ->
                val selected = app.packageName in settings.readerPackages
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = selected,
                            role = Role.Switch,
                            onValueChange = { isChecked ->
                                viewModel.update { current ->
                                    val next = current.readerPackages.toMutableSet()
                                    if (isChecked) next.add(app.packageName) else next.remove(app.packageName)
                                    current.copy(readerPackages = next)
                                }
                            },
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
        }
    }
}

private fun needsRestrictedSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    return installer == null || installer in PACKAGE_INSTALLERS
}

private fun isServiceEnabled(context: Context): Boolean {
    if (app.serifu.service.SerifuAccessibilityService.isRunning) return true
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
    app.serifu.SerifuApp.openAccessibilitySettings(context)
}
