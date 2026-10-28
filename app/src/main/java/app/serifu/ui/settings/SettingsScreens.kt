package app.serifu.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.serifu.R
import app.serifu.SerifuApp
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TargetLanguage
import app.serifu.ml.DeviceCapability
import app.serifu.ml.GemmaModelDownloader
import app.serifu.ml.GemmaTranslationEngine
import app.serifu.ml.ModelCatalog
import app.serifu.overlay.SerifuFonts
import app.serifu.settings.KnownReaders
import app.serifu.ui.components.LaunchableApp
import app.serifu.ui.components.MangaPanel
import app.serifu.ui.components.SealBadge
import app.serifu.ui.components.SpeechBubblePreview
import app.serifu.ui.components.rememberLaunchableApps
import app.serifu.ui.home.HomeViewModel
import app.serifu.ui.theme.DelaGothicOne
import app.serifu.ui.design.GroupLabel
import app.serifu.ui.design.Hairline
import app.serifu.ui.design.Pill
import app.serifu.ui.design.SectionTitle
import app.serifu.ui.design.Serifu
import app.serifu.ui.design.SerifuScreen
import app.serifu.ui.design.SerifuSwitch
import app.serifu.ui.design.SerifuTextField
import app.serifu.ui.design.SettingsCard
import app.serifu.ui.design.SettingLine
import app.serifu.ui.design.SwitchLine
import app.serifu.ui.design.TopBar

