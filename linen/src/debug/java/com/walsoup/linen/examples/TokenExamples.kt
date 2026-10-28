package com.walsoup.linen.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.walsoup.linen.Linen
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.TopBar

private fun Color.hex(): String {
    val argb = toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
    return if (argb.startsWith("FF")) "#" + argb.drop(2) else "#$argb"
}

/** Every color role with its current value; flips with the theme. */
@Composable
fun ColorsExample() {
    val c = Linen.colors
    val roles = listOf(
        "bg" to c.bg, "surface" to c.surface,
        "container" to c.container, "containerLow" to c.containerLow,
        "hairline" to c.hairline, "inactive" to c.inactive,
        "text" to c.text, "textSecondary" to c.textSecondary,
        "muted" to c.muted, "scrim" to c.scrim,
        "primary" to c.primary, "onPrimary" to c.onPrimary,
        "rec" to c.rec, "recSoft" to c.recSoft,
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        TopBar(onBack = {}, title = "Colors")
        Spacer(Modifier.height(8.dp))
        roles.chunked(2).forEach { pair ->
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (name, color) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color)
                                .border(1.dp, c.hairline, RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(name, style = Linen.type.label, color = c.text)
                            Text(color.hex(), style = Linen.type.monoTiny, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** The type scale, each style set in its own sample. */
@Composable
fun TypeExample() {
    val c = Linen.colors
    val t = Linen.type
    val styles: List<Triple<String, String, TextStyle>> = listOf(
        Triple("display", "Notes.", t.display),
        Triple("headline", "Groceries", t.headline),
        Triple("title", "Recording", t.title),
        Triple("body", "Drop the note into the chat", t.body),
        Triple("bodySecondary", "3 notes, 2 min 41 s", t.bodySecondary),
        Triple("caption", "Today, 09:12", t.caption),
        Triple("overline", "TUESDAY 14 OCTOBER", t.overline),
        Triple("label", "Save note", t.label),
        Triple("monoHero", "01:23", t.monoHero),
        Triple("monoDock", "00:42", t.monoDock),
        Triple("monoSmall / monoTiny", "01:11  ·  07", t.monoSmall),
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        TopBar(onBack = {}, title = "Type")
        styles.forEach { (name, sample, style) ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(sample, style = style, color = c.text, maxLines = 1)
                Text(
                    "$name  ${style.fontSize.value.toInt()}/${style.lineHeight.value.toInt()}",
                    style = t.monoTiny,
                    color = c.muted,
                )
            }
        }
    }
}

@Preview(name = "Colors · light", heightDp = 720) @Composable private fun ColorsLight() = Example(false) { ColorsExample() }
@Preview(name = "Colors · dark", heightDp = 720) @Composable private fun ColorsDark() = Example(true) { ColorsExample() }
@Preview(name = "Type · light", heightDp = 720) @Composable private fun TypeLight() = Example(false) { TypeExample() }
@Preview(name = "Type · dark", heightDp = 720) @Composable private fun TypeDark() = Example(true) { TypeExample() }
