package app.serifu.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.LinenDimens
import app.serifu.ui.testpage.TestPageActivity
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.serifu.R
import app.serifu.SerifuApp
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.PillSide
import app.serifu.core.model.QualityMode
import app.serifu.core.model.SfxMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TargetLanguage
import androidx.compose.foundation.verticalScroll
import app.serifu.ml.LanguageModelManager
import app.serifu.ml.LanguagePack
import kotlinx.coroutines.launch
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
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    var selectedPacks by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
    }

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

        SectionTitle("Offline Language Packs")
        Text(
            text = "Select multiple language packs to download together for instant offline translation. Each ML Kit pack is ~30 MB.",
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Quick multi-selection filters
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val allCodes = remember { LanguageModelManager.ALL_PACKS.map { it.code }.toSet() }
            val uninstalledCodes = remember(downloadedCodes) { allCodes - downloadedCodes }
            Pill(
                text = "Select uninstalled (${uninstalledCodes.size})",
                selected = selectedPacks.isNotEmpty() && selectedPacks == uninstalledCodes,
                onClick = {
                    selectedPacks = if (selectedPacks == uninstalledCodes) emptySet() else uninstalledCodes
                },
            )
            if (selectedPacks.isNotEmpty()) {
                Pill(
                    text = "Clear (${selectedPacks.size})",
                    selected = false,
                    onClick = { selectedPacks = emptySet() },
                )
            }
        }

        // Batch Action Panel
        AnimatedVisibility(
            visible = selectedPacks.isNotEmpty(),
            enter = fadeIn(tween(150)) + expandVertically(),
            exit = fadeOut(tween(100)) + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.5.dp, c.primary, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "${selectedPacks.size} packs selected (~${selectedPacks.size * 30} MB)",
                    style = Serifu.type.body.copy(fontWeight = FontWeight.Bold),
                    color = c.text,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val notDownloadedInSelection = selectedPacks.filter { it !in downloadedCodes }.toSet()
                    if (notDownloadedInSelection.isNotEmpty()) {
                        ActionButton(
                            text = "Download Selected (${notDownloadedInSelection.size})",
                            onClick = {
                                coroutineScope.launch {
                                    LanguageModelManager.downloadPacks(notDownloadedInSelection, settings.wifiOnlyDownloads)
                                    selectedPacks = emptySet()
                                }
                            },
                            style = ActionStyle.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val downloadedInSelection = selectedPacks.filter { it in downloadedCodes }.toSet()
                    if (downloadedInSelection.isNotEmpty()) {
                        ActionButton(
                            text = "Delete Selected (${downloadedInSelection.size})",
                            onClick = {
                                coroutineScope.launch {
                                    LanguageModelManager.deletePacks(downloadedInSelection)
                                    selectedPacks = emptySet()
                                }
                            },
                            style = ActionStyle.Ghost,
                        )
                    }
                }
            }
        }

        Text("SOURCE SCRIPTS", style = Serifu.type.overline, color = c.textSecondary, modifier = Modifier.padding(vertical = 4.dp))
        LanguageModelManager.SOURCE_PACKS.forEach { pack ->
            LanguagePackRow(
                pack = pack,
                isSelected = pack.code in selectedPacks,
                isDownloaded = pack.code in downloadedCodes,
                isInProgress = pack.code in inProgressCodes,
                onToggleSelect = {
                    selectedPacks = if (pack.code in selectedPacks) selectedPacks - pack.code else selectedPacks + pack.code
                },
                onDownload = {
                    coroutineScope.launch {
                        LanguageModelManager.downloadPacks(setOf(pack.code), settings.wifiOnlyDownloads)
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        LanguageModelManager.deletePack(pack.code)
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("TARGET LANGUAGES", style = Serifu.type.overline, color = c.textSecondary, modifier = Modifier.padding(vertical = 4.dp))
        LanguageModelManager.POPULAR_TARGET_PACKS.forEach { pack ->
            LanguagePackRow(
                pack = pack,
                isSelected = pack.code in selectedPacks,
                isDownloaded = pack.code in downloadedCodes,
                isInProgress = pack.code in inProgressCodes,
                onToggleSelect = {
                    selectedPacks = if (pack.code in selectedPacks) selectedPacks - pack.code else selectedPacks + pack.code
                },
                onDownload = {
                    coroutineScope.launch {
                        LanguageModelManager.downloadPacks(setOf(pack.code), settings.wifiOnlyDownloads)
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        LanguageModelManager.deletePack(pack.code)
                    }
                },
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
private fun LanguagePackRow(
    pack: LanguagePack,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isInProgress: Boolean,
    onToggleSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val c = Serifu.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable {
                if (Build.VERSION.SDK_INT >= 34) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onToggleSelect()
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = {
                if (Build.VERSION.SDK_INT >= 34) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onToggleSelect()
            },
            colors = CheckboxDefaults.colors(
                checkedColor = c.primary,
                checkmarkColor = c.onPrimary,
                uncheckedColor = c.inactive,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("${pack.name} · ${pack.nativeName}", style = Serifu.type.body, color = c.text)
            val statusText = when {
                isInProgress -> "Downloading…"
                isDownloaded -> "Downloaded (${pack.sizeMb} MB) · Ready offline"
                else -> "Not downloaded (${pack.sizeMb} MB)"
            }
            val statusColor = when {
                isInProgress -> c.primary
                isDownloaded -> c.primary
                else -> c.textSecondary
            }
            Text(statusText, style = Serifu.type.caption, color = statusColor)
        }
        Spacer(Modifier.width(8.dp))
        if (isInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = c.primary,
            )
        } else if (isDownloaded) {
            Pill(
                text = "Delete",
                selected = false,
                onClick = onDelete,
            )
        } else {
            Pill(
                text = "Download",
                selected = false,
                onClick = onDownload,
            )
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
fun SelectableEngineCard(
    title: String,
    subtitle: String,
    description: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val c = Serifu.colors

    val borderColor by animateColorAsState(
        targetValue = if (selected) c.primary else c.hairline,
        animationSpec = tween(durationMillis = 200),
        label = "engine_card_border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) c.surfaceAlt else c.surface,
        animationSpec = tween(durationMillis = 200),
        label = "engine_card_bg",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable {
                if (Build.VERSION.SDK_INT >= 34) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onClick()
            }
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Serifu.type.title, color = c.text)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(c.container)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(badge, style = Serifu.type.monoTiny, color = c.primary)
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(c.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = c.onPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "ACTIVE",
                            style = Serifu.type.monoTiny.copy(fontWeight = FontWeight.Bold),
                            color = c.onPrimary,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .border(1.dp, c.inactive, RoundedCornerShape(100))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "TAP TO SELECT",
                        style = Serifu.type.monoTiny,
                        color = c.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = Serifu.type.monoSmall, color = if (selected) c.primary else c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(description, style = Serifu.type.bodySecondary, color = c.textSecondary)

        if (footer != null) {
            Spacer(Modifier.height(12.dp))
            footer()
        }
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

        SectionTitle("Active Translation Engine")
        Text(
            text = "Select your active engine. Tap any engine to switch immediately.",
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Card 1: On-Device ML Kit
        SelectableEngineCard(
            title = stringResource(R.string.engine_mlkit),
            subtitle = "Instant · ~300ms · Lightweight neural models",
            description = "Fast offline neural translation using Google ML Kit. Translates dialogue and captions with zero wait and minimal battery drain.",
            selected = settings.quality == QualityMode.FAST,
            onClick = { viewModel.update { it.copy(quality = QualityMode.FAST) } },
        )

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

        SectionTitle(stringResource(R.string.diagnostics))
        Text(
            text = "Total: ${timings.totalMs} ms · Cache: ${timings.cacheResult.ifEmpty { "miss" }}",
            style = Serifu.type.monoSmall,
            color = c.text,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = "Capture: ${timings.captureMs} ms · Convert: ${timings.convertMs} ms",
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = "Fingerprint: ${timings.fingerprintMs} ms · OCR: ${timings.ocrMs} ms",
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = "Cluster: ${timings.clusterMs} ms · Layout: ${timings.layoutMs} ms",
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = "ML Kit: ${timings.translateMlKitMs} ms",
            style = Serifu.type.monoSmall,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        if (timings.selfDiscarded > 0) {
            Text(
                text = "Self-detection discarded: ${timings.selfDiscarded}",
                style = Serifu.type.monoSmall,
                color = c.primary,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(
            text = "Open Sample Test Reader",
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
                ActionButton("Close", onClick = { activeLicense = null }, style = ActionStyle.Primary)
            },
            title = { Text(licenseName, style = Serifu.type.title, color = c.text) },
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
            shape = RoundedCornerShape(LinenDimens.DialogRadius),
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
            SettingLine(
                label = name,
                showChevron = true,
                showHairline = true,
                onClick = { activeLicense = name },
            )
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
