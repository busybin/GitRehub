package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R

/**
 * "Since your last visit" card on the repository screen.
 *
 * Everything is local bookkeeping plus three plain GETs, so it works for any repository and never
 * asks the server for something GitHub does not expose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepoDigestCard(
    digest: RepoDigest,
    onOpenCommits: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.m3_digest_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (digest.commits > 0) {
                    AssistChip(
                        onClick = onOpenCommits,
                        label = {
                            Text(
                                text = if (digest.commitsCapped) {
                                    stringResource(R.string.m3_digest_commits_more)
                                } else {
                                    stringResource(R.string.m3_digest_commits, digest.commits)
                                },
                            )
                        },
                    )
                }
                if (digest.threads > 0) {
                    AssistChip(
                        onClick = onOpenIssues,
                        label = { Text(stringResource(R.string.m3_digest_threads, digest.threads)) },
                    )
                }
                digest.releaseTag?.let { tag ->
                    AssistChip(
                        onClick = onOpenRelease,
                        label = { Text(stringResource(R.string.m3_digest_release, tag)) },
                    )
                }
            }
        }
    }
}
