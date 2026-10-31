package app.walsoup.serifu.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.walsoup.serifu.ui.components.SerifuIcons
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.walsoup.serifu.ui.testpage.TestPageActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TargetLanguage
import androidx.compose.foundation.verticalScroll
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.ml.LanguagePack
import kotlinx.coroutines.launch
import app.walsoup.serifu.overlay.SerifuFonts
import app.walsoup.serifu.settings.KnownReaders
import app.walsoup.serifu.ui.components.LaunchableApp
import app.walsoup.serifu.ui.components.MangaPanel
import app.walsoup.serifu.ui.components.SealBadge
import app.walsoup.serifu.ui.components.SpeechBubblePreview
import app.walsoup.serifu.ui.components.rememberLaunchableApps
import app.walsoup.serifu.ui.home.HomeViewModel
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.BrandLockup
import app.walsoup.serifu.ui.design.SerifuConfirmDialog
import app.walsoup.serifu.ui.design.SerifuSlider
import app.walsoup.serifu.ui.design.fullName
import app.walsoup.serifu.ui.design.label
import app.walsoup.serifu.ui.design.shortName
import app.walsoup.serifu.ui.design.targetName
import app.walsoup.serifu.ui.design.LightPalette
import app.walsoup.serifu.ui.design.GroupLabel
import app.walsoup.serifu.ui.design.Hairline
import app.walsoup.serifu.ui.design.Pill
import app.walsoup.serifu.ui.design.SectionTitle
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.design.SerifuScreen
import app.walsoup.serifu.ui.design.SerifuSwitch
import app.walsoup.serifu.ui.design.SerifuTextField
import app.walsoup.serifu.ui.design.SettingLine
import app.walsoup.serifu.ui.design.SwitchLine
import app.walsoup.serifu.ui.design.TopBar
import app.walsoup.serifu.ui.versionName

