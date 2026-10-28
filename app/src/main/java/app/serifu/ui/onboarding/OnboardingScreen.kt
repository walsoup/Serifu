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
import app.serifu.ui.design.Wordmark

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
    onHome: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Serifu.colors
    val clamped = step.coerceIn(1, Tuning.ONBOARDING_STEPS)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Serifu.margin),
        ) {
            when (clamped) {
                1 -> WelcomeStep(onNext = { onStep(2) })
                2 -> LanguagesStep(
                    source = settings.sourceLanguage,
                    onSource = { language -> viewModel.update { it.copy(sourceLanguage = language) } },
                    wifiOnly = settings.wifiOnlyDownloads,
                    onWifi = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
                    onNext = { onStep(if (needsRestrictedSettings(context)) 3 else 4) },
                )
                3 -> RestrictedSettingsStep(
                    onOpen = { openAppInfo(context) },
                    onDone = { onStep(4) },
                    onSkip = { onStep(4) },
                )
                4 -> EnableServiceStep(
                    onOpen = { openAccessibilitySettings(context) },
                    onEnabled = { onStep(5) },
                )
                else -> TryItStep(
                    viewModel = viewModel,
                    onFinish = {
                        viewModel.update { it.copy(onboardingDone = true) }
                        onHome()
                    },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val c = Serifu.colors
    val steps = listOf(
        Triple(stringResource(R.string.onboarding_note_languages), stringResource(R.string.onboarding_note_lang_sub), true),
        Triple(stringResource(R.string.onboarding_note_restricted), stringResource(R.string.onboarding_note_restricted_sub), false),
        Triple(stringResource(R.string.onboarding_note_service), stringResource(R.string.onboarding_note_service_sub), false),
        Triple(stringResource(R.string.onboarding_note_readers), stringResource(R.string.onboarding_note_readers_sub), false),
    )

    Spacer(Modifier.height(56.dp))
    Wordmark("Serifu")
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_lede),
        style = Serifu.type.body,
        color = c.textSecondary,
    )
    Spacer(Modifier.height(32.dp))

    steps.forEachIndexed { i, (title, subtitle, ready) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "0${i + 1}",
                style = Serifu.type.monoSmall,
                color = if (ready) c.primary else c.muted,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = Serifu.type.body, color = c.text)
                Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
            }
            if (ready) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.onboarding_step_ready),
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (i < steps.lastIndex) Hairline()
    }

    Spacer(Modifier.height(48.dp))
    ActionButton(
        text = "Continue",
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Primary,
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun LanguagesStep(
    source: SourceLanguage,
    onSource: (SourceLanguage) -> Unit,
    wifiOnly: Boolean,
    onWifi: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val c = Serifu.colors
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.onboarding_step_01), style = Serifu.type.overline, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_source_head), style = Serifu.type.headline, color = c.text)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_source_body),
        style = Serifu.type.bodySecondary,
        color = c.textSecondary,
    )
    Spacer(Modifier.height(24.dp))

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

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.onboarding_downloads))
    SwitchLine(
        label = stringResource(R.string.wifi_only),
        checked = wifiOnly,
        onCheckedChange = onWifi,
        subtitle = stringResource(R.string.models_body),
        showHairline = false,
    )

    Spacer(Modifier.height(48.dp))
    ActionButton(
        text = "Continue",
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Primary,
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun RestrictedSettingsStep(onOpen: () -> Unit, onDone: () -> Unit, onSkip: () -> Unit) {
    val c = Serifu.colors
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.onboarding_step_02), style = Serifu.type.overline, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_restricted_head), style = Serifu.type.headline, color = c.text)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_restricted_body),
        style = Serifu.type.bodySecondary,
        color = c.textSecondary,
    )
    Spacer(Modifier.height(24.dp))

    val instructions = listOf(
        Pair("1", stringResource(R.string.onboarding_restricted_step1)),
        Pair("2", stringResource(R.string.onboarding_restricted_step2)),
        Pair("3", stringResource(R.string.onboarding_restricted_step3)),
    )

    instructions.forEachIndexed { i, (num, step) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(num, style = Serifu.type.monoSmall, color = c.primary, modifier = Modifier.width(28.dp))
            Text(step, style = Serifu.type.body, color = c.text)
        }
        if (i < instructions.lastIndex) Hairline()
    }

    Spacer(Modifier.height(36.dp))
    ActionButton(
        text = stringResource(R.string.open_app_info),
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Primary,
    )
    Spacer(Modifier.height(8.dp))
    ActionButton(
        text = stringResource(R.string.done_this),
        onClick = onDone,
        style = ActionStyle.Tonal,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    ActionButton(
        text = stringResource(R.string.skip),
        onClick = onSkip,
        style = ActionStyle.Ghost,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun EnableServiceStep(onOpen: () -> Unit, onEnabled: () -> Unit) {
    val c = Serifu.colors
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isServiceEnabled(context)) }
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
        if (enabled) onEnabled()
    }

    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.onboarding_step_03), style = Serifu.type.overline, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_service_head), style = Serifu.type.headline, color = c.text)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_service_body),
        style = Serifu.type.bodySecondary,
        color = c.textSecondary,
    )
    Spacer(Modifier.height(28.dp))

    SettingLine(
        label = stringResource(R.string.a11y_service_label),
        value = if (enabled) stringResource(R.string.onboarding_service_state_on) else stringResource(R.string.onboarding_service_state_off),
        subtitle = if (enabled) stringResource(R.string.onboarding_service_ready) else stringResource(R.string.onboarding_service_waiting),
        warning = !enabled,
        emphasis = enabled,
        showChevron = false,
        onClick = onOpen,
    )

    Spacer(Modifier.height(48.dp))
    ActionButton(
        text = if (enabled) stringResource(R.string.continue_action) else stringResource(R.string.open_accessibility),
        onClick = if (enabled) onEnabled else onOpen,
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Primary,
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun TryItStep(viewModel: HomeViewModel, onFinish: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apps = rememberLaunchableApps()
    val c = Serifu.colors

    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.onboarding_step_04), style = Serifu.type.overline, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_readers_head), style = Serifu.type.headline, color = c.text)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.try_body),
        style = Serifu.type.bodySecondary,
        color = c.textSecondary,
    )
    Spacer(Modifier.height(24.dp))

    SectionTitle(stringResource(R.string.installed_apps))
    if (apps.isEmpty()) {
        Text(
            text = stringResource(R.string.onboarding_apps_loading),
            style = Serifu.type.caption,
            color = c.muted,
        )
    } else {
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
                        role = Role.Checkbox,
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
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(app.label, style = Serifu.type.body, color = c.text)
                    Text(app.packageName, style = Serifu.type.monoTiny, color = c.textSecondary)
                }
                SerifuSwitch(checked = selected, enabled = false)
            }
            Hairline()
        }
    }

    Spacer(Modifier.height(36.dp))
    ActionButton(
        text = stringResource(R.string.onboarding_try_sample),
        onClick = { context.startActivity(Intent(context, TestPageActivity::class.java)) },
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Tonal,
    )
    Spacer(Modifier.height(8.dp))
    ActionButton(
        text = stringResource(R.string.finish_setup),
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth(),
        style = ActionStyle.Primary,
    )
    Spacer(Modifier.height(20.dp))
}

private fun needsRestrictedSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    return installer == null || installer in PACKAGE_INSTALLERS
}

private fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val flat = ComponentName(context.packageName, SERVICE_CLASS).flattenToString()
    return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
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
    val component = ComponentName(context.packageName, SERVICE_CLASS).flattenToString()
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
        putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        putExtra("extra_fragment_arg_key", component)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(details)
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {}
    }
}
