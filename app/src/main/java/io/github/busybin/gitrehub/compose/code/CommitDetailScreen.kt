package io.github.busybin.gitrehub.compose.code

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.model.CommitFile
import io.github.busybin.gitrehub.helper.AppHelper

/**
 * Commit detail plus diff.
 *
 * All data arrives at once: `files[].patch` in the `commits/{sha}` response is the unified diff, so it
 * just gets coloured line by line; no need to parse the `Accept: ...diff` plain text.
 */
@Composable
fun CommitDetailScreen(owner: String, repo: String, sha: String) {
    val vm: CommitDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, sha) { vm.load(owner, repo, sha) }

    AsyncContent(state = state, onRetry = { vm.load(owner, repo, sha, force = true) }) { commit ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CommitHeader(commit) }
            val files = commit.files
            if (files.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.m3_commit_no_diff),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(files, key = { it.filename ?: it.sha ?: "file" }) { file ->                    FileDiff(file)
                }
                item {
                    Text(
                        text = stringResource(R.string.m3_commit_diff_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitHeader(commit: Commit) {
    val message = commit.message
    val author = commit.authorName
    val date = commit.date
    val stats = commit.stats
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = message.lineSequence().firstOrNull().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            if (message.lineSequence().count() > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.lineSequence().drop(1).joinToString("\n").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(commit.author?.avatarUrl, size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = author ?: "unknown",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MetaText(relativeTime(date))
                }
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { AppHelper.copyToClipboard(context, commit.sha) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = commit.shortSha,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.m3_copy_sha),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (stats != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "+${stats.additions}",
                        container = Color(0x223FB950),
                        content = Color(0xFF7EE787),
                    )
                    Pill(
                        "−${stats.deletions}",
                        container = Color(0x22F85149),
                        content = Color(0xFFFFA198),
                    )
                    Pill(stringResource(R.string.m3_commit_files_changed, commit.files.size))
                }
            }
        }
    }
}

/** One file's change: header (path + added/removed counts) plus the patch coloured line by line. */
@Composable
private fun FileDiff(file: CommitFile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.filename.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (file.status != null && file.status != "modified") {
                    Spacer(Modifier.width(6.dp))
                    Pill(stringResource(fileStatusLabel(file.status!!)))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "+${file.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7EE787),
                )
                Text(
                    text = "−${file.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFA198),
                )
            }
            val patch = file.patch
            if (patch.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.m3_commit_patch_too_big),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                DiffLines(patch)
            }
        }
    }
}

@Composable
private fun DiffLines(patch: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 6.dp),
    ) {
        patch.lineSequence().forEach { line ->
            val (background, textColor) = when {
                line.startsWith("@@") -> null to MaterialTheme.colorScheme.onSurfaceVariant
                line.startsWith("+") -> Color(0x223FB950) to Color(0xFF7EE787)
                line.startsWith("-") -> Color(0x22F85149) to Color(0xFFFFA198)
                line.startsWith("\\") -> null to MaterialTheme.colorScheme.onSurfaceVariant
                else -> null to MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (background != null) Modifier.background(background) else Modifier)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = line.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textColor,
                )
            }
        }
    }
}

private fun fileStatusLabel(status: String): Int = when (status) {
    "added" -> R.string.m3_commit_status_added
    "removed" -> R.string.m3_commit_status_removed
    "renamed" -> R.string.m3_commit_status_renamed
    "copied" -> R.string.m3_commit_status_copied
    "changed" -> R.string.m3_commit_status_changed
    else -> R.string.m3_commit_status_modified
}
