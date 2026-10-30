package app.serifu.ui.testpage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.serifu.SerifuApp
import app.serifu.core.coordinator.Event
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.serifu.service.EventRouter
import app.serifu.service.SerifuAccessibilityService
import app.serifu.settings.Settings
import app.serifu.ui.design.Pill
import app.serifu.ui.design.Serifu
import app.serifu.ui.theme.SerifuTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TestPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val startBottom = intent.getBooleanExtra(EXTRA_START_BOTTOM, false)
        val initialLanguageCode = intent.getStringExtra(EXTRA_LANGUAGE)
        val initialLang = when (initialLanguageCode) {
            "ko" -> SourceLanguage.KO
            "zh" -> SourceLanguage.ZH
            else -> SourceLanguage.JA
        }

        setContent {
            SerifuTheme {
                ReaderPage(
                    startBottom = startBottom,
                    initialLang = initialLang,
                    onBack = { finish() },
                    onEnableAccessibility = { SerifuApp.openAccessibilitySettings(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? SerifuApp)?.let { app ->
            app.checkAccessibilityService()
            app.readerPresence.value = EventRouter.State(
                foregroundPackage = packageName,
                obscured = false,
                reader = true,
            )
        }
        val service = SerifuAccessibilityService.instance
        if (service != null && service.coordinator != null) {
            val serifuApp = application as? SerifuApp
            service.coordinator?.pushSettings(serifuApp?.settingsRepository?.settings?.value ?: Settings())
            service.coordinator?.onScreenOn()
            service.coordinator?.send(Event.ForegroundChanged(isReader = true))
        }
    }

    override fun onPause() {
        super.onPause()
        (application as? SerifuApp)?.let { app ->
            app.readerPresence.value = app.readerPresence.value.copy(reader = false)
        }
    }

    companion object {
        const val EXTRA_START_BOTTOM = "serifu.startBottom"
        const val EXTRA_LANGUAGE = "serifu.language"
    }
}

@Composable
private fun ReaderPage(
    startBottom: Boolean,
    initialLang: SourceLanguage,
    onBack: () -> Unit,
    onEnableAccessibility: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? SerifuApp
    val scope = rememberCoroutineScope()
    val settingsState by (app?.settingsRepository?.settings ?: MutableStateFlow(Settings()))
        .collectAsState(initial = Settings())
    val settings = settingsState ?: Settings()
    val isServiceActive by (app?.isServiceActive ?: MutableStateFlow(true))
        .collectAsState(initial = true)
    val selectedLanguage = settings.sourceLanguage
    var showToolbar by remember { mutableStateOf(true) }
    val c = Serifu.colors
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        app?.settingsRepository?.update {
            val base = if (it.mode == TriggerMode.OFF) it.copy(mode = TriggerMode.MANUAL) else it
            base.copy(debugOverlay = false, sourceLanguage = initialLang)
        }
    }
    if (startBottom) {
        LaunchedEffect(Unit) {
            repeat(20) {
                kotlinx.coroutines.delay(200)
                val max = scroll.maxValue
                if (max > 0) {
                    scroll.scrollTo(max)
                    return@LaunchedEffect
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        AnimatedVisibility(
            visible = showToolbar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = c.text,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = chapterTitle(selectedLanguage),
                            style = Serifu.type.headline.copy(fontSize = 17.sp),
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = selectedLanguage.englishName,
                            style = Serifu.type.caption,
                            color = c.textSecondary,
                            maxLines = 1,
                        )
                    }
                    Pill(
                        text = if (isServiceActive) "Translate" else "Enable",
                        selected = true,
                        onClick = {
                            val service = SerifuAccessibilityService.instance
                            if (isServiceActive && service?.coordinator != null) {
                                service.coordinator?.onPillTap()
                            } else {
                                onEnableAccessibility()
                            }
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = "Boxes",
                        selected = settings.debugOverlay,
                        onClick = {
                            scope.launch {
                                app?.settingsRepository?.update { it.copy(debugOverlay = !it.debugOverlay) }
                            }
                        },
                    )
                }

                if (!isServiceActive) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Accessibility is off, so this page stays in the original.",
                        style = Serifu.type.caption,
                        color = c.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceLanguage.entries.forEach { lang ->
                        Pill(
                            text = lang.tag.uppercase(),
                            selected = selectedLanguage == lang,
                            onClick = {
                                app?.let { sApp ->
                                    scope.launch {
                                        scroll.scrollTo(0)
                                        sApp.settingsRepository.update { it.copy(sourceLanguage = lang) }
                                        kotlinx.coroutines.delay(200)
                                        SerifuAccessibilityService.instance?.coordinator?.onPillTap()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showToolbar = !showToolbar }
                .verticalScroll(scroll),
        ) {
            Crossfade(targetState = selectedLanguage, label = "sample_page") { lang ->
                SamplePage(language = lang, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun chapterTitle(language: SourceLanguage): String = when (language) {
    SourceLanguage.JA -> "Rooftop note"
    SourceLanguage.KO -> "Sunset letter"
    SourceLanguage.ZH -> "Clubroom secret"
}
