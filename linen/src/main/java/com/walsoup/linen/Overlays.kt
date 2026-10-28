package com.walsoup.linen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Yes/no dialog. [destructive] makes the confirm button terracotta; say what will be lost
 * in [text] rather than "Are you sure?".
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val c = Linen.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ActionButton(confirmLabel, onConfirm, style = if (destructive) ActionStyle.Record else ActionStyle.Primary)
        },
        dismissButton = {
            ActionButton(dismissLabel, onDismiss, style = ActionStyle.Ghost)
        },
        title = { Text(title, style = Linen.type.title, color = c.text) },
        text = { Text(text, style = Linen.type.bodySecondary, color = c.textSecondary) },
        shape = RoundedCornerShape(LinenDimens.DialogRadius),
        containerColor = c.surface,
        tonalElevation = 0.dp,
    )
}

/** Short confirmation shown above the thumb zone, with an optional single action such as Undo. */
@Composable
fun LinenSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Linen.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(LinenDimens.CardRadius))
            .background(c.text)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = Linen.type.bodySecondary,
            color = c.bg,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = Linen.type.label,
                color = c.container,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
