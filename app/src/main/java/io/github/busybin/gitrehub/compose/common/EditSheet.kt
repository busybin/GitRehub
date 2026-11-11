package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R

/**
 * Shared input sheet for writing/editing a comment, editing an issue, or opening a discussion;
 * `showTitle` adds a title field.
 *
 * [header] is inserted above the title field (the new-discussion sheet puts its category picker
 * there), and [canSubmit] lets the caller veto the button while that extra choice is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    heading: String,
    showTitle: Boolean,
    initialTitle: String,
    initialBody: String,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    repo: String? = null,
    imageBaseUrl: String? = null,
    canSubmit: Boolean = true,
    header: (@Composable () -> Unit)? = null,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var showSnippets by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { preview = !preview }) {
                    Text(
                        stringResource(
                            if (preview) R.string.m3_write_edit_mode else R.string.m3_write_preview
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            header?.invoke()
            if (showTitle) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.m3_write_title_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (preview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (body.isBlank()) {
                        Text(
                            text = stringResource(R.string.m3_write_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(markdown = body, baseUrl = imageBaseUrl, repo = repo)
                    }
                }
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text(stringResource(R.string.m3_write_comment_hint)) },
                    minLines = 5,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showSnippets = true }) {
                    Text(stringResource(R.string.m3_write_snippets))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(title, body) },
                    enabled = canSubmit && body.isNotBlank() && (!showTitle || title.isNotBlank()),
                ) {
                    Text(submitLabel)
                }
            }
        }
    }

    if (showSnippets) {
        SnippetSheet(
            current = body,
            onDismiss = { showSnippets = false },
            onPick = { snippet ->
                body = if (body.isBlank()) snippet else body.trimEnd() + "\n\n" + snippet
                showSnippets = false
            },
        )
    }
}

/** Saved replies: reuse a canned answer or save the text typed so far. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetSheet(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val replies by SavedReplies.items.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.m3_write_snippets),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.m3_write_snippets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    SavedReplies.add(current)
                    onDismiss()
                },
                enabled = current.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.m3_write_snippet_save))
            }
            Spacer(Modifier.height(10.dp))
            if (replies.isEmpty()) {
                MetaText(stringResource(R.string.m3_write_snippets_empty))
            }
            replies.forEach { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(reply.body) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reply.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { SavedReplies.remove(reply) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.m3_write_delete),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
