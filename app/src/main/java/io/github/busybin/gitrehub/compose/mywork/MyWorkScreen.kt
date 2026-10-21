package io.github.busybin.gitrehub.compose.mywork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Issue

/**
 * "My work": assigned issues, review requests, mentions and my open items — one search-backed list
 * per section, each row opening the issue / pull request it points at.
 *
 * Rows are read as they are opened (the badge counts unread rows), and "mark all read" clears the
 * whole section. Deliberately a dense list, not a dashboard: the official app's dashboard redesign
 * is one of the things users complain about.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyWorkScreen(onOpenIssue: (String, String, Int) -> Unit) {
    val vm: MyWorkViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val readKeys by ReadState.readKeys.collectAsStateWithLifecycle()
    val readKeyTimes by ReadState.readKeyTimes.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.start() }

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
        Column(Modifier.fillMaxSize()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MyWorkSection.entries.forEach { entry ->
                    FilterChip(
                        selected = section == entry,
                        onClick = { vm.select(entry) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncContent(state = state, onRetry = { vm.refresh() }) { issues ->
                    if (issues.isEmpty()) {
                        EmptyView(stringResource(R.string.m3_my_work_empty))
                        return@AsyncContent
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(issues, key = { it.id }) { issue ->
                            val key = ThreadKey.ofIssue(issue)
                            val unread = key != null &&
                                !ThreadKey.isRead(key, issue.updatedAt?.toEpochMilli(), readKeys, readKeyTimes)
                            MyWorkRow(issue, unread = unread) {
                                val fullName = issue.repoFullName
                                val owner = fullName?.substringBefore('/')
                                val name = fullName?.substringAfter('/', "")
                                if (!owner.isNullOrBlank() && !name.isNullOrBlank()) {
                                    vm.markRead(issue)
                                    onOpenIssue(owner, name, issue.number)
                                }
                            }
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                            }
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
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = stringResource(R.string.m3_my_work_mark_all_read),
                )
            }
        }
    }
}

@Composable
private fun MyWorkRow(issue: Issue, unread: Boolean, onOpen: () -> Unit) {
    val fullName = issue.repoFullName
    val owner = fullName?.substringBefore('/')
    val name = fullName?.substringAfter('/', "")
    val isPull = issue.pullRequest != null
    val open = !issue.isClosed
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !owner.isNullOrBlank() && !name.isNullOrBlank(), onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = issue.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isPull) Icons.Outlined.CallMerge else Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = if (open) Color(0xFF3FB950) else Color(0xFFA371F7),
                    modifier = Modifier.size(16.dp),
                )
                if (!fullName.isNullOrBlank()) {
                    MetaText(fullName)
                }
                MetaText("#${issue.number}")
                Spacer(Modifier.weight(1f))
                MetaText(relativeTime(issue.updatedAt))
            }
        }
    }
}
