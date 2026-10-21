package io.github.busybin.gitrehub.compose.discussions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.FailureView
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.SkeletonList
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.compose.data.DiscussionsDisabledException

/**
 * Repository discussions. The official client does not show these at all, and on the web they are a
 * separate tab people only open in a browser — so this screen (and its detail page) is deliberately
 * native, like issues are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionsScreen(
    owner: String,
    repo: String,
    onOpenDiscussion: (Int) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
) {
    val vm: DiscussionsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }

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
        when (val current = state) {
            is LoadState.Loading -> SkeletonList()

            is LoadState.Failure ->
                // A 404 here means "this repository has discussions turned off", which is a fact
                // about the repository, not something the user can retry away.
                if (current.cause is DiscussionsDisabledException) {
                    EmptyView(
                        text = stringResource(R.string.m3_discussions_disabled),
                        icon = Icons.Outlined.ChatBubbleOutline,
                    )
                } else {
                    FailureView(current.message, onRetry = { vm.refresh() })
                }

            is LoadState.Ready -> {
                val discussions = current.value
                if (discussions.isEmpty()) {
                    EmptyView(
                        text = stringResource(R.string.m3_no_discussions),
                        icon = Icons.Outlined.ChatBubbleOutline,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(tokens.listPadding),
                        verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                    ) {
                        items(discussions, key = { it.number }) { discussion ->
                            DiscussionCard(discussion, onOpenDiscussion, onOpenUser)
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscussionCard(
    discussion: Discussion,
    onOpenDiscussion: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDiscussion(discussion.number) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Row(
            modifier = Modifier.padding(tokens.cardPadding),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (discussion.isAnswered || discussion.isClosed) {
                    Icons.Outlined.TaskAlt
                } else {
                    Icons.Outlined.ChatBubbleOutline
                },
                contentDescription = null,
                tint = when {
                    discussion.isAnswered -> Color(0xFF1A7F37)
                    discussion.isClosed -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = discussion.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val meta = buildString {
                    append("#${discussion.number}")
                    discussion.user?.login?.let { append(" · @$it") }
                    append(" · ${relativeTime(discussion.createdAt)}")
                }
                MetaText(meta)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    discussion.category?.name?.takeIf { it.isNotBlank() }?.let { Pill(it) }
                    if (discussion.isAnswered) {
                        Pill(
                            stringResource(R.string.m3_discussion_answered),
                            leadingDot = Color(0xFF1A7F37),
                        )
                    }
                    if (discussion.isClosed) Pill(stringResource(R.string.m3_discussion_closed))
                    if (discussion.comments > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                            MetaText("${discussion.comments}")
                        }
                    }
                }
            }
            Avatar(
                url = discussion.user?.avatarUrl,
                size = if (tokens.compact) 22.dp else 28.dp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(enabled = discussion.user?.login != null) {
                        discussion.user?.login?.let(onOpenUser)
                    },
            )
        }
    }
}
