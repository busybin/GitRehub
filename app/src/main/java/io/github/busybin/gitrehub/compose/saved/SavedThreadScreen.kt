package io.github.busybin.gitrehub.compose.saved

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.offline.OfflineSaves
import io.github.busybin.gitrehub.core.offline.SavedThread

/**
 * Read-only rendering of a thread saved earlier: works with the radio off, because nothing here
 * touches the network. A failed lookup (copy deleted) shows an explicit empty state instead of a
 * spinner that never ends.
 */
@Composable
fun SavedThreadScreen(
    owner: String,
    repo: String,
    number: Int,
    onOpenOnline: (String, String, Int) -> Unit,
) {
    val context = LocalContext.current
    var thread by remember { mutableStateOf<SavedThread?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, number) {
        val login = runCatching { Session.currentAccount()?.login }.getOrNull()
        thread = login?.let { account ->
            OfflineSaves.index(account)
                .firstOrNull { it.owner == owner && it.repo == repo && it.number == number }
                ?.let { OfflineSaves.load(context, it) }
        }
        loaded = true
    }

    val saved = thread
    if (loaded && saved == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.m3_saved_missing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (saved == null) return

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.m3_saved_offline_banner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            Text(
                text = saved.issue.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            MetaText("$owner/$repo #$number · ${saved.issue.state}")
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (saved.issue.body.isNullOrBlank()) {
                        Text(stringResource(R.string.m3_issue_no_body))
                    } else {
                        MarkdownText(
                            markdown = saved.issue.body,
                            repo = "$owner/$repo",
                        )
                    }
                }
            }
        }
        items(saved.comments, key = { it.id }) { comment -> SavedComment(comment) }
        item {
            TextButton(
                onClick = { onOpenOnline(owner, repo, number) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.m3_saved_open_online)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SavedComment(comment: Comment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(comment.user?.avatarUrl, size = 20.dp)
                Text(
                    text = "  " + comment.user?.login.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                MetaText(relativeTime(comment.createdAt))
            }
            if (!comment.body.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                MarkdownText(markdown = comment.body)
            }
        }
    }
}
