package io.github.busybin.gitrehub.compose.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.model.CommitFile

/** Compare two branches/tags: commits and the aggregate diff between them. */
@Composable
fun CompareScreen(
    owner: String,
    repo: String,
    base: String,
    head: String,
    onOpenCommit: (String) -> Unit = {},
    onCreatePull: (() -> Unit)? = null,
) {
    val vm: CompareViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo, base, head) { vm.load(owner, repo, base, head) }

    AsyncContent(state = state, onRetry = { vm.load(owner, repo, base, head) }) { result ->
        if (result.isEmpty) {
            EmptyView(stringResource(R.string.m3_compare_empty, base, head))
            return@AsyncContent
        }
        var expanded by remember { mutableStateOf(emptySet<String>()) }
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "$base ← $head",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.m3_compare_summary,
                                result.totalCommits,
                                result.files.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.m3_compare_ahead_behind,
                                result.aheadBy,
                                result.behindBy,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onCreatePull != null) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onCreatePull) {
                                Text(stringResource(R.string.m3_compare_create_pull))
                            }
                        }
                    }
                }
            }
            if (result.commits.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.m3_commits),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(result.commits, key = { it.sha }) { commit ->
                    CompareCommitRow(commit) { onOpenCommit(commit.sha) }
                }
            }
            if (result.files.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.m3_compare_files, result.files.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(result.files, key = { it.filename ?: it.sha ?: "file" }) { file ->
                    CompareFileRow(
                        file = file,
                        expanded = expanded.contains(file.filename.orEmpty()),
                        onToggle = {
                            val path = file.filename.orEmpty()
                            expanded = if (expanded.contains(path)) expanded - path
                            else expanded + path
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CompareCommitRow(commit: Commit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = commit.messageTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${commit.shortSha} · ${commit.authorName.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompareFileRow(file: CommitFile, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = file.filename.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${file.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF3FB950),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "−${file.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                HorizontalDivider()
                val patch = file.patch
                if (patch.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.m3_pr_diff_too_large),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Text(
                        text = patch,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