@Composable
fun LanguagesScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<LanguagePack?>(null) }
    var showAllTargets by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
    }

    val needed = remember(settings.sourceLanguage, settings.targetLanguage) {
        setOf(settings.sourceLanguage.tag, settings.targetLanguage)
    }
    val missing = LanguageModelManager.ALL_PACKS.filter { it.code !in downloadedCodes && it.code !in inProgressCodes }
    val missingNeeded = missing.filter { it.code in needed }

    pendingDelete?.let { pack ->
        SerifuConfirmDialog(
            title = stringResource(R.string.delete_pack_title, pack.name),
            text = stringResource(R.string.delete_pack_body, pack.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                coroutineScope.launch { LanguageModelManager.deletePack(pack.code) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceLanguage.entries.forEach { lang ->
                Pill(
                    text = lang.englishName,
                    selected = settings.sourceLanguage == lang,
                    onClick = { viewModel.update { it.copy(sourceLanguage = lang) } },
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
        Hairline()
        // The ten languages with offline packs come first; the long tail stays one tap away.
        val popular = remember { LanguageModelManager.POPULAR_TARGET_PACKS.map { TargetLanguage.fromTag(it.code) } }
        val selectedExtra = TargetLanguage.fromTag(settings.targetLanguage).takeIf { it !in popular }
        val shownTargets = when {
            showAllTargets -> TargetLanguage.all
            selectedExtra != null -> listOf(selectedExtra) + popular
            else -> popular
        }
        shownTargets.forEach { language ->
            val selected = settings.targetLanguage == language.tag
            SettingLine(
                label = language.englishName,
                value = if (selected) stringResource(R.string.active_value) else null,
                emphasis = selected,
                showChevron = false,
                onClick = { viewModel.update { it.copy(targetLanguage = language.tag) } },
            )
        }
        SettingLine(
            label = if (showAllTargets) stringResource(R.string.fewer_languages) else stringResource(R.string.more_languages, TargetLanguage.all.size),
            showChevron = false,
            emphasis = true,
            onClick = { showAllTargets = !showAllTargets },
        )

        SectionTitle(stringResource(R.string.packs_title))
        Text(
            text = stringResource(R.string.packs_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        // Needed packs are what stops translation working, so they get the primary action.
        // Everything else is optional and stays quiet.
        if (missingNeeded.isNotEmpty()) {
            ActionButton(
                text = stringResource(R.string.packs_download_needed, missingNeeded.size, missingNeeded.sumOf { it.sizeMb }),
                onClick = {
                    coroutineScope.launch {
                        LanguageModelManager.downloadPacks(missingNeeded.map { it.code }.toSet(), settings.wifiOnlyDownloads)
                    }
                },
            )
        } else {
            Text(
                stringResource(R.string.packs_needed_ready, settings.sourceLanguage.englishName, targetName(settings.targetLanguage)),
                style = Serifu.type.caption,
                color = c.primaryText,
            )
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = stringResource(R.string.packs_download_missing, missing.size, missing.sumOf { it.sizeMb }),
                    onClick = {
                        coroutineScope.launch {
                            LanguageModelManager.downloadPacks(missing.map { it.code }.toSet(), settings.wifiOnlyDownloads)
                        }
                    },
                    style = ActionStyle.Ghost,
                )
            }
        }

        GroupHeader(stringResource(R.string.source_packs))
        Hairline()
        LanguageModelManager.SOURCE_PACKS.forEach { pack ->
            LanguagePackRow(pack, settings.sourceLanguage.englishName, targetName(settings.targetLanguage), pack.code in needed,
                pack.code in downloadedCodes, pack.code in inProgressCodes,
                onDownload = { coroutineScope.launch { LanguageModelManager.downloadPacks(setOf(pack.code), settings.wifiOnlyDownloads) } },
                onDelete = { pendingDelete = pack })
        }

        GroupHeader(stringResource(R.string.target_packs))
        Hairline()
        LanguageModelManager.POPULAR_TARGET_PACKS.forEach { pack ->
            LanguagePackRow(pack, settings.sourceLanguage.englishName, targetName(settings.targetLanguage), pack.code in needed,
                pack.code in downloadedCodes, pack.code in inProgressCodes,
                onDownload = { coroutineScope.launch { LanguageModelManager.downloadPacks(setOf(pack.code), settings.wifiOnlyDownloads) } },
                onDelete = { pendingDelete = pack })
        }

        SectionTitle(stringResource(R.string.network_label))
        Hairline()
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
private fun GroupHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = Serifu.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Serifu.colors.textSecondary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LanguagePackRow(
    pack: LanguagePack,
    sourceName: String,
    targetName: String,
    isNeeded: Boolean,
    isDownloaded: Boolean,
    isInProgress: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Serifu.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (pack.nativeName == pack.name) pack.name else "${pack.name} \u00B7 ${pack.nativeName}", style = Serifu.type.body, color = c.text)
            val (statusText, statusColor) = when {
                isInProgress -> stringResource(R.string.pack_downloading) to c.primaryText
                isDownloaded -> stringResource(R.string.pack_ready, pack.sizeMb) to c.textSecondary
                isNeeded -> stringResource(R.string.pack_needed, sourceName, targetName) to c.primaryText
                else -> stringResource(R.string.pack_not_downloaded, pack.sizeMb) to c.textSecondary
            }
            Text(statusText, style = Serifu.type.caption, color = statusColor)
        }
        Spacer(Modifier.width(12.dp))
        when {
            isInProgress -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = c.primary,
            )
            isDownloaded -> Pill(text = stringResource(R.string.delete), selected = false, onClick = onDelete)
            else -> Pill(text = stringResource(R.string.download), selected = isNeeded, onClick = onDownload)
        }
    }
    Hairline()
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
        if (shown.isEmpty()) {
            Text(stringResource(R.string.no_apps_match), style = Serifu.type.caption, color = c.textSecondary)
        }
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
                SerifuSwitch(checked = selected)
            }
            Hairline()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LetteringScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        MangaPanel(
            containerColor = LightPalette.bg,
            borderColor = LightPalette.text.copy(alpha = 0.85f),
        ) {
            SpeechBubblePreview(
                text = if (settings.classicCaps && settings.targetLanguage == "en") {
                    stringResource(R.string.lettering_sample).uppercase()
                } else {
                    stringResource(R.string.lettering_sample)
                },
                fontFamily = sampleFont,
                fontLabel = "${settings.letteringFont.fullName} · ${settings.minFontSp}-${settings.maxFontSp} sp",
            )
        }

        SectionTitle(stringResource(R.string.font_label))
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LetteringFont.entries.forEach { font ->
                Pill(
                    text = font.shortName,
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
                    text = mode.label,
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
    val downloaded by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { LanguageModelManager.refreshDownloaded() }
    val packs = LanguageModelManager.SOURCE_PACKS + LanguageModelManager.POPULAR_TARGET_PACKS.take(1)

    Column(
        Modifier
            .fillMaxSize()
            .background(Serifu.colors.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
    ) {
        TopBar(onBack = onBack, title = stringResource(R.string.engine_title))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.engine_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )

        SectionTitle(stringResource(R.string.engine_mode_label))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = stringResource(R.string.engine_on_device),
                selected = settings.quality == QualityMode.FAST,
                onClick = { viewModel.update { it.copy(quality = QualityMode.FAST) } },
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = stringResource(R.string.engine_api),
                selected = settings.quality == QualityMode.BEST_ONLY || settings.quality == QualityMode.BEST_PROGRESSIVE,
                onClick = { viewModel.update { it.copy(quality = QualityMode.BEST_ONLY) } },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (settings.quality == QualityMode.FAST) R.string.engine_on_device_desc
                else R.string.engine_api_desc,
            ),
            style = Serifu.type.caption,
            color = c.textSecondary,
        )

        SectionTitle(stringResource(R.string.engine_api_title))
        Text(
            text = stringResource(R.string.engine_api_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        var keyFocus by remember { mutableStateOf(false) }
        var modelFocus by remember { mutableStateOf(false) }
        var endpointFocus by remember { mutableStateOf(false) }

        var localKey by rememberSaveable { mutableStateOf(settings.aiApiKey) }
        var localModel by rememberSaveable { mutableStateOf(settings.aiModel) }
        var localEndpoint by rememberSaveable { mutableStateOf(settings.aiEndpoint) }
        var showPassword by remember { mutableStateOf(false) }

        // Sync from external settings updates only when user is not actively typing
        LaunchedEffect(settings.aiApiKey) {
            if (!keyFocus) localKey = settings.aiApiKey
        }
        LaunchedEffect(settings.aiModel) {
            if (!modelFocus) localModel = settings.aiModel
        }
        LaunchedEffect(settings.aiEndpoint) {
            if (!endpointFocus) localEndpoint = settings.aiEndpoint
        }

        // Debounce DataStore writes to avoid disk I/O churn and cursor jitter
        LaunchedEffect(localKey) {
            delay(300)
            if (localKey != settings.aiApiKey) {
                viewModel.update { it.copy(aiApiKey = localKey) }
            }
        }
        LaunchedEffect(localModel) {
            delay(300)
            if (localModel != settings.aiModel) {
                viewModel.update { it.copy(aiModel = localModel) }
            }
        }
        LaunchedEffect(localEndpoint) {
            delay(300)
            if (localEndpoint != settings.aiEndpoint) {
                viewModel.update { it.copy(aiEndpoint = localEndpoint) }
            }
        }

        GroupLabel(stringResource(R.string.ai_api_key_label))
        Spacer(Modifier.height(4.dp))
        SerifuTextField(
            value = localKey,
            onValueChange = { localKey = it },
            placeholder = stringResource(R.string.ai_api_key_placeholder),
            modifier = Modifier.onFocusChanged { keyFocus = it.isFocused },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (localKey.isNotEmpty()) {
                        IconButton(onClick = {
                            localKey = ""
                            viewModel.update { it.copy(aiApiKey = "") }
                        }) {
                            Icon(
                                painter = painterResource(SerifuIcons.Close),
                                contentDescription = "Clear key",
                                tint = c.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painter = painterResource(if (showPassword) SerifuIcons.EyeOff else SerifuIcons.Eye),
                            contentDescription = if (showPassword) "Hide key" else "Show key",
                            tint = c.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        GroupLabel(stringResource(R.string.ai_model_label))
        Spacer(Modifier.height(4.dp))
        SerifuTextField(
            value = localModel,
            onValueChange = { localModel = it },
            placeholder = stringResource(R.string.ai_model_placeholder),
            modifier = Modifier.onFocusChanged { modelFocus = it.isFocused },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            trailingIcon = if (localModel.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        localModel = ""
                        viewModel.update { it.copy(aiModel = "") }
                    }) {
                        Icon(
                            painter = painterResource(SerifuIcons.Close),
                            contentDescription = "Clear model",
                            tint = c.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else null,
        )
        Spacer(Modifier.height(10.dp))

        GroupLabel(stringResource(R.string.ai_endpoint_label))
        Spacer(Modifier.height(4.dp))
        SerifuTextField(
            value = localEndpoint,
            onValueChange = { localEndpoint = it },
            placeholder = stringResource(R.string.ai_endpoint_placeholder),
            modifier = Modifier.onFocusChanged { endpointFocus = it.isFocused },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            trailingIcon = if (localEndpoint.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        localEndpoint = ""
                        viewModel.update { it.copy(aiEndpoint = "") }
                    }) {
                        Icon(
                            painter = painterResource(SerifuIcons.Close),
                            contentDescription = "Clear endpoint",
                            tint = c.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else null,
        )

        SectionTitle(stringResource(R.string.engine_mlkit))
        packs.forEachIndexed { index, pack ->
            SettingLine(
                label = pack.name,
                value = stringResource(
                    if (pack.code in downloaded) R.string.pack_on_phone else R.string.pack_missing,
                ),
                showChevron = false,
                showHairline = index < packs.lastIndex,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}


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
        Spacer(Modifier.height(10.dp))

        GroupLabel(stringResource(R.string.reasoning_effort_label))
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReasoningEffort.entries.forEach { effort ->
                Pill(
                    text = effort.label,
                    selected = settings.reasoningEffort == effort,
                    onClick = { viewModel.update { it.copy(reasoningEffort = effort) } },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.reasoning_effort_desc),
            style = Serifu.type.caption,
            color = c.textSecondary,
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
            color = c.primaryText,
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

        SectionTitle(stringResource(R.string.timings))
        Text(
            text = stringResource(
                R.string.timing_total,
                timings.totalMs,
                timings.cacheResult.ifEmpty { stringResource(R.string.timing_cache_miss) },
            ),
            style = Serifu.type.monoSmall,
            color = c.text,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = stringResource(R.string.timing_capture, timings.captureMs, timings.convertMs),
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = stringResource(R.string.timing_fingerprint, timings.fingerprintMs, timings.ocrMs),
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = stringResource(R.string.timing_layout, timings.clusterMs, timings.layoutMs),
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = stringResource(R.string.timing_mlkit, timings.translateMlKitMs),
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        if (timings.selfDiscarded > 0) {
            Text(
                text = stringResource(R.string.timing_self, timings.selfDiscarded),
                style = Serifu.type.monoSmall,
                color = c.primaryText,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(
            text = stringResource(R.string.open_test_page),
            onClick = {
                context.startActivity(Intent(context, TestPageActivity::class.java))
            },
            style = ActionStyle.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Serifu.colors
    var activeLicense by remember { mutableStateOf<String?>(null) }
    val version = remember(context.packageName) { context.versionName() }
    val licenses = remember { context.assets.list("licenses")?.sorted().orEmpty() }

    if (activeLicense != null) {
        val licenseName = activeLicense!!
        val content = remember(licenseName) {
            try {
                context.assets.open("licenses/$licenseName").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "Unable to load license: ${e.message}"
            }
        }
        AlertDialog(
            onDismissRequest = { activeLicense = null },
            confirmButton = {
                ActionButton(stringResource(R.string.close), onClick = { activeLicense = null }, style = ActionStyle.Primary)
            },
            title = { Text(licenseLabel(licenseName), style = Serifu.type.title, color = c.text) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(content, style = Serifu.type.monoSmall, color = c.textSecondary)
                }
            },
            shape = RoundedCornerShape(Serifu.radiusSurface),
            containerColor = c.surface,
            tonalElevation = 0.dp,
        )
    }

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
        BrandLockup()
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
            SettingLine(
                label = licenseLabel(name),
                showChevron = true,
                showHairline = true,
                onClick = { activeLicense = name },
            )
        }

        SectionTitle(stringResource(R.string.developer_label))
        SettingLine(
            label = stringResource(R.string.developer_github_label),
            value = stringResource(R.string.developer_github_value),
            showChevron = true,
            showHairline = true,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/walsoup"))
                runCatching { context.startActivity(intent) }
            },
        )
        SettingLine(
            label = stringResource(R.string.source_code_label),
            value = stringResource(R.string.source_code_value),
            showChevron = true,
            showHairline = false,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/walsoup/Serifu"))
                runCatching { context.startActivity(intent) }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun licenseLabel(file: String): String = when (file) {
    "OFL-AtkinsonHyperlegible.txt" -> "Atkinson Hyperlegible"
    "OFL-Bangers.txt" -> "Bangers"
    "OFL-ComicNeue.txt" -> "Comic Neue"
    "OFL-DelaGothicOne.txt" -> "Dela Gothic One"
    "OFL-PatrickHand.txt" -> "Patrick Hand"
    "mlkit-terms.txt" -> "ML Kit"
    else -> file.substringBeforeLast('.')
}
