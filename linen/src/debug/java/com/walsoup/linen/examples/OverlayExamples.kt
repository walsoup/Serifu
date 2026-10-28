package com.walsoup.linen.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ConfirmDialog
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenDimens
import com.walsoup.linen.LinenSnackbar
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SwitchLine

/** Destructive confirmation over the list. The dialog names what will be lost. */
@Composable
fun ConfirmDialogExample() {
    Box(Modifier.fillMaxSize()) {
        ListScreenExample()
        ConfirmDialog(
            title = "Delete this note?",
            text = "\"Groceries for the weekend\" and its audio will be removed from this phone.",
            confirmLabel = "Delete",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}

/** Bottom sheet with a handle, a short explanation, switch rows and one Done action. */
@Composable
fun PickerSheetExample() {
    val c = Linen.colors
    var chosen by remember { mutableStateOf(mapOf("WhatsApp" to true, "Signal" to true, "Telegram" to false, "Messages" to false)) }
    Box(Modifier.fillMaxSize()) {
        SettingsScreenExample()
        Box(Modifier.fillMaxSize().background(c.scrim))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(topStart = LinenDimens.SheetRadius, topEnd = LinenDimens.SheetRadius))
                .navigationBarsPadding()
                .padding(horizontal = ScreenMargin)
                .padding(bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.hairline)
            )
            Text("Paste into", style = Linen.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text("Ditto only pastes in the apps you pick.", style = Linen.type.bodySecondary, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            chosen.entries.forEachIndexed { i, (app, on) ->
                SwitchLine(app, checked = on, onCheckedChange = { chosen = chosen + (app to it) }, showHairline = i < chosen.size - 1)
            }
            Spacer(Modifier.height(16.dp))
            ActionButton("Done", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Snackbar sitting just above the record dock, with Undo. */
@Composable
fun SnackbarExample() {
    Box(Modifier.fillMaxSize()) {
        ListScreenExample()
        LinenSnackbar(
            message = "Note copied. Paste it anywhere.",
            actionLabel = "Undo",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp)
                .padding(bottom = 128.dp),
        )
    }
}

@Preview(name = "Dialog · light", heightDp = 720) @Composable private fun DialogLight() = Example(false) { ConfirmDialogExample() }
@Preview(name = "Dialog · dark", heightDp = 720) @Composable private fun DialogDark() = Example(true) { ConfirmDialogExample() }
@Preview(name = "Sheet · light", heightDp = 720) @Composable private fun SheetLight() = Example(false) { PickerSheetExample() }
@Preview(name = "Sheet · dark", heightDp = 720) @Composable private fun SheetDark() = Example(true) { PickerSheetExample() }
@Preview(name = "Snackbar · light", heightDp = 720) @Composable private fun SnackbarLight() = Example(false) { SnackbarExample() }
@Preview(name = "Snackbar · dark", heightDp = 720) @Composable private fun SnackbarDark() = Example(true) { SnackbarExample() }
