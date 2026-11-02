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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.core.offline.RepoPackMeta
import io.github.busybin.gitrehub.core.offline.RepoPacks
import io.github.busybin.gitrehub.compose.common.formatBytes
import java.util.Date
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
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
    var packs by remember { mutableStateOf<List<RepoPackMeta>>(emptyList()) }
    var pendingThread by remember { mutableStateOf<SavedThreadMeta?>(null) }
    var pendingPack by remember { mutableStateOf<RepoPackMeta?>(null) }
    val packRevision by RepoPacks.revision.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey, packRevision) {
        loading = true
        val login = runCatching { Session.currentAccount()?.login }.getOrNull()
        items = if (login == null) emptyList() else OfflineSaves.index(login)
        packs = if (login == null) emptyList() else RepoPacks.index(login)
        loading = false
    }

    pendingThread?.let { meta ->
        ConfirmDialog(
            title = stringResource(R.string.m3_saved_delete_confirm_title),
            message = stringResource(R.string.m3_saved_delete_confirm_message),
            confirmLabel = stringResource(R.string.m3_remove),
            destructive = true,
            onConfirm = {
                OfflineSaves.remove(context, meta)
                items = items.filterNot { it.key == meta.key }
                pendingThread = null
            },
            onDismiss = { pendingThread = null },
        )
    }
    pendingPack?.let { pack ->
        ConfirmDialog(
            title = stringResource(R.string.m3_pack_delete_confirm_title),
            message = stringResource(R.string.m3_pack_delete_confirm_message),
            confirmLabel = stringResource(R.string.m3_pack_remove),
            destructive = true,
            onConfirm = {
                RepoPacks.remove(context, pack)
                packs = packs.filterNot { it.fullName == pack.fullName }
                pendingPack = null
            },
            onDismiss = { pendingPack = null },
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(24.dp),
                strokeWidth = 2.dp,
            )

            items.isEmpty() && packs.isEmpty() -> Text(
                text = stringResource(R.string.m3_saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (packs.isNotEmpty()) {
                    item(key = "packs-header") {
                        Text(
                            text = stringResource(R.string.m3_offline_repos),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(packs, key = { "pack-${it.fullName}" }) { pack ->
                        RepoPackRow(
                            pack = pack,
                            onRemove = { pendingPack = pack },
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    item(key = "threads-header") {
                        Text(
                            text = stringResource(R.string.m3_saved),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items, key = { it.key }) { meta ->
                    SavedRow(
                        meta = meta,
                        onOpen = { onOpen(meta) },
                        onDelete = { pendingThread = meta },
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

/** One offline repository: name, size, last sync and a delete action. */
@Composable
private fun RepoPackRow(pack: RepoPackMeta, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = pack.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                MetaText(
                    stringResource(R.string.m3_pack_saved, formatBytes(pack.bytes), relativeTime(Date(pack.syncedAt))),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.m3_pack_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
