package com.walsoup.linen.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.GroupLabel
import com.walsoup.linen.Hairline
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenTextField
import com.walsoup.linen.Pill
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SectionTitle
import com.walsoup.linen.SettingLine
import com.walsoup.linen.SwitchLine
import com.walsoup.linen.TopBar
import com.walsoup.linen.TrackRow
import com.walsoup.linen.Wordmark

/** First run: wordmark, numbered setup steps, the next action at the bottom with a way out. */
@Composable
fun OnboardingExample() {
    val c = Linen.colors
    val steps = listOf(
        Triple("Microphone", "So Ditto can hear you", false),
        Triple("Draw over apps", "For the floating record bubble", true),
        Triple("Accessibility", "To paste into the chat for you", false),
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = ScreenMargin)
    ) {
        Spacer(Modifier.height(56.dp))
        Wordmark("Ditto")
        Spacer(Modifier.height(10.dp))
        Text(
            "Voice notes that paste themselves. Three permissions first, then you're set.",
            style = Linen.type.body,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(28.dp))
        steps.forEachIndexed { i, (title, subtitle, done) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "0${i + 1}",
                    style = Linen.type.monoSmall,
                    color = if (done) c.primary else c.muted,
                    modifier = Modifier.width(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = Linen.type.body, color = c.text)
                    Text(subtitle, style = Linen.type.caption, color = c.textSecondary)
                }
                if (done) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
            if (i < steps.lastIndex) Hairline()
        }
        Spacer(Modifier.weight(1f))
        ActionButton("Allow microphone", onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ActionButton("Not now", onClick = {}, style = ActionStyle.Ghost, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}

/** Search: back bar, field, filter pills, results as track rows. */
@Composable
fun SearchExample() {
    var query by remember { mutableStateOf("flat") }
    var filter by remember { mutableStateOf("All") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Linen.colors.bg)
            .padding(horizontal = ScreenMargin)
    ) {
        TopBar(onBack = {}, title = "Search")
        Spacer(Modifier.height(4.dp))
        LinenTextField(query, onValueChange = { query = it }, placeholder = "Search notes")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Today", "Sent", "Starred").forEach {
                Pill(text = it, selected = it == filter, onClick = { filter = it })
            }
        }
        GroupLabel("2 results")
        TrackRow(index = 1, title = "Call back about the flat", subtitle = "Today, 08:40", durationMs = 71_000)
        TrackRow(index = 2, title = "Flat viewing questions", subtitle = "3 October", durationMs = 35_000, showHairline = false)
    }
}

/** Form: labelled fields, option rows, Cancel and Save pinned in the thumb zone. */
@Composable
fun EditorExample() {
    var title by remember { mutableStateOf("Groceries for the weekend") }
    var notes by remember { mutableStateOf("Oat milk, lemons, the good bread from the market.") }
    var pinned by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Linen.colors.bg)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin)
        ) {
            TopBar(onBack = {}, title = "Edit note")
            Spacer(Modifier.height(4.dp))
            LinenTextField(title, onValueChange = { title = it }, label = "Title")
            Spacer(Modifier.height(12.dp))
            LinenTextField(notes, onValueChange = { notes = it }, label = "Notes", singleLine = false, minLines = 4)
            SectionTitle("Options")
            SwitchLine("Pin to top", checked = pinned, onCheckedChange = { pinned = it })
            SettingLine("Folder", value = "Errands", onClick = {}, showHairline = false)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = ScreenMargin, vertical = 16.dp),
        ) {
            ActionButton("Cancel", onClick = {}, style = ActionStyle.Ghost)
            ActionButton("Save", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Onboarding · light", heightDp = 720) @Composable private fun OnboardingLight() = Example(false) { OnboardingExample() }
@Preview(name = "Onboarding · dark", heightDp = 720) @Composable private fun OnboardingDark() = Example(true) { OnboardingExample() }
@Preview(name = "Search · light", heightDp = 720) @Composable private fun SearchLight() = Example(false) { SearchExample() }
@Preview(name = "Search · dark", heightDp = 720) @Composable private fun SearchDark() = Example(true) { SearchExample() }
@Preview(name = "Editor · light", heightDp = 720) @Composable private fun EditorLight() = Example(false) { EditorExample() }
@Preview(name = "Editor · dark", heightDp = 720) @Composable private fun EditorDark() = Example(true) { EditorExample() }
