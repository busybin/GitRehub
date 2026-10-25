package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.FailureView
import io.github.busybin.gitrehub.compose.common.LoadingView
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Label
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Repository labels: list, create, edit, delete. */
@Composable
fun RepoLabelsScreen(owner: String, repo: String) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Label>?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Label?>(null) }
    var creating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, repo, reload) {
        error = null
        items = null
        runCatching { withContext(Dispatchers.IO) { GitHubApi.repoLabels(owner, repo) } }
            .onSuccess { items = it }
            .onFailure { error = it.toUiText() }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            error != null -> FailureView(
                message = error!!,
                onRetry = { reload++ },
            )
            items == null -> LoadingView()
            items!!.isEmpty() -> EmptyView(text = stringResource(R.string.m3_labels_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items!!, key = { it.name.orEmpty() }) { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = label }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(parseColor(label.color)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            if (!label.description.isNullOrBlank()) {
                                MetaText(label.description)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.m3_label_new)) }
    }

    message?.let {
        LaunchedEffect(it) { message = null }
    }

    if (creating || editing != null) {
        LabelDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onDelete = {
                val target = editing ?: return@LabelDialog
                editing = null
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            GitHubApi.deleteLabel(owner, repo, target.name.orEmpty())
                        }
                    }.isSuccess
                    message = if (ok) null else "failed"
                    reload++
                }
            },
            onSave = { name, color, description ->
                val original = editing?.name
                creating = false
                editing = null
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            if (original == null) {
                                GitHubApi.createLabel(owner, repo, name, color, description)
                            } else {
                                GitHubApi.editLabel(owner, repo, original, name, color, description)
                            }
                        }
                    }.isSuccess
                    message = if (ok) null else "failed"
                    reload++
                }
            },
        )
    }
}

@Composable
private fun LabelDialog(
    initial: Label?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (name: String, color: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color?.let { "#$it" } ?: "#1F6FEB") }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.m3_label_new else R.string.m3_label_edit
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.m3_label_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text(stringResource(R.string.m3_label_color)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.m3_label_description)) },
                    minLines = 2,
                )
                if (initial != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.m3_label_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && color.trim().removePrefix("#").length >= 3) {
                        onSave(name.trim(), color.trim(), description.trim().takeIf { it.isNotEmpty() })
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.m3_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}

/** `#RRGGBB` (or bare `RRGGBB`) to a colour; unparsable values fall back to grey. */
internal fun parseColor(raw: String?): Color {
    val hex = raw?.trim()?.removePrefix("#").orEmpty()
    return runCatching {
        if (hex.length == 6) Color(0xFF000000 or hex.toLong(16)) else Color(0xFF888888)
    }.getOrDefault(Color(0xFF888888))
}

