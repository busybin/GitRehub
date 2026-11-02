package io.github.busybin.gitrehub.compose.repos

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.offline.RepoPackMeta
import io.github.busybin.gitrehub.core.offline.RepoPackOptions
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import io.github.busybin.gitrehub.core.offline.RepoPacks
import java.util.Date

/**
 * Top-bar action of the repository screen: save the whole repository for offline reading.
 *
 * Everything the pack contains is explicit: small text files, large text files, issues/PRs, and
 * whether the pack refreshes itself. Sizes are measured before the first download so "large files"
 * is an informed choice rather than a surprise quota hit.
 */
@Composable
fun RepoPackAction(owner: String, repo: String) {
    val context = LocalContext.current
    val revision by RepoPacks.revision.collectAsStateWithLifecycle()
    val sync by RepoPackSyncer.state.collectAsStateWithLifecycle()
    var account by remember { mutableStateOf<String?>(null) }
    var pack by remember { mutableStateOf<RepoPackMeta?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, revision) {
        account = runCatching { Session.currentAccount()?.login }.getOrNull()
        pack = account?.let { RepoPacks.get(it, owner, repo) }
    }

    val fullName = "$owner/$repo"
    val runningHere = sync.running?.fullName == fullName
    val queuedPosition = sync.queued.indexOfFirst { it.fullName == fullName }
    val busyHere = runningHere || queuedPosition >= 0
    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = if (pack != null) Icons.Filled.CloudDone else Icons.Outlined.CloudDownload,
            contentDescription = stringResource(R.string.m3_pack_title),
            tint = when {
                busyHere -> MaterialTheme.colorScheme.tertiary
                pack != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    if (showDialog) {
        RepoPackDialog(
            owner = owner,
            repo = repo,
            account = account,
            pack = pack,
            running = runningHere,
            queuedPosition = queuedPosition,
            progress = if (runningHere) sync.progress else null,
            onDismiss = { showDialog = false },
            onStart = { options ->
                when (RepoPackSyncer.enqueue(context, owner, repo, options)) {
                    RepoPackSyncer.Enqueue.QUEUED -> Toast.makeText(
                        context,
                        context.getString(R.string.m3_pack_queue_added),
                        Toast.LENGTH_SHORT,
                    ).show()
                    RepoPackSyncer.Enqueue.ALREADY_RUNNING -> Unit
                    RepoPackSyncer.Enqueue.STARTED -> Unit
                }
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
    running: Boolean,
    queuedPosition: Int,
    progress: RepoPackSyncer.Progress?,
    onDismiss: () -> Unit,
    onStart: (RepoPackOptions) -> Unit,
    onRemove: () -> Unit,
) {
    val busyHere = running || queuedPosition >= 0
    var includeSmall by remember { mutableStateOf(pack?.includeSmall ?: true) }
    var includeLarge by remember { mutableStateOf(pack?.includeLarge ?: false) }
    var includeIssues by remember { mutableStateOf(pack?.includeIssues ?: false) }
    var autoSync by remember { mutableStateOf(pack?.autoSync ?: true) }
    var estimate by remember { mutableStateOf<RepoPackSyncer.Estimate?>(null) }
    var estimating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Sizes do not depend on the switches: measure once per dialog, not on every toggle.
    LaunchedEffect(Unit) {
        estimating = true
        estimate = runCatching {
            RepoPackSyncer.estimate(owner, repo, pack?.branch?.takeIf { it.isNotBlank() })
        }.getOrNull()
        estimating = false
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.m3_pack_delete_confirm_title),
            message = stringResource(R.string.m3_pack_delete_confirm_message),
            confirmLabel = stringResource(R.string.m3_pack_remove),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onRemove()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_pack_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.m3_pack_stats,
                            pack.fileCount,
                            pack.issueCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                PackSwitch(
                    title = stringResource(R.string.m3_pack_include_small),
                    hint = estimate?.let {
                        if (it.smallCount == 0) stringResource(R.string.m3_pack_none)
                        else stringResource(R.string.m3_pack_estimate, formatBytes(it.smallBytes), it.smallCount)
                    } ?: estimatingHint(estimating),
                    checked = includeSmall,
                    enabled = !busyHere,
                    onCheckedChange = { includeSmall = it },
                )
                PackSwitch(
                    title = stringResource(R.string.m3_pack_include_large),
                    hint = estimate?.let {
                        if (it.largeCount == 0) stringResource(R.string.m3_pack_none)
                        else stringResource(R.string.m3_pack_estimate, formatBytes(it.largeBytes), it.largeCount)
                    } ?: estimatingHint(estimating),
                    checked = includeLarge,
                    enabled = !busyHere,
                    onCheckedChange = { includeLarge = it },
                )
                PackSwitch(
                    title = stringResource(R.string.m3_pack_include_issues),
                    hint = estimate?.let {
                        stringResource(R.string.m3_pack_issues_count, it.issueCount)
                    } ?: estimatingHint(estimating),
                    checked = includeIssues,
                    enabled = !busyHere,
                    onCheckedChange = { includeIssues = it },
                )
                PackSwitch(
                    title = stringResource(R.string.m3_pack_auto_sync),
                    hint = stringResource(R.string.m3_pack_auto_sync_hint),
                    checked = autoSync,
                    enabled = !busyHere,
                    onCheckedChange = { autoSync = it },
                )

                if (running) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val done = progress?.done ?: 0
                            val total = progress?.total ?: 0
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { done.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.m3_pack_sync_progress,
                                        done,
                                        total,
                                        formatBytes(progress?.bytes ?: 0L),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (
                                        progress?.phase == RepoPackSyncer.Phase.ARCHIVE &&
                                        (progress?.bytes ?: 0L) > 0
                                    ) {
                                        formatBytes(progress?.bytes ?: 0L)
                                    } else {
                                        stringResource(R.string.m3_pack_syncing_meta)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (queuedPosition >= 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (queuedPosition == 0) {
                            stringResource(R.string.m3_pack_queued)
                        } else {
                            stringResource(R.string.m3_pack_queued_position, queuedPosition)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(
                        RepoPackOptions(
                            includeSmall = includeSmall,
                            includeLarge = includeLarge,
                            includeIssues = includeIssues,
                            autoSync = autoSync,
                        ),
                    )
                    onDismiss()
                },
                enabled = !busyHere && account != null &&
                    (includeSmall || includeLarge || includeIssues || pack != null),
            ) {
                Text(
                    when {
                        running -> stringResource(R.string.m3_pack_saving)
                        queuedPosition >= 0 -> stringResource(R.string.m3_pack_queued)
                        else -> stringResource(
                            if (pack == null) R.string.m3_pack_start else R.string.m3_pack_update,
                        )
                    },
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pack != null && !busyHere) {
                    TextButton(onClick = { confirmDelete = true }) {
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

@Composable
private fun estimatingHint(estimating: Boolean): String = stringResource(
    if (estimating) R.string.m3_pack_estimating else R.string.m3_pack_none,
)

@Composable
private fun PackSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
