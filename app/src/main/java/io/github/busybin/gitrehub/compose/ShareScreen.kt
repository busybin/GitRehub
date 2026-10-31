package io.github.busybin.gitrehub.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.SavedPrefs
import io.github.busybin.gitrehub.compose.common.copyText
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Entry point for text shared into GitRehub: open a link, turn it into an issue, or save it as a
 * gist. Before this, shared text without a URL was dropped silently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(
    text: String,
    onNewIssue: (String, String) -> Unit,
    onNewGist: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val favorites by SavedPrefs.favorites.collectAsStateWithLifecycle()
    val url = remember(text) {
        text.split(Regex("\\s+")).firstOrNull {
            it.startsWith("http://") || it.startsWith("https://")
        }?.trimEnd('.', ',', ')', ']', '"', '\'')
    }
    var repo by rememberSaveable { mutableStateOf(favorites.firstOrNull().orEmpty()) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.m3_share_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (url != null) {
            Button(
                onClick = {
                    BrowserLauncher.open(context, url)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.m3_share_open_link)) }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = stringResource(R.string.m3_share_repo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it },
            placeholder = { Text("owner/repo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (favorites.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                favorites.take(8).forEach { name ->
                    OutlinedButton(onClick = { repo = name }) { Text(name) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val owner = repo.substringBefore('/', "")
                val name = repo.substringAfter('/', "")
                if (owner.isNotBlank() && name.isNotBlank()) onNewIssue(owner, name)
            },
            enabled = repo.contains('/') && repo.substringBefore('/').isNotBlank() &&
                repo.substringAfter('/').isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.m3_share_new_issue)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onNewGist,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.m3_share_new_gist)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                copyText(context, text, context.getString(R.string.m3_share_copied))
                onDone()
            }) { Text(stringResource(R.string.m3_share_copy)) }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.m3_cancel)) }
        }
    }
}
