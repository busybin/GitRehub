package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Open a pull request between two branches; GitHub validates that `head` is ahead of `base`. */
@Composable
fun NewPullScreen(
    owner: String,
    repo: String,
    initialBase: String = "",
    initialHead: String = "",
    onCreated: (Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf(initialBase) }
    var head by rememberSaveable { mutableStateOf(initialHead) }
    var draft by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(owner, repo) {
        if (base.isBlank()) {
            runCatching { withContext(Dispatchers.IO) { GitHubApi.repo(owner, repo).defaultBranch } }
                .getOrNull()?.let { base = it }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.m3_new_pull),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.m3_write_title_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = base,
                onValueChange = { base = it },
                label = { Text(stringResource(R.string.m3_new_pull_base)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = head,
                onValueChange = { head = it },
                label = { Text(stringResource(R.string.m3_new_pull_head)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(stringResource(R.string.m3_write_comment_hint)) },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.m3_new_pull_draft),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = draft, onCheckedChange = { draft = it })
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.height(20.dp).padding(end = 10.dp), strokeWidth = 2.dp)
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                GitHubApi.createPull(
                                    owner = owner,
                                    repo = repo,
                                    title = title.trim(),
                                    head = head.trim(),
                                    base = base.trim(),
                                    body = body.ifBlank { null },
                                    draft = draft,
                                )
                            }
                        }
                        busy = false
                        result.onSuccess { onCreated(it.number) }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }
                    }
                },
                enabled = !busy && title.isNotBlank() && head.isNotBlank() && base.isNotBlank(),
            ) {
                Text(stringResource(R.string.m3_new_pull_create))
            }
        }
    }
}
