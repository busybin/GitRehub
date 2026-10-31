package io.github.busybin.gitrehub.compose.gists

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.render

/** New / edit gist: one file, a description and the public switch. */
@Composable
fun GistEditorScreen(
    id: String?,
    initialContent: String? = null,
    onDone: () -> Unit,
) {
    val vm: GistEditorViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val messageText = message?.render()

    LaunchedEffect(id) { vm.load(id, initialContent) }
    LaunchedEffect(saved) { if (saved) onDone() }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            kotlinx.coroutines.delay(3000)
            vm.consumeMessage()
        }
    }

    AsyncContent(state = state, onRetry = { vm.load(id, initialContent) }) { data ->
        var description by rememberSaveable { mutableStateOf(data.description) }
        var filename by rememberSaveable { mutableStateOf(data.filename) }
        var content by rememberSaveable { mutableStateOf(data.content) }
        var public by rememberSaveable { mutableStateOf(data.public) }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            messageText?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(if (data.gist == null) R.string.m3_gist_new else R.string.m3_gist_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.m3_gist_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text(stringResource(R.string.m3_gist_filename)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.m3_gist_content)) },
                minLines = 12,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_gist_public),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = public, onCheckedChange = { public = it })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.save(id, description, public, filename, content) },
                    enabled = !busy && content.isNotBlank(),
                ) {
                    Text(stringResource(R.string.m3_gist_save))
                }
            }
        }
    }
}
