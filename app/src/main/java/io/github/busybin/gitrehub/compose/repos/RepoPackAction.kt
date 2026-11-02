package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.offline.RepoPackMeta
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import io.github.busybin.gitrehub.core.offline.RepoPacks
import java.util.Date

/**
 * Top-bar action of the repository screen: save the whole repository for offline reading.
 *
 * The dialog owns the two decisions: whether to include the source tree, and how big that tree is
 * (measured before downloading). Progress lives in [RepoPackSyncer] so it keeps running when the
 * user leaves the screen.
 */
@Composable
fun RepoPackAction(owner: String, repo: String) {
    val context = LocalContext.current
    val revision by RepoPacks.revision.collectAsStateWithLifecycle()
    val progress by RepoPackSyncer.progress.collectAsStateWithLifecycle()
    var account by remember { mutableStateOf<String?>(null) }
    var pack by remember { mutableStateOf<RepoPackMeta?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, revision) {
        account = runCatching { Session.currentAccount()?.login }.getOrNull()
        pack = account?.let { RepoPacks.get(it, owner, repo) }
    }

    val syncing = progress?.fullName == "$owner/$repo"
    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = if (pack != null) Icons.Filled.CloudDone else Icons.Outlined.CloudDownload,
            contentDescription = stringResource(R.string.m3_pack_title),
            tint = if (pack != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        RepoPackDialog(
            owner = owner,
            repo = repo,
            account = account,
            pack = pack,
            syncing = syncing,
            progress = progress,
            onDismiss = { showDialog = false },
            onStart = { includeCode ->
                RepoPackSyncer.start(context, owner, repo, includeCode)
            },
            onRemove = {
                pack?.let { RepoPacks.remove(context, it) }
                showDialog = false
            },
        )
    }
}

@Composable
private fun RepoPackDialog(
    owner: String,
    repo: String,
    account: String?,
    pack: RepoPackMeta?,
    syncing: Boolean,
    progress: RepoPackSyncer.Progress?,
    onDismiss: () -> Unit,
    onStart: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var includeCode by remember { mutableStateOf(pack?.includeCode ?: false) }
    var estimate by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var estimating by remember { mutableStateOf(false) }

    // Size is only needed when the code switch is on; measure once per dialog.
    LaunchedEffect(includeCode) {
        if (!includeCode) return@LaunchedEffect
        estimating = true
        estimate = runCatching {
            RepoPackSyncer.estimate(owner, repo, pack?.branch?.takeIf { it.isNotBlank() })
        }.getOrNull()
        estimating = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_pack_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (pack == null) {
                        stringResource(R.string.m3_pack_not_saved)
                    } else {
                        stringResource(
                            R.string.m3_pack_saved,
                            formatBytes(pack.bytes),
                            relativeTime(Date(pack.syncedAt)),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pack != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.m3_pack_stats,
                            pack.fileCount,
                            pack.releaseCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pack.skipped > 0) {
                        Text(
                            text = stringResource(R.string.m3_pack_skipped, pack.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeCode,
                        onCheckedChange = { includeCode = it },
                        enabled = !syncing,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.m3_pack_include_code),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = when {
                                !includeCode -> stringResource(R.string.m3_pack_include_code_hint)
                                estimating -> stringResource(R.string.m3_pack_estimating)
                                estimate != null -> stringResource(
                                    R.string.m3_pack_estimate,
                                    formatBytes(estimate!!.second),
                                    estimate!!.first,
                                )
                                else -> stringResource(R.string.m3_pack_estimate_failed)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (syncing && progress != null) {
                    Spacer(Modifier.height(12.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.m3_pack_syncing,
                                progress.done,
                                progress.total,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.m3_pack_syncing_meta),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(includeCode)
                    onDismiss()
                },
                enabled = !syncing && account != null,
            ) {
                Text(
                    stringResource(
                        if (pack == null) R.string.m3_pack_start else R.string.m3_pack_update,
                    ),
                )
            }
        },
        dismissButton = {
            Row {
                if (pack != null && !syncing) {
                    TextButton(onClick = onRemove) {
                        Text(
                            stringResource(R.string.m3_pack_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_close)) }
            }
        },
    )
}
