package com.walsoup.linen.examples

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.LevelMeter
import com.walsoup.linen.Linen
import com.walsoup.linen.RoundIconButton
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SectionTitle
import com.walsoup.linen.SettingLine
import com.walsoup.linen.TopBar
import com.walsoup.linen.Waveform
import com.walsoup.linen.formatClock

/** Full-screen live recording: terracotta status, hero timer, live waveform, stop in the thumb zone. */
@Composable
fun LiveRecordingExample() {
    val c = Linen.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = ScreenMargin)
    ) {
        TopBar(onBack = {})
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(c.rec)
            )
            Spacer(Modifier.width(8.dp))
            Text("RECORDING", style = Linen.type.overline, color = c.rec)
            Spacer(Modifier.weight(1f))
            LevelMeter(level = { 0.7f }, active = true)
        }
        Spacer(Modifier.height(6.dp))
        Text(formatClock(83_000), style = Linen.type.monoHero, color = c.text)
        Spacer(Modifier.height(28.dp))
        Waveform(
            levels = sampleLevels.take(64),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            color = c.rec,
            alignEnd = true,
        )
        Spacer(Modifier.weight(1.4f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            ActionButton("Pause", onClick = {}, style = ActionStyle.Tonal)
            ActionButton("Stop and keep", onClick = {}, style = ActionStyle.Record, modifier = Modifier.weight(1f))
        }
    }
}

/** Detail of one item: back bar with actions, title, player, read-only facts. */
@Composable
fun NoteDetailExample() {
    val c = Linen.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = ScreenMargin)
    ) {
        TopBar(onBack = {}) {
            RoundIconButton(Icons.Default.Share, contentDescription = "Share", onClick = {})
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Default.Delete, contentDescription = "Delete", onClick = {}, container = c.recSoft, tint = c.rec)
        }
        Spacer(Modifier.height(12.dp))
        Text("TUESDAY 14 OCTOBER · 09:12", style = Linen.type.overline, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text("Groceries for the weekend", style = Linen.type.headline, color = c.text)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                onClick = {},
                size = 52.dp,
                container = c.primary,
                tint = c.onPrimary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Waveform(sampleLevels, Modifier.fillMaxWidth().height(36.dp), progress = 0.6f)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("00:25", style = Linen.type.monoTiny, color = c.muted)
                    Spacer(Modifier.weight(1f))
                    Text("00:42", style = Linen.type.monoTiny, color = c.muted)
                }
            }
        }
        SectionTitle("Details")
        SettingLine("Sent to", value = "WhatsApp", showChevron = false)
        SettingLine("Voice", value = "Natural", showChevron = false)
        SettingLine("Format", value = "Opus, 48 kHz", showChevron = false)
        SettingLine("Size", value = "84 KB", showChevron = false, showHairline = false)
    }
}

@Preview(name = "Live recording · light", heightDp = 720) @Composable private fun LiveLight() = Example(false) { LiveRecordingExample() }
@Preview(name = "Live recording · dark", heightDp = 720) @Composable private fun LiveDark() = Example(true) { LiveRecordingExample() }
@Preview(name = "Detail · light", heightDp = 720) @Composable private fun DetailLight() = Example(false) { NoteDetailExample() }
@Preview(name = "Detail · dark", heightDp = 720) @Composable private fun DetailDark() = Example(true) { NoteDetailExample() }
