package com.walsoup.linen.showcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.walsoup.linen.GroupLabel
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenTheme
import com.walsoup.linen.PageHeader
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SettingLine
import com.walsoup.linen.examples.ColorsExample
import com.walsoup.linen.examples.ConfirmDialogExample
import com.walsoup.linen.examples.ControlsExample
import com.walsoup.linen.examples.EditorExample
import com.walsoup.linen.examples.ListScreenExample
import com.walsoup.linen.examples.LiveRecordingExample
import com.walsoup.linen.examples.NoteDetailExample
import com.walsoup.linen.examples.OnboardingExample
import com.walsoup.linen.examples.PickerSheetExample
import com.walsoup.linen.examples.ReviewSheetExample
import com.walsoup.linen.examples.SearchExample
import com.walsoup.linen.examples.SettingsScreenExample
import com.walsoup.linen.examples.SnackbarExample
import com.walsoup.linen.examples.TypeExample

private class Screen(
    val id: String,
    val group: String,
    val title: String,
    val subtitle: String,
    val edgeToEdgeBottom: Boolean = false,
    val content: @Composable () -> Unit,
)

private val screens = listOf(
    Screen("list", "Screens", "List", "Header, grouped rows, record dock, tabs") { ListScreenExample() },
    Screen("detail", "Screens", "Detail", "Back bar, player, read-only facts") { NoteDetailExample() },
    Screen("settings", "Screens", "Settings", "Switch rows, value rows, pills") { SettingsScreenExample() },
    Screen("search", "Screens", "Search", "Text field, filter pills, results") { SearchExample() },
    Screen("editor", "Screens", "Editor", "Form fields, options, pinned actions") { EditorExample() },
    Screen("onboarding", "Screens", "Onboarding", "Numbered steps, one next action") { OnboardingExample() },
    Screen("live", "Recording", "Live recording", "Hero timer, live waveform, stop") { LiveRecordingExample() },
    Screen("review", "Recording", "Review sheet", "Timer, waveform, send", edgeToEdgeBottom = true) { ReviewSheetExample() },
    Screen("dialog", "Overlays", "Confirm dialog", "Destructive confirmation") { ConfirmDialogExample() },
    Screen("sheet", "Overlays", "Picker sheet", "Handle, switch rows, done", edgeToEdgeBottom = true) { PickerSheetExample() },
    Screen("snackbar", "Overlays", "Snackbar", "Confirmation with undo") { SnackbarExample() },
    Screen("controls", "Tokens", "Controls", "Every button style, empty state") { ControlsExample() },
    Screen("colors", "Tokens", "Colors", "Every color role") { ColorsExample() },
    Screen("type", "Tokens", "Type", "The type scale") { TypeExample() },
)

/** Browses the Linen examples. `-e screen <id>` opens one directly (used for CI screenshots). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.getStringExtra("screen")
        setContent {
            LinenTheme {
                var id by rememberSaveable { mutableStateOf(initial?.takeIf { s -> screens.any { it.id == s } }) }
                val screen = screens.firstOrNull { it.id == id }
                BackHandler(enabled = screen != null) { id = null }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Linen.colors.bg)
                        .then(if (screen?.edgeToEdgeBottom == true) Modifier.statusBarsPadding() else Modifier.systemBarsPadding())
                ) {
                    if (screen != null) screen.content() else Catalog(onOpen = { id = it })
                }
            }
        }
    }
}

@Composable
private fun Catalog(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        PageHeader(overline = "Design system", title = "Linen", lede = "Reference screens built only from Linen components.")
        screens.groupBy { it.group }.forEach { (group, items) ->
            GroupLabel(group)
            items.forEachIndexed { i, s ->
                SettingLine(label = s.title, subtitle = s.subtitle, showHairline = i < items.lastIndex, onClick = { onOpen(s.id) })
            }
        }
    }
}
