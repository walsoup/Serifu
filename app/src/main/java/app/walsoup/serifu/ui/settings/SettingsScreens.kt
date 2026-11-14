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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.walsoup.serifu.ml.isGeminiEndpoint
import app.walsoup.serifu.ui.components.SerifuIcons
import app.serifu.core.model.ModelVariant
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.QualityMode
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.translate.ModelRoute
import app.serifu.bergamot.BergamotNative
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
import app.walsoup.serifu.ml.BergamotInstalledModel
import app.walsoup.serifu.ml.DownloadError
import app.walsoup.serifu.ui.components.label
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.ml.LanguagePack
import kotlinx.coroutines.launch
import app.walsoup.serifu.overlay.SerifuFonts
import app.walsoup.serifu.settings.ActiveEngine
import app.walsoup.serifu.settings.KnownReaders
import app.walsoup.serifu.ui.components.ActiveModelStatusCard
import app.walsoup.serifu.ui.components.LaunchableApp
import app.walsoup.serifu.ui.components.MangaPanel
import app.walsoup.serifu.ui.components.SpeechBubblePreview
import app.walsoup.serifu.ui.components.rememberBergamotRoute
import app.walsoup.serifu.ui.components.rememberLaunchableApps
import app.walsoup.serifu.ui.home.HomeViewModel
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.BrandLockup
import app.walsoup.serifu.ui.design.SerifuConfirmDialog
import app.walsoup.serifu.ui.design.SerifuProgressBar
import app.walsoup.serifu.ui.design.SerifuSlider
import app.walsoup.serifu.ui.design.StatusBadge
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
import app.walsoup.serifu.ui.design.SerifuSwitch
import app.walsoup.serifu.ui.design.SerifuTextField
import app.walsoup.serifu.ui.design.SettingLine
import app.walsoup.serifu.ui.design.SwitchLine
import app.walsoup.serifu.ui.design.TopBar
import app.walsoup.serifu.ui.versionName

