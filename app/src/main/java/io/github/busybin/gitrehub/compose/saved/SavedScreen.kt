package io.github.busybin.gitrehub.compose.saved

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.offline.OfflineSaves
import io.github.busybin.gitrehub.core.offline.RepoPackMeta
import io.github.busybin.gitrehub.core.offline.RepoPackPrefs
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import io.github.busybin.gitrehub.core.offline.RepoPacks
import io.github.busybin.gitrehub.core.offline.SavedThreadMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Everything kept on this device: whole-repository packs and saved threads.
 *
 * The pack section is the offline library: a summary with the quota, a global auto-sync switch and
 * one card per repository that can be opened directly. Each card shows what the copy actually
 * contains (size, files, issues, branch, top-level paths), so "saved" is not a black box.
 */
@Composable
fun SavedScreen(
    onOpen: (SavedThreadMeta) -> Unit,
    onOpenRepo: (String, String) -> Unit,
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    var threads by remember { mutableStateOf<List<SavedThreadMeta>>(emptyList()) }
    var packs by remember { mutableStateOf<List<RepoPackMeta>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var pendingThread by remember { mutableStateOf<SavedThreadMeta?>(null) }
    var pendingPack by remember { mutableStateOf<RepoPackMeta?>(null) }
    val packRevision by RepoPacks.revision.collectAsStateWithLifecycle()
    val sync by RepoPackSyncer.state.collectAsStateWithLifecycle()
    val autoSync by RepoPackPrefs.enabled.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, packRevision) {
        // Only the first read blocks the screen; later revisions are toggles/deletes and must not
        // flash the spinner over the list.
        if (!loaded) loading = true
        val login = runCatching { Session.currentAccount()?.login }.getOrNull()
        threads = if (login == null) emptyList() else OfflineSaves.index(login)
        packs = if (login == null) emptyList() else RepoPacks.index(login)
        previews = if (packs.isEmpty()) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) {
                packs.associate { pack ->
                    pack.fullName to RepoPacks.entries(context, pack, "")
                        .mapNotNull { it.name }
                        .take(6)
                }
            }
        }
        loading = false
        loaded = true
    }

    pendingThread?.let { meta ->
        ConfirmDialog(
            title = stringResource(R.string.m3_saved_delete_confirm_title),
            message = stringResource(R.string.m3_saved_delete_confirm_message),
            confirmLabel = stringResource(R.string.m3_remove),
            destructive = true,
            onConfirm = {
                OfflineSaves.remove(context, meta)
                threads = threads.filterNot { it.key == meta.key }
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

            threads.isEmpty() && packs.isEmpty() -> Text(
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
                        OfflineSummary(
                            count = packs.size,
                            used = packs.sumOf { it.bytes },
                            quota = RepoPacks.MAX_TOTAL_BYTES,
                            autoSync = autoSync,
                            onAutoSync = { enabled ->
                                RepoPackPrefs.setEnabled(enabled)
                                if (!enabled) {
                                    RepoPackSyncer.cancelAuto()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.m3_pack_auto_stopped),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                    items(packs, key = { "pack-${it.fullName}" }) { pack ->
                        val running = sync.running?.fullName == pack.fullName
                        RepoPackRow(
                            pack = pack,
                            preview = previews[pack.fullName].orEmpty(),
                            busy = running || sync.queued.any { it.fullName == pack.fullName },
                            running = running,
                            progress = if (running) sync.progress else null,
                            onOpen = { onOpenRepo(pack.owner, pack.repo) },
                            onAutoSync = { enabled -> RepoPacks.setAutoSync(context, pack, enabled) },
                            onRemove = { pendingPack = pack },
                        )
                    }
                }
                if (threads.isNotEmpty()) {
                    item(key = "threads-header") {
                        Text(
                            text = stringResource(R.string.m3_saved),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(threads, key = { it.key }) { meta ->
                    SavedRow(
                        meta = meta,
                        onOpen = { onOpen(meta) },
                        onDelete = { pendingThread = meta },
                    )
                }
            }
        }
    }
}

/** Quota, count and the account-wide auto-sync switch above the pack cards. */
@Composable
private fun OfflineSummary(
    count: Int,
    used: Long,
    quota: Long,
    autoSync: Boolean,
    onAutoSync: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_offline_repos),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.m3_pack_quota,
                        count,
                        formatBytes(used),
                        formatBytes(quota),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (used.toFloat() / quota).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.m3_pack_auto_sync_all),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    MetaText(stringResource(R.string.m3_pack_auto_sync_all_hint))
                }
                Switch(checked = autoSync, onCheckedChange = onAutoSync)
            }
        }
    }
}

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
                    stringResource(R.string.m3_saved_at, relativeTime(Date(meta.savedAt))),
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

/**
 * One offline repository: tap the card to open the repository, the switches and the delete button
 * are separate touch targets. Progress replaces the auto-sync row while this pack is downloading.
 */
@Composable
private fun RepoPackRow(
    pack: RepoPackMeta,
    preview: List<String>,
    busy: Boolean,
    running: Boolean,
    progress: RepoPackSyncer.Progress?,
    onOpen: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pack.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaText(
                        stringResource(R.string.m3_pack_stats, pack.fileCount, pack.issueCount),
                    )
                }
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.m3_pack_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PackBadge(
                    stringResource(
                        if (pack.includeSmall || pack.includeLarge) R.string.m3_pack_badge_code
                        else R.string.m3_pack_badge_meta,
                    ),
                )
                if (pack.includeIssues) {
                    PackBadge(stringResource(R.string.m3_pack_badge_issues))
                }
                if (pack.skipped > 0) {
                    PackBadge(stringResource(R.string.m3_pack_skipped_short, pack.skipped))
                }
                if (pack.branch.isNotBlank()) PackBadge(pack.branch)
            }
            Spacer(Modifier.height(6.dp))
            MetaText(
                "${formatBytes(pack.bytes)} · ${relativeTime(Date(pack.syncedAt))}" +
                    if (pack.headSha.isNotBlank()) " · ${pack.headSha.take(7)}" else "",
                maxLines = 1,
            )
            if (preview.isNotEmpty()) {
                MetaText(
                    stringResource(R.string.m3_pack_contents, preview.joinToString(" · ")),
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (busy) {
                if (running && progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    MetaText(
                        stringResource(
                            R.string.m3_pack_sync_progress,
                            progress.done,
                            progress.total,
                            formatBytes(progress.bytes),
                        ),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                    Spacer(Modifier.height(4.dp))
                    MetaText(
                        stringResource(
                            if (running) R.string.m3_pack_syncing_meta else R.string.m3_pack_queued,
                        ),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.m3_pack_auto_sync),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = pack.autoSync, onCheckedChange = onAutoSync)
                }
            }
        }
    }
}

@Composable
private fun PackBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
