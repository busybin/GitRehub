package io.github.busybin.gitrehub.compose.gists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Gist

/** Gist list: the signed-in account's gists, or a user's public gists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GistsScreen(
    login: String? = null,
    onOpenGist: (Gist) -> Unit,
) {
    val vm: GistsViewModel = viewModel(key = "gists-${login.orEmpty()}")
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    LaunchedEffect(login) { vm.setup(login) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore) {
        if (shouldLoadMore && hasMore) vm.loadMore()
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }) {
        AsyncContent(state = state, onRetry = { vm.refresh() }) { gists ->
            if (gists.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.m3_no_gists),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@AsyncContent
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(tokens.listPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
            ) {
                items(gists, key = { it.id }) { gist ->
                    GistCard(gist, onClick = { onOpenGist(gist) })
                }
                if (loadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    }
                }
            }
        }
    }
}

@Composable
fun GistCard(gist: Gist, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = gist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = gist.filesSorted.joinToString(", ") { it.filename },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                gist.owner?.let { owner ->
                    Avatar(owner.avatarUrl, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = owner.login,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                MetaText(relativeTime(gist.updatedAt ?: gist.createdAt))
                Spacer(Modifier.weight(1f))
                if (!gist.public) {
                    Pill(stringResource(R.string.m3_gist_secret))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(R.string.m3_gist_file_count, gist.files.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (gist.comments > 0) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.m3_gist_comments, gist.comments),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