@Composable
fun LanguagesScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenEngine: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    val mlKitErrors by LanguageModelManager.errors.collectAsStateWithLifecycle()
    val bergamotInstalled by app.bergamotModels.installed.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<LanguagePack?>(null) }
    var pendingBergamotDelete by remember { mutableStateOf<BergamotInstalledModel?>(null) }
    var pickingTarget by rememberSaveable { mutableStateOf(false) }
    var targetQuery by rememberSaveable { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
        app.bergamotModels.refreshInstalled()
    }

    val needed = remember(settings.sourceLanguage, settings.targetLanguage) {
        setOf(settings.sourceLanguage.tag, settings.targetLanguage)
    }
    val extraTargetPack = remember(settings.targetLanguage) {
        if (LanguageModelManager.ALL_PACKS.any { it.code == settings.targetLanguage }) {
            null
        } else {
            val lang = TargetLanguage.fromTag(settings.targetLanguage)
            LanguagePack(code = lang.tag, name = lang.englishName, nativeName = lang.englishName, isSource = false)
        }
    }
    val allManageablePacks = remember(extraTargetPack) {
        if (extraTargetPack != null) {
            LanguageModelManager.ALL_PACKS + extraTargetPack
        } else {
            LanguageModelManager.ALL_PACKS
        }
    }
    val missing = allManageablePacks.filter { it.code !in downloadedCodes && it.code !in inProgressCodes }
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
    pendingBergamotDelete?.let { model ->
        SerifuConfirmDialog(
            title = stringResource(R.string.bergamot_delete_title, model.from, model.to),
            text = stringResource(R.string.bergamot_delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                app.bergamotModels.deletePair(model.from, model.to, model.architecture)
                pendingBergamotDelete = null
            },
            onDismiss = { pendingBergamotDelete = null },
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

        Spacer(Modifier.height(14.dp))
        ActiveModelStatusCard(
            settings = settings,
            onOpenEngine = onOpenEngine,
            onOpenLanguages = {},
            showReadyState = true,
        )

        SectionTitle(stringResource(R.string.source_label))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceLanguage.entries.forEach { lang ->
                val readyMark = if (lang.tag in downloadedCodes) " \u2713" else ""
                Pill(
                    text = "${lang.englishName}$readyMark",
                    selected = settings.sourceLanguage == lang,
                    onClick = { viewModel.update { it.copy(sourceLanguage = lang) } },
                )
            }
        }

        SectionTitle(stringResource(R.string.target_label))
        Hairline()
        SettingLine(
            label = targetName(settings.targetLanguage),
            value = if (pickingTarget) stringResource(R.string.close) else stringResource(R.string.target_change),
            emphasis = true,
            showChevron = !pickingTarget,
            showHairline = !pickingTarget,
            onClick = { pickingTarget = !pickingTarget },
        )
        AnimatedVisibility(
            visible = pickingTarget,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(
                    text = stringResource(R.string.languages_pivot),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                GroupLabel(stringResource(R.string.target_popular_languages))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LanguageModelManager.POPULAR_TARGET_PACKS.forEach { pack ->
                        Pill(
                            text = pack.name,
                            selected = settings.targetLanguage == pack.code,
                            onClick = {
                                viewModel.update { it.copy(targetLanguage = pack.code) }
                                pickingTarget = false
                                targetQuery = ""
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SerifuTextField(
                    value = targetQuery,
                    onValueChange = { targetQuery = it },
                    placeholder = stringResource(R.string.target_search_placeholder),
                    trailingIcon = if (targetQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { targetQuery = "" }) {
                                Icon(
                                    painter = painterResource(SerifuIcons.Close),
                                    contentDescription = stringResource(R.string.close),
                                    tint = c.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else null,
                )
                val filteredTargets = remember(targetQuery) {
                    if (targetQuery.isBlank()) {
                        TargetLanguage.all
                    } else {
                        TargetLanguage.all.filter {
                            it.englishName.contains(targetQuery, ignoreCase = true) ||
                                it.tag.contains(targetQuery, ignoreCase = true)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GroupLabel(stringResource(R.string.target_all_languages, TargetLanguage.all.size))
                Spacer(Modifier.height(4.dp))
                Hairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (filteredTargets.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_apps_match),
                            style = Serifu.type.caption,
                            color = c.textSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        filteredTargets.forEach { language ->
                            val selected = settings.targetLanguage == language.tag
                            SettingLine(
                                label = language.englishName,
                                value = if (selected) stringResource(R.string.active_value) else language.tag,
                                emphasis = selected,
                                mono = !selected,
                                showChevron = false,
                                onClick = {
                                    viewModel.update { it.copy(targetLanguage = language.tag) }
                                    pickingTarget = false
                                    targetQuery = ""
                                },
                            )
                        }
                    }
                }
            }
        }
        Hairline()

        SectionTitle(stringResource(R.string.packs_title))
        Text(
            text = stringResource(R.string.packs_overview_note),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Hairline()
        SwitchLine(
            label = stringResource(R.string.wifi_only),
            checked = settings.wifiOnlyDownloads,
            onCheckedChange = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
            subtitle = stringResource(R.string.wifi_subtitle),
        )
        Spacer(Modifier.height(14.dp))
        if (missingNeeded.isNotEmpty()) {
            ActionButton(
                text = stringResource(R.string.packs_download_needed, missingNeeded.size, missingNeeded.sumOf { it.sizeMb }),
                onClick = {
                    LanguageModelManager.startDownload(
                        app.appScope,
                        missingNeeded.map { it.code }.toSet(),
                        settings.wifiOnlyDownloads,
                        context,
                    )
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
                        LanguageModelManager.startDownload(
                            app.appScope,
                            missing.map { it.code }.toSet(),
                            settings.wifiOnlyDownloads,
                            context,
                        )
                    },
                    style = ActionStyle.Ghost,
                )
            }
        }

        GroupHeader(stringResource(R.string.source_packs))
        Hairline()
        LanguageModelManager.SOURCE_PACKS.forEach { pack ->
            LanguagePackRow(
                pack = pack,
                sourceName = settings.sourceLanguage.englishName,
                targetName = targetName(settings.targetLanguage),
                isNeeded = pack.code in needed,
                isDownloaded = pack.code in downloadedCodes,
                isInProgress = pack.code in inProgressCodes,
                error = mlKitErrors[pack.code],
                onDownload = {
                    LanguageModelManager.startDownload(
                        app.appScope,
                        setOf(pack.code),
                        settings.wifiOnlyDownloads,
                        context,
                    )
                },
                onDelete = { pendingDelete = pack },
                onCancel = { LanguageModelManager.cancelDownload(setOf(pack.code)) },
            )
        }

        GroupHeader(stringResource(R.string.target_packs))
        Hairline()
        val targetPackRows = remember(extraTargetPack) {
            if (extraTargetPack != null) {
                listOf(extraTargetPack) + LanguageModelManager.POPULAR_TARGET_PACKS
            } else {
                LanguageModelManager.POPULAR_TARGET_PACKS
            }
        }
        targetPackRows.forEach { pack ->
            LanguagePackRow(
                pack = pack,
                sourceName = settings.sourceLanguage.englishName,
                targetName = targetName(settings.targetLanguage),
                isNeeded = pack.code in needed,
                isDownloaded = pack.code in downloadedCodes,
                isInProgress = pack.code in inProgressCodes,
                error = mlKitErrors[pack.code],
                onDownload = {
                    LanguageModelManager.startDownload(
                        app.appScope,
                        setOf(pack.code),
                        settings.wifiOnlyDownloads,
                        context,
                    )
                },
                onDelete = { pendingDelete = pack },
                onCancel = { LanguageModelManager.cancelDownload(setOf(pack.code)) },
            )
        }

        SectionTitle(stringResource(R.string.bergamot_models_title))
        Text(
            text = stringResource(R.string.bergamot_models_body),
            style = Serifu.type.bodySecondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        if (bergamotInstalled.isEmpty()) {
            Text(
                text = stringResource(R.string.bergamot_none_installed),
                style = Serifu.type.caption,
                color = c.textSecondary,
            )
        } else {
            Hairline()
            bergamotInstalled.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.bergamot_model_row,
                                model.from,
                                model.to,
                                model.architecture,
                                model.sizeMb,
                            ),
                            style = Serifu.type.body,
                            color = c.text,
                        )
                        Text(
                            text = stringResource(R.string.pack_on_phone),
                            style = Serifu.type.caption,
                            color = c.textSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Pill(
                        text = stringResource(R.string.delete),
                        selected = false,
                        onClick = { pendingBergamotDelete = model },
                    )
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(10.dp))
        ActionButton(
            text = stringResource(R.string.packs_bergamot_cta),
            onClick = {
                viewModel.update {
                    it.copy(quality = QualityMode.FAST, onDeviceEngine = OnDeviceEngine.BERGAMOT)
                }
                onOpenEngine()
            },
            style = ActionStyle.Ghost,
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
    error: DownloadError?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Serifu.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (pack.nativeName == pack.name) pack.name else "${pack.name} \u00B7 ${pack.nativeName}",
                        style = Serifu.type.body,
                        color = c.text,
                    )
                    if (isNeeded && !isDownloaded && !isInProgress) {
                        StatusBadge(text = stringResource(R.string.packs_needed_badge), highlighted = true)
                    }
                }
                val (statusText, statusColor) = when {
                    isInProgress -> stringResource(R.string.pack_downloading) to c.primaryText
                    error != null -> error.label() to c.primaryText
                    isDownloaded -> stringResource(R.string.pack_ready, pack.sizeMb) to c.textSecondary
                    isNeeded -> stringResource(R.string.pack_needed, sourceName, targetName) to c.primaryText
                    else -> stringResource(R.string.pack_not_downloaded, pack.sizeMb) to c.textSecondary
                }
                Text(statusText, style = Serifu.type.caption, color = statusColor)
            }
            Spacer(Modifier.width(12.dp))
            when {
                isInProgress -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = c.primary,
                    )
                    Pill(
                        text = stringResource(R.string.download_cancel),
                        selected = false,
                        onClick = onCancel,
                    )
                }
                isDownloaded -> Pill(text = stringResource(R.string.packs_delete_action), selected = false, onClick = onDelete)
                error != null -> Pill(text = stringResource(R.string.download_retry), selected = true, onClick = onDownload)
                else -> Pill(text = stringResource(R.string.download), selected = isNeeded, onClick = onDownload)
            }
        }
        if (isInProgress) {
            SerifuProgressBar(progress = null, height = 3.dp)
            Spacer(Modifier.height(8.dp))
        }
        Hairline()
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
fun EngineScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenLanguages: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = Serifu.colors
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val bergamotModels = app.bergamotModels
    val bergamotInstalled by bergamotModels.installed.collectAsStateWithLifecycle()
    val bergamotReadyKeys by bergamotModels.readyKeys.collectAsStateWithLifecycle()
    var pendingBergamotDelete by remember { mutableStateOf<BergamotInstalledModel?>(null) }
    var confirmDeleteActiveRoute by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LanguageModelManager.refreshDownloaded()
        bergamotModels.refreshInstalled()
    }

    val active = settings.activeEngine
    val usingApi = active.usesApi
    val usingBergamot = active.kind == ActiveEngine.Kind.BERGAMOT
    val usingMlKit = active.kind == ActiveEngine.Kind.ML_KIT
    val sourceName = settings.sourceLanguage.englishName
    val targetLabel = targetName(settings.targetLanguage)

    val resolvedRoute = rememberBergamotRoute(
        bergamotModels = bergamotModels,
        sourceTag = settings.sourceLanguage.tag,
        targetTag = settings.targetLanguage,
        variant = settings.bergamotVariant,
    )
    val route = resolvedRoute.route
    val routeReady = remember(route, bergamotReadyKeys) {
        route != null && bergamotModels.isRouteReady(route)
    }

    pendingBergamotDelete?.let { model ->
        SerifuConfirmDialog(
            title = stringResource(R.string.bergamot_delete_title, model.from, model.to),
            text = stringResource(R.string.bergamot_delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                bergamotModels.deletePair(model.from, model.to, model.architecture)
                pendingBergamotDelete = null
            },
            onDismiss = { pendingBergamotDelete = null },
        )
    }

    if (confirmDeleteActiveRoute && route != null) {
        SerifuConfirmDialog(
            title = stringResource(R.string.bergamot_delete_title, sourceName, targetLabel),
            text = stringResource(R.string.bergamot_delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                when (route) {
                    is ModelRoute.Direct -> bergamotModels.deletePair(
                        route.model.from,
                        route.model.to,
                        route.model.architecture,
                    )
                    is ModelRoute.Pivot -> {
                        bergamotModels.deletePair(route.first.from, route.first.to, route.first.architecture)
                        bergamotModels.deletePair(route.second.from, route.second.to, route.second.architecture)
                    }
                }
                confirmDeleteActiveRoute = false
            },
            onDismiss = { confirmDeleteActiveRoute = false },
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
                text = stringResource(R.string.engine_mlkit_pill),
                selected = usingMlKit,
                onClick = {
                    viewModel.update {
                        it.copy(quality = QualityMode.FAST, onDeviceEngine = OnDeviceEngine.MLKIT)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = stringResource(R.string.engine_bergamot_pill),
                selected = usingBergamot,
                onClick = {
                    viewModel.update {
                        it.copy(quality = QualityMode.FAST, onDeviceEngine = OnDeviceEngine.BERGAMOT)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = stringResource(R.string.engine_api),
                selected = usingApi,
                onClick = {
                    viewModel.update { current ->
                        val nextQuality = if (current.quality == QualityMode.BEST_PROGRESSIVE) {
                            QualityMode.BEST_PROGRESSIVE
                        } else {
                            QualityMode.BEST_ONLY
                        }
                        current.copy(quality = nextQuality)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when {
                    usingBergamot -> R.string.engine_bergamot_desc
                    usingApi -> R.string.engine_api_desc
                    else -> R.string.engine_mlkit_desc
                },
            ),
            style = Serifu.type.caption,
            color = c.textSecondary,
        )

        if (usingMlKit) {
            SectionTitle(stringResource(R.string.engine_mlkit_pair_title))
            ActiveModelStatusCard(
                settings = settings,
                onOpenEngine = {},
                onOpenLanguages = onOpenLanguages,
                showReadyState = true,
            )
            Spacer(Modifier.height(14.dp))
            Hairline()
            SwitchLine(
                label = stringResource(R.string.wifi_only),
                checked = settings.wifiOnlyDownloads,
                onCheckedChange = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
                subtitle = stringResource(R.string.wifi_subtitle),
            )
            Spacer(Modifier.height(14.dp))
            ActionButton(
                text = stringResource(R.string.engine_manage_all_packs),
                onClick = onOpenLanguages,
                style = ActionStyle.Ghost,
            )
        }

        if (usingBergamot) {
            SectionTitle(stringResource(R.string.engine_bergamot_size_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    text = stringResource(R.string.engine_bergamot_lite),
                    selected = settings.bergamotVariant == ModelVariant.LITE,
                    onClick = { viewModel.update { it.copy(bergamotVariant = ModelVariant.LITE) } },
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    text = stringResource(R.string.engine_bergamot_full),
                    selected = settings.bergamotVariant == ModelVariant.FULL,
                    onClick = { viewModel.update { it.copy(bergamotVariant = ModelVariant.FULL) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            val sizeMb = route?.sizeMb ?: 0
            Text(
                text = stringResource(
                    if (settings.bergamotVariant == ModelVariant.LITE) {
                        R.string.engine_bergamot_lite_desc
                    } else {
                        R.string.engine_bergamot_full_desc
                    },
                    sizeMb.coerceAtLeast(1),
                ),
                style = Serifu.type.caption,
                color = c.textSecondary,
            )
            if (BergamotNative.available && route != null) {
                val routeArch = when (route) {
                    is ModelRoute.Direct -> route.model.architecture
                    is ModelRoute.Pivot -> route.first.architecture
                }
                val archMismatchMessage = when {
                    settings.bergamotVariant == ModelVariant.FULL && routeArch == "tiny" ->
                        stringResource(R.string.engine_bergamot_full_unavailable, sourceName, targetLabel)
                    settings.bergamotVariant == ModelVariant.LITE && routeArch != "tiny" ->
                        stringResource(R.string.engine_bergamot_lite_unavailable, sourceName, targetLabel)
                    else -> null
                }
                if (archMismatchMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = archMismatchMessage,
                        style = Serifu.type.caption,
                        color = c.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            ActiveModelStatusCard(
                settings = settings,
                onOpenEngine = {},
                onOpenLanguages = onOpenLanguages,
                showReadyState = true,
                resolvedRoute = resolvedRoute,
            )
            if (routeReady && route != null) {
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = stringResource(R.string.engine_bergamot_delete),
                    onClick = { confirmDeleteActiveRoute = true },
                    style = ActionStyle.Ghost,
                )
            }

            Spacer(Modifier.height(16.dp))
            Hairline()
            SwitchLine(
                label = stringResource(R.string.wifi_only),
                checked = settings.wifiOnlyDownloads,
                onCheckedChange = { checked -> viewModel.update { it.copy(wifiOnlyDownloads = checked) } },
                subtitle = stringResource(R.string.wifi_subtitle),
            )

            SectionTitle(stringResource(R.string.engine_bergamot_installed_section))
            if (bergamotInstalled.isEmpty()) {
                Text(
                    text = stringResource(R.string.bergamot_none_installed),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
            } else {
                Hairline()
                bergamotInstalled.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.bergamot_model_row,
                                    model.from,
                                    model.to,
                                    model.architecture,
                                    model.sizeMb,
                                ),
                                style = Serifu.type.body,
                                color = c.text,
                            )
                            Text(
                                text = stringResource(R.string.pack_on_phone),
                                style = Serifu.type.caption,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Pill(
                            text = stringResource(R.string.delete),
                            selected = false,
                            onClick = { pendingBergamotDelete = model },
                        )
                    }
                    Hairline()
                }
            }
        }

        if (usingApi) {
            SectionTitle(stringResource(R.string.engine_api_mode_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    text = stringResource(R.string.engine_api_only_pill),
                    selected = settings.quality == QualityMode.BEST_ONLY,
                    onClick = { viewModel.update { it.copy(quality = QualityMode.BEST_ONLY) } },
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    text = stringResource(R.string.engine_api_progressive_pill),
                    selected = settings.quality == QualityMode.BEST_PROGRESSIVE,
                    onClick = { viewModel.update { it.copy(quality = QualityMode.BEST_PROGRESSIVE) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (settings.quality == QualityMode.BEST_PROGRESSIVE) {
                        R.string.engine_api_progressive_desc
                    } else {
                        R.string.engine_api_only_desc
                    },
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

            LaunchedEffect(settings.aiApiKey) {
                if (!keyFocus) localKey = settings.aiApiKey
            }
            LaunchedEffect(settings.aiModel) {
                if (!modelFocus) localModel = settings.aiModel
            }
            LaunchedEffect(settings.aiEndpoint) {
                if (!endpointFocus) localEndpoint = settings.aiEndpoint
            }

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

            GroupLabel(stringResource(R.string.engine_api_presets_label))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    text = stringResource(R.string.engine_api_preset_openai),
                    selected = localEndpoint.contains("api.openai.com", ignoreCase = true) || localEndpoint.isBlank(),
                    onClick = {
                        localEndpoint = "https://api.openai.com/v1/chat/completions"
                        if (localModel.isBlank() || localModel.contains("llama", ignoreCase = true) || localModel.contains("/")) {
                            localModel = "gpt-4o-mini"
                        }
                    },
                )
                Pill(
                    text = stringResource(R.string.engine_api_preset_gemini),
                    selected = isGeminiEndpoint(localEndpoint),
                    onClick = {
                        localEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
                    },
                )
                Pill(
                    text = stringResource(R.string.engine_api_preset_openrouter),
                    selected = localEndpoint.contains("openrouter.ai", ignoreCase = true),
                    onClick = {
                        localEndpoint = "https://openrouter.ai/api/v1/chat/completions"
                        localModel = "google/gemini-2.5-flash"
                    },
                )
                Pill(
                    text = stringResource(R.string.engine_api_preset_ollama),
                    selected = localEndpoint.contains("127.0.0.1") || localEndpoint.contains("localhost"),
                    onClick = {
                        localEndpoint = "http://127.0.0.1:11434/v1/chat/completions"
                        localModel = "qwen2.5:7b"
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

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
                                    contentDescription = stringResource(R.string.cd_clear_key),
                                    tint = c.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(if (showPassword) SerifuIcons.EyeOff else SerifuIcons.Eye),
                                contentDescription = stringResource(
                                    if (showPassword) R.string.cd_hide_key else R.string.cd_show_key,
                                ),
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
                                contentDescription = stringResource(R.string.cd_clear_model),
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
                                contentDescription = stringResource(R.string.cd_clear_endpoint),
                                tint = c.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else null,
            )
            if (isGeminiEndpoint(localEndpoint)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ai_gemini_model_note),
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))
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

            if (active.kind == ActiveEngine.Kind.API_PROGRESSIVE) {
                Spacer(Modifier.height(14.dp))
                ActiveModelStatusCard(
                    settings = settings,
                    onOpenEngine = {},
                    onOpenLanguages = onOpenLanguages,
                    showReadyState = false,
                )
            }
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
        Text(
            text = stringResource(R.string.timing_bergamot, timings.translateBergamotMs),
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
    "bergamot-mpl-2.0.txt" -> "Bergamot Translator (MPL-2.0)"
    "mozilla-translation-models.txt" -> "Mozilla Translation Models"
    else -> file.substringBeforeLast('.')
}
