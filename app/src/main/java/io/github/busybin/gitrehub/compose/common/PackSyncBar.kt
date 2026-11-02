package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer

/**
 * App-wide pack download bar.
 *
 * It lives in the scaffold (not inside a screen), so it occupies layout instead of floating over
 * the page, stays visible on every route and is covered by the navigation drawer when that opens.
 * One line of text plus a thin progress line keeps the cost small while a big repository downloads.
 */
@Composable
fun PackSyncBar(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by RepoPackSyncer.state.collectAsStateWithLifecycle()
    val task = state.running ?: return
    val progress = state.progress
    val phase = when (progress?.phase) {
        RepoPackSyncer.Phase.ARCHIVE -> stringResource(R.string.m3_pack_phase_archive)
        RepoPackSyncer.Phase.CODE -> stringResource(R.string.m3_pack_phase_code)
        RepoPackSyncer.Phase.ISSUES -> stringResource(R.string.m3_pack_phase_issues)
        else -> stringResource(R.string.m3_pack_syncing_meta)
    }
    val detail = when {
        progress == null -> ""
        progress.total > 0 -> stringResource(
            R.string.m3_pack_sync_progress,
            progress.done,
            progress.total,
            formatBytes(progress.bytes),
        )
        progress.phase == RepoPackSyncer.Phase.ARCHIVE && progress.bytes > 0 ->
            formatBytes(progress.bytes)
        else -> ""
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { onOpen(task.fullName) }) {
            HorizontalDivider()
            if (progress != null) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                }
            }
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${task.fullName} · $phase",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (state.queued.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.m3_pack_queue_waiting, state.queued.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { RepoPackSyncer.cancelAll() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.m3_pack_cancel_save),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
