package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.render

/**
 * New issue: title, body (Markdown), labels, assignees and milestone in a single `POST`.
 * On success the number is handed back via `onCreated`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewIssueScreen(owner: String, repo: String, onCreated: (Int) -> Unit) {
    val vm: NewIssueViewModel = viewModel()
    val options by vm.options.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf(emptySet<String>()) }
    var assignees by remember { mutableStateOf(emptySet<String>()) }
    var milestone by remember { mutableStateOf<Int?>(null) }
    var templateIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(owner, repo) { vm.load(owner, repo) }
    LaunchedEffect(created) { created?.let(onCreated) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
    ) {
        val templates = options.templates
        if (templates.isNotEmpty()) {
            Text(stringResource(R.string.m3_issue_template), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = templateIndex == null,
                    onClick = { templateIndex = null },
                    label = { Text(stringResource(R.string.m3_issue_template_blank)) },
                )
                templates.forEachIndexed { index, template ->
                    FilterChip(
                        selected = templateIndex == index,
                        onClick = {
                            templateIndex = index
                            body = template.body
                            val prefix = template.titlePrefix
                            if (!prefix.isNullOrBlank() && title.isBlank()) title = prefix
                        },
                        label = { Text(template.name + if (template.isForm) " · form" else "") },
                    )
                }
            }
            templateIndex?.let { index ->
                templates.getOrNull(index)?.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    MetaText(it)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.m3_new_issue_title)) },
            placeholder = { Text(stringResource(R.string.m3_write_title_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = { Text(stringResource(R.string.m3_new_issue_body)) },
            placeholder = { Text(stringResource(R.string.m3_write_comment_hint)) },
            minLines = 8,
        )

        if (options.failed) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_new_issue_options_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.load(owner, repo, force = true) }) {
                    Text(stringResource(R.string.m3_retry))
                }
            }
        }

        val allLabels = options.labels.mapNotNull { it.name }
        if (allLabels.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.m3_write_labels), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allLabels.forEach { name ->
                    FilterChip(
                        selected = labels.contains(name),
                        onClick = {
                            labels = if (labels.contains(name)) labels - name else labels + name
                        },
                        label = { Text(name) },
                    )
                }
            }
        }

        val allAssignees = options.assignees.mapNotNull { it.login }
        if (allAssignees.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.m3_write_assignees),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allAssignees.forEach { login ->
                    FilterChip(
                        selected = assignees.contains(login),
                        onClick = {
                            assignees =
                                if (assignees.contains(login)) assignees - login else assignees + login
                        },
                        label = { Text(login) },
                    )
                }
            }
        }

        if (options.milestones.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.m3_write_milestone),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = milestone == null,
                    onClick = { milestone = null },
                    label = { Text(stringResource(R.string.m3_write_no_milestone)) },
                )
                options.milestones.forEach { item ->
                    FilterChip(
                        selected = milestone == item.number,
                        onClick = { milestone = item.number },
                        label = { Text(item.title.orEmpty()) },
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.m3_write_failed, it.render()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Button(
                onClick = {
                    vm.create(
                        owner, repo, title, body,
                        labels.toList(), assignees.toList(), milestone,
                    )
                },
                enabled = !submitting && title.isNotBlank(),
            ) {
                Text(stringResource(R.string.m3_new_issue_submit))
            }
        }
        if (options.loading) {
            Spacer(Modifier.height(14.dp))
            MetaText(stringResource(R.string.m3_new_issue_loading_options))
        }
        Spacer(Modifier.height(24.dp))
    }
}
