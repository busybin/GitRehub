package io.github.busybin.gitrehub.compose.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.busybin.gitrehub.R

/** Clients delete labels and milestones for good, so ask before the request goes out. */
@Composable
fun ConfirmDeleteDialog(
    name: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_delete_confirm_title, name)) },
        text = { Text(stringResource(R.string.m3_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}
