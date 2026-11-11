package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText

/**
 * "Since your last visit" card on the repository screen.
 *
 * Everything is local bookkeeping plus three plain GETs, so it works for any repository and never
 * asks the server for something GitHub does not expose.
 *
 * The counts are quiet monospace links, not filled chips: they are meta, and the design reserves
 * fill for things the user must notice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepoDigestCard(
    digest: RepoDigest,
    onOpenCommits: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val tokens = LocalTokens.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Text(
                text = stringResource(R.string.m3_digest_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.space4),
                verticalArrangement = Arrangement.spacedBy(tokens.space1),
            ) {
                if (digest.commits > 0) {
                    DigestLink(
                        text = if (digest.commitsCapped) {
                            stringResource(R.string.m3_digest_commits_more)
                        } else {
                            stringResource(R.string.m3_digest_commits, digest.commits)
                        },
                        onClick = onOpenCommits,
                    )
                }
                if (digest.threads > 0) {
                    DigestLink(
                        text = stringResource(R.string.m3_digest_threads, digest.threads),
                        onClick = onOpenIssues,
                    )
                }
                digest.releaseTag?.let { tag ->
                    DigestLink(
                        text = stringResource(R.string.m3_digest_release, tag),
                        onClick = onOpenRelease,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigestLink(text: String, onClick: () -> Unit) {
    MetaText(
        text = text,
        mono = true,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
