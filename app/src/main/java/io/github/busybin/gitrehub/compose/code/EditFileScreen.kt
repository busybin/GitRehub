package io.github.busybin.gitrehub.compose.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.FailureView
import io.github.busybin.gitrehub.compose.common.LoadingView
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.render

/** Edit a text file, then commit it to the default branch or open a PR from a new branch. */
@Composable
fun EditFileScreen(
    owner: String,
    repo: String,
    path: String,
    ref: String?,
    onDone: (prNumber: Int?) -> Unit,
) {
    val vm: EditFileViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()

    var content by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var newBranch by remember { mutableStateOf("patch-${path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "file" }}") }
    var prMode by remember { mutableStateOf(true) }
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }

    LaunchedEffect(owner, repo, path, ref) { vm.load(owner, repo, path, ref) }
    LaunchedEffect(state.text, state.loading) {
        if (!state.loading && content == null) content = state.text
    }
    // Both outcomes end the screen: a direct commit has no PR to open, so it just goes back.
    LaunchedEffect(finished) { if (finished) onDone(vm.prNumber.value) }

    when {
        state.loading -> LoadingView()
        state.failed -> FailureView(
            message = UiText.res(R.string.m3_edit_file_load_failed),
            onRetry = { vm.load(owner, repo, path, ref, force = true) },
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(path, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            MetaText(
                stringResource(
                    R.string.m3_edit_file_target,
                    state.ref ?: state.defaultBranch,
                ) + if (state.canWrite) "" else " · " + stringResource(R.string.m3_edit_file_readonly)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = content.orEmpty(),
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                label = { Text(stringResource(R.string.m3_edit_file_content)) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.m3_edit_commit_message)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !prMode,
                    onClick = { prMode = false },
                    label = { Text(stringResource(R.string.m3_edit_direct)) },
                )
                FilterChip(
                    selected = prMode,
                    onClick = { prMode = true },
                    label = { Text(stringResource(R.string.m3_edit_new_branch)) },
                )
            }
            if (prMode) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newBranch,
                    onValueChange = { newBranch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.m3_edit_branch_name)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = prTitle,
                    onValueChange = { prTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.m3_edit_pr_title)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.m3_edit_pr_title_hint, path.substringAfterLast('/'))) },
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = prBody,
                    onValueChange = { prBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.m3_edit_pr_body)) },
                    minLines = 3,
                )
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.m3_write_failed, it.render()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                }
                Button(
                    onClick = {
                        vm.submit(
                            owner = owner,
                            repo = repo,
                            path = path,
                            content = content.orEmpty(),
                            message = message,
                            newBranch = if (prMode) newBranch else null,
                            prTitle = prTitle,
                            prBody = prBody,
                        )
                    },
                    enabled = !submitting && state.canWrite &&
                        (!prMode || newBranch.isNotBlank()),
                ) {
                    Text(
                        stringResource(
                            if (prMode) R.string.m3_edit_submit_pr else R.string.m3_edit_submit
                        )
                    )
                }
            }
        }
    }
}
