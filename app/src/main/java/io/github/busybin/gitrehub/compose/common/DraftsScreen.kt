package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import kotlinx.coroutines.launch

/** Comments that failed to send while offline: retry one, retry all, or delete. */
@Composable
fun DraftsScreen(onOpenIssue: (String, String, Int) -> Unit) {
    val drafts by DraftQueue.drafts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.m3_drafts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        DraftQueue.flushAll()
                        busy = false
                    }
                },
                enabled = !busy && drafts.isNotEmpty(),
            ) { Text(stringResource(R.string.m3_drafts_send_all)) }
        }
        if (drafts.isEmpty()) {
            EmptyView(text = stringResource(R.string.m3_drafts_empty))
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(drafts, key = { it.id }) { draft ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        TextButton(
                            onClick = { onOpenIssue(draft.owner, draft.repo, draft.number) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("${draft.owner}/${draft.repo}#${draft.number}")
                        }
                        Text(
                            text = draft.body,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (draft.blocked) {
                            Text(
                                text = stringResource(R.string.m3_draft_blocked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        if (!DraftQueue.send(draft) && draft.body.isNotBlank()) {
                                            // keep the draft for the next attempt
                                        }
                                        busy = false
                                    }
                                },
                            ) { Text(stringResource(R.string.m3_draft_send)) }
                            TextButton(
                                enabled = !busy,
                                onClick = { DraftQueue.remove(draft.id) },
                            ) { Text(stringResource(R.string.m3_draft_delete)) }
                        }
                    }
                }
            }
        }
    }
}

