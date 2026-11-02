package io.github.busybin.gitrehub.compose.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.offline.OfflineSaves
import io.github.busybin.gitrehub.core.offline.SavedThreadMeta

/**
 * Threads saved for offline reading.
 *
 * Only the signed-in account's copies are listed, and signing out deletes them (see `Session`).
 */
@Composable
fun SavedScreen(
    onOpen: (SavedThreadMeta) -> Unit,
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<SavedThreadMeta>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        loading = true
        val login = runCatching { Session.currentAccount()?.login }.getOrNull()
        items = if (login == null) emptyList() else OfflineSaves.index(login)
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(24.dp),
                strokeWidth = 2.dp,
            )

            items.isEmpty() -> Text(
                text = stringResource(R.string.m3_saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.key }) { meta ->
                    SavedRow(
                        meta = meta,
                        onOpen = { onOpen(meta) },
                        onDelete = {
                            OfflineSaves.remove(context, meta)
                            items = items.filterNot { it.key == meta.key }
                        },
                    )
                }
            }
        }
    }}

@Composable
private fun SavedRow(meta: SavedThreadMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = meta.title.ifBlank { "#${meta.number}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                MetaText("${meta.repoFullName} #${meta.number}")
                MetaText(
                    stringResource(R.string.m3_saved_at, relativeTime(java.util.Date(meta.savedAt))),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.m3_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