@Composable
fun LanguagesScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.languages_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.languages_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )

        SectionTitle(stringResource(R.string.source_label))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceLanguage.entries.forEach { lang ->
                Pill(
                    text = lang.englishName,
                    selected = settings.sourceLanguage == lang,
                    onClick = { viewModel.update { it.copy(sourceLanguage = lang) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTitle(stringResource(R.string.target_label))
        Text(
            text = stringResource(R.string.languages_pivot),
            style = Serifu.type.caption,
            color = c.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        TargetLanguage.all.forEach { language ->
            val selected = settings.targetLanguage == language.tag
            SettingLine(
                label = language.englishName,
                value = if (selected) stringResource(R.string.active_value) else null,
                emphasis = selected,
                showChevron = false,
                onClick = { viewModel.update { it.copy(targetLanguage = language.tag) } },
            )
        }

        SectionTitle(stringResource(R.string.network_label))
        SwitchLine(
            label = stringResource(R.string.wifi_only),
            checked = settings.wifiOnlyDownloads,
            onCheckedChange = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
            subtitle = stringResource(R.string.wifi_subtitle),
            showHairline = false,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ReadersScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps = rememberLaunchableApps()
    var query by remember { mutableStateOf("") }
    val c = Serifu.colors
    val shown = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }.sortedWith(compareByDescending<LaunchableApp> { it.packageName in KnownReaders.packages }.thenBy { it.label.lowercase() })

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.readers_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.readers_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        SerifuTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.find_app),
        )

        SectionTitle(stringResource(R.string.installed_apps))
        shown.forEach { app ->
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LetteringScreen(viewModel: HomeViewModel, onBack: () -> Unit) {    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val sampleFont = SerifuFonts.lettering(settings.letteringFont)

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.lettering_title))
        Spacer(Modifier.height(8.dp))

        // Manga Speech Bubble Preview
        val fontName = when (settings.letteringFont) {
            LetteringFont.COMIC_NEUE -> "Comic Neue Bold"
            LetteringFont.ATKINSON -> "Atkinson Hyperlegible"
            LetteringFont.PATRICK_HAND -> "Patrick Hand"
        }
        MangaPanel {
            SpeechBubblePreview(
                text = if (settings.classicCaps && settings.targetLanguage == "en") {
                    stringResource(R.string.lettering_sample).uppercase()
                } else {
                    stringResource(R.string.lettering_sample)
                },
                fontFamily = sampleFont,
                fontLabel = "$fontName · ${settings.minFontSp}-${settings.maxFontSp} sp",
            )
        }

        SectionTitle(stringResource(R.string.font_label))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LetteringFont.entries.forEach { font ->
                val name = when (font) {
                    LetteringFont.COMIC_NEUE -> "Comic Neue"
                    LetteringFont.ATKINSON -> "Atkinson"
                    LetteringFont.PATRICK_HAND -> "Patrick Hand"
                }
                Pill(
                    text = name,
                    selected = settings.letteringFont == font,
                    onClick = { viewModel.update { it.copy(letteringFont = font) } },
                )
            }
        }
        if (settings.letteringFont == LetteringFont.PATRICK_HAND) {
            Text(
                stringResource(R.string.patrick_note),
                style = Serifu.type.caption,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        SwitchLine(
            label = stringResource(R.string.classic_caps),
            checked = settings.classicCaps,
            onCheckedChange = { checked -> viewModel.update { it.copy(classicCaps = checked) } },
            subtitle = stringResource(R.string.caps_subtitle),
        )

        SectionTitle(stringResource(R.string.font_size_range))
        SerifuSlider(
            label = stringResource(R.string.font_size_min),
            valueText = stringResource(R.string.min_size, settings.minFontSp),
            value = settings.minFontSp.toFloat(),
            range = Tuning.MIN_FONT_SP_FLOOR.toFloat()..Tuning.MIN_FONT_SP_CEILING.toFloat(),
            steps = Tuning.MIN_FONT_SP_CEILING - Tuning.MIN_FONT_SP_FLOOR - 1,
            onChange = { value -> viewModel.update { it.copy(minFontSp = value.toInt()) } },
        )
        SerifuSlider(
            label = stringResource(R.string.font_size_max),
            valueText = stringResource(R.string.max_size, settings.maxFontSp),
            value = settings.maxFontSp.toFloat(),
            range = Tuning.MAX_FONT_SP_FLOOR.toFloat()..Tuning.MAX_FONT_SP_CEILING.toFloat(),
            steps = Tuning.MAX_FONT_SP_CEILING - Tuning.MAX_FONT_SP_FLOOR - 1,
            onChange = { value -> viewModel.update { it.copy(maxFontSp = value.toInt()) } },
        )

        SectionTitle(stringResource(R.string.sfx_label))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfxMode.entries.forEach { mode ->
                Pill(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.sfxMode == mode,
                    onClick = { viewModel.update { it.copy(sfxMode = mode) } },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BehaviourScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.behaviour_title))

        SectionTitle(stringResource(R.string.settle_title))
        SerifuSlider(
            label = stringResource(R.string.settle_debounce),
            valueText = stringResource(R.string.settle_label, settings.settleDelayMs),
            value = settings.settleDelayMs.toFloat(),
            range = Tuning.SETTLE_DELAY_MIN_MS.toFloat()..Tuning.SETTLE_DELAY_MAX_MS.toFloat(),
            steps = (Tuning.SETTLE_DELAY_MAX_MS - Tuning.SETTLE_DELAY_MIN_MS) / Tuning.SETTLE_DELAY_STEP_MS - 1,
            onChange = { value -> viewModel.update { it.copy(settleDelayMs = Tuning.snapSettleDelay(value.toInt())) } },
            subtitle = stringResource(R.string.settle_subtitle),
        )

        SectionTitle(stringResource(R.string.rules_title))
        SwitchLine(
            label = stringResource(R.string.honorifics),
            checked = settings.keepHonorifics,
            onCheckedChange = { checked -> viewModel.update { it.copy(keepHonorifics = checked) } },
            subtitle = stringResource(R.string.honorifics_subtitle),
        )
        SwitchLine(
            label = stringResource(R.string.skip_latin),
            checked = settings.skipLatinText,
            onCheckedChange = { checked -> viewModel.update { it.copy(skipLatinText = checked) } },
            subtitle = stringResource(R.string.skip_latin_subtitle),
        )
        SwitchLine(
            label = stringResource(R.string.haptics),
            checked = settings.haptics,
            onCheckedChange = { checked -> viewModel.update { it.copy(haptics = checked) } },
            subtitle = stringResource(R.string.haptics_subtitle),
        )
        SwitchLine(
            label = stringResource(R.string.llm_cache),
            checked = settings.llmDiskCache,
            onCheckedChange = { checked -> viewModel.update { it.copy(llmDiskCache = checked) } },
            subtitle = stringResource(R.string.llm_cache_subtitle),
        )

        SectionTitle(stringResource(R.string.pill_panel))
        GroupLabel(stringResource(R.string.pill_edge_label))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillSide.entries.forEach { side ->
                Pill(
                    text = stringResource(
                        if (side == PillSide.START) R.string.pill_edge_left else R.string.pill_edge_right,
                    ),
                    selected = settings.pillSide == side,
                    onClick = { viewModel.update { it.copy(pillSide = side) } },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SerifuSlider(
            label = stringResource(R.string.pill_opacity),
            valueText = stringResource(R.string.pill_alpha, (settings.pillIdleAlpha * 100).toInt()),
            value = settings.pillIdleAlpha,
            range = Tuning.PILL_IDLE_ALPHA_MIN..Tuning.PILL_IDLE_ALPHA_MAX,
            steps = 6,
            onChange = { value -> viewModel.update { it.copy(pillIdleAlpha = value) } },
            subtitle = stringResource(R.string.pill_opacity_subtitle),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun EngineScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.engine_title))

        SectionTitle(stringResource(R.string.quality_label))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QualityMode.entries.forEach { mode ->
                val label = when (mode) {
                    QualityMode.FAST -> stringResource(R.string.quality_fast)
                    QualityMode.BEST_PROGRESSIVE -> stringResource(R.string.quality_progressive)
                    QualityMode.BEST_ONLY -> stringResource(R.string.quality_best)
                }
                Pill(
                    text = label,
                    selected = settings.quality == mode,
                    onClick = { viewModel.update { it.copy(quality = mode) } },
                )
            }
        }

        SectionTitle(stringResource(R.string.active_engine))
        MangaPanel {
            Text(stringResource(R.string.engine_mlkit), style = Serifu.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.models_body),
                style = Serifu.type.bodySecondary,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        GemmaRow(viewModel)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GemmaRow(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val c = Serifu.colors
    val gate = remember { DeviceCapability.check(context) }
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(GemmaModelDownloader.WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running = workInfos.firstOrNull()?.let {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } == true
    val progress = workInfos.firstOrNull()?.progress
    val done = progress?.getLong(GemmaModelDownloader.PROGRESS_DONE, 0L) ?: 0L
    val total = progress?.getLong(GemmaModelDownloader.PROGRESS_TOTAL, ModelCatalog.EXPECTED_BYTES) ?: ModelCatalog.EXPECTED_BYTES
    val installed = remember(running) { ModelCatalog.isComplete(context) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Thread {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                GemmaTranslationEngine(context).use { engine -> engine.importSafFile(uri) }
            }.start()
        }
    }

    val stateText = when {
        installed -> stringResource(R.string.gemma_state_ready)
        running -> stringResource(R.string.gemma_downloading_state, formatMb(done), formatMb(total))
        else -> stringResource(R.string.gemma_state_missing, stringResource(R.string.gemma_size))
    }
    MangaPanel {
        Text("Gemma 4 E2B", style = Serifu.type.title, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(stateText, style = Serifu.type.bodySecondary, color = c.textSecondary)
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = c.primary,
                trackColor = c.inactive,
            )
        }
        if (!gate.supported && !installed) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.gemma_unsupported_title) + ": " + (gate.reason ?: ""),
                style = Serifu.type.caption,
                color = c.muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!installed && gate.supported && !running) {
                Pill(
                    text = stringResource(R.string.gemma_install),
                    selected = true,
                    onClick = { GemmaModelDownloader.enqueue(context, wifiOnly = true) },
                )
            }
            if (!installed && !running) {
                Pill(
                    text = stringResource(R.string.gemma_import),
                    selected = false,
                    onClick = { importLauncher.launch("*/*") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.gemma_body),
            style = Serifu.type.caption,
            color = c.muted,
        )
    }
}

private fun formatMb(bytes: Long): String = "${bytes / 1_000_000} MB"

@Composable
fun DebugScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val presence by app.readerPresence.collectAsState()
    val timings by app.pipelineTimings.collectAsState()
    val c = Serifu.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.debug_title))

        SectionTitle(stringResource(R.string.diagnostics))
        SwitchLine(
            label = stringResource(R.string.debug_overlay),
            checked = settings.debugOverlay,
            onCheckedChange = { checked -> viewModel.update { it.copy(debugOverlay = checked) } },
            subtitle = stringResource(R.string.debug_overlay_subtitle),
        )
        SwitchLine(
            label = stringResource(R.string.dynamic_color),
            checked = settings.dynamicColor,
            onCheckedChange = { checked -> viewModel.update { it.copy(dynamicColor = checked) } },
            subtitle = stringResource(R.string.dynamic_color_subtitle),
        )

        SectionTitle(stringResource(R.string.window_state))
        Text(
            text = stringResource(R.string.debug_limit),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        val currentForeground = presence.foregroundPackage?.let {
            stringResource(R.string.foreground_line, it)
        } ?: stringResource(R.string.foreground_none)
        Text(
            text = currentForeground,
            style = Serifu.type.monoSmall,
            color = c.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        val hideLine = if (timings.hideSamples == 0) {
            stringResource(R.string.hide_waiting)
        } else {
            stringResource(R.string.hide_latency, timings.hideUs / 1000, (timings.hideUs % 1000) / 100)
        }
        Text(
            text = hideLine,
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Serifu.colors
    val version = remember {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: ""
    }
    val licenses = remember { context.assets.list("licenses")?.sorted().orEmpty() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.about_title))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Serifu", fontFamily = DelaGothicOne, fontSize = 28.sp, color = c.text)
            Text(
                stringResource(R.string.brand_kana),
                color = c.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.version_format, version),
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )

        SectionTitle(stringResource(R.string.licenses))
        licenses.forEach { name ->
            SettingLine(label = name, showChevron = false, showHairline = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SerifuSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    subtitle: String? = null,
) {
    val c = Serifu.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Serifu.type.body, color = c.text)
            Text(valueText, style = Serifu.type.monoSmall, color = c.textSecondary)
        }
        if (subtitle != null) {
            Text(subtitle, style = Serifu.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.inactive,
            ),
        )
        Hairline()
    }
}
