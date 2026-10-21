package io.github.busybin.gitrehub.compose.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.SkeletonList
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.model.Event

/**
 * Activity feed (GitHub "received events") with pull-to-refresh, infinite scrolling, keyword
 * blocking and an optional "hide what I already read" filter.
 *
 * Everything the feed receives is kept by the ViewModel; keyword/read filtering happens here so that
 * toggling the filter is instant and never refetches.
 *
 * "Mark everything read" is the bottom-right FAB, deliberately identical to the notifications
 * screen's: same icon, same place, same `PUT /notifications` call behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenRepo: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onOpenReleases: (String, String) -> Unit = { _, _ -> },
) {
    val vm: FeedViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    val filter by FeedPrefs.filter.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val readIds by ReadState.readIds.collectAsStateWithLifecycle()
    val readKeys by ReadState.readKeys.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

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

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
        ) {
            AsyncContent(
                state = state,
                onRetry = { vm.refresh() },
                loading = { SkeletonList(rows = 7) },
            ) { events ->
                // Filtering only depends on the loaded page, the filters and the read set.
                val visible = remember(events, filter, me, readIds, readKeys, showAll) {
                    if (showAll) {
                        events
                    } else {
                        events
                            .filter { event -> event.matchesFilter(filter, me, isEventRead(event, readIds, readKeys)) }
                            .let { filtered ->
                                when (filter.order) {
                                    FeedOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                                    FeedOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                                }
                            }
                    }
                }
                val hiddenCount = events.size - visible.size

                if (events.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.m3_feed_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.m3_feed_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    return@AsyncContent
                }



                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(tokens.listPadding),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    if (hiddenCount > 0 || showAll) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetaText(
                                    text = if (showAll) stringResource(R.string.m3_feed_showing_all, hiddenCount)
                                    else stringResource(R.string.m3_feed_hidden, hiddenCount),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { showAll = !showAll }) {
                                    Text(stringResource(if (showAll) R.string.m3_feed_restore else R.string.m3_feed_show_all))
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.m3_feed_all_filtered),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.m3_feed_all_filtered_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(visible, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            tokens = tokens,
                            isRead = isEventRead(event, readIds, readKeys),
                            onOpenUser = onOpenUser,
                            onOpenRepo = { fullName ->
                                markEventRead(event)
                                onOpenRepo(fullName)
                            },
                            onOpen = { clicked ->
                                markEventRead(clicked)
                                when (val target = clicked.feedTarget()) {
                                    is FeedTarget.ForkedRepo -> onOpenRepo(target.fullName)
                                    is FeedTarget.Issue ->
                                        onOpenIssue(target.owner, target.repo, target.number)

                                    is FeedTarget.Releases -> onOpenReleases(target.owner, target.repo)
                                    is FeedTarget.Repo -> onOpenRepo(target.fullName)
                                    is FeedTarget.User -> onOpenUser(target.login)
                                    null -> Unit
                                }
                            },
                        )
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

        if (state is LoadState.Ready) {
            FloatingActionButton(
                onClick = { vm.markAllRead() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
            ) {
                Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.m3_mark_all_read))
            }
        }
    }
}

/**
 * Opening an event from the feed counts as reading it. The event id covers non-thread events;
 * the thread key also marks the opened issue/PR read on the notifications screen.
 */
private fun markEventRead(event: Event) {
    ReadState.markRead(event.id)
    ReadState.markReadKey(ThreadKey.ofEvent(event))
}
