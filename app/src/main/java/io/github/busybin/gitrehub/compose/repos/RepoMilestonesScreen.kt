package io.github.busybin.gitrehub.compose.repos

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.FailureView
import io.github.busybin.gitrehub.compose.common.LoadingView
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Milestone
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Repository milestones: list all, create, edit (title/description/state), delete. */
@Composable
fun RepoMilestonesScreen(owner: String, repo: String) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Milestone>?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Milestone?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, reload) {
        error = null
        items = null
        runCatching { withContext(Dispatchers.IO) { GitHubApi.repoMilestones(owner, repo, "all") } }
            .onSuccess { items = it }
            .onFailure { error = it.toUiText() }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            error != null -> FailureView(message = error!!, onRetry = { reload++ })
            items == null -> LoadingView()
            items!!.isEmpty() -> EmptyView(text = stringResource(R.string.m3_milestones_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items!!, key = { it.number }) { milestone ->
                    val due = milestone.dueOn?.let { relativeTime(it) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = milestone }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = milestone.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        MetaText(
                            buildString {
                                append("${milestone.closedIssues}/${milestone.openIssues + milestone.closedIssues}")
                                if (milestone.state == "closed") {
                                    append(" · ").append(stringResource(R.string.m3_milestone_closed))
                                }
                                due?.let { append(" · ").append(it) }
                            }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.m3_milestone_new)) }
    }

    if (creating || editing != null) {
        MilestoneDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onDelete = {
                val target = editing ?: return@MilestoneDialog
                editing = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { GitHubApi.deleteMilestone(owner, repo, target.number) }
                    }
                    reload++
                }
            },
            onSave = { title, description, closed ->
                val original = editing
                creating = false
                editing = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val state = if (closed) "closed" else "open"
                            if (original == null) {
                                GitHubApi.createMilestone(owner, repo, title, description, null)
                            } else {
                                GitHubApi.editMilestone(
                                    owner, repo, original.number, title, state, description, null,
                                )
                            }
                        }
                    }
                    reload++
                }
            },
        )
    }
}

@Composable
private fun MilestoneDialog(
    initial: Milestone?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (title: String, description: String?, closed: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var closed by remember { mutableStateOf(initial?.state == "closed") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.m3_milestone_new else R.string.m3_milestone_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.m3_milestone_title)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.m3_milestone_description)) },
                    minLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !closed,
                        onClick = { closed = false },
                        label = { Text(stringResource(R.string.m3_milestone_open)) },
                    )
                    FilterChip(
                        selected = closed,
                        onClick = { closed = true },
                        label = { Text(stringResource(R.string.m3_milestone_closed)) },
                    )
                }
                if (initial != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.m3_milestone_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), description.trim().takeIf { it.isNotEmpty() }, closed) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.m3_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}
