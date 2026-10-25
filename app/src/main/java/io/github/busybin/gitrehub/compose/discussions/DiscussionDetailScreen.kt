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
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.helper.BrowserLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionDetailScreen(
    owner: String,
    repo: String,
    number: Int,
    onOpenUser: (String) -> Unit = {},
) {
    val vm: DiscussionDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val commentsLoadingMore by vm.commentsLoadingMore.collectAsStateWithLifecycle()
    val commentsHasMore by vm.commentsHasMore.collectAsStateWithLifecycle()
    val commentsRefreshing by vm.commentsRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    LaunchedEffect(owner, repo, number) { vm.load(owner, repo, number) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, commentsHasMore) {
        if (shouldLoadMore && commentsHasMore) vm.loadMoreComments()
    }

    PullToRefreshBox(
        isRefreshing = commentsRefreshing,
        onRefresh = { vm.refreshComments() },
    ) {
        AsyncContent(
            state = state,
            onRetry = { vm.load(owner, repo, number, force = true) },
        ) { discussion ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(tokens.listPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
            ) {
                item(key = "header") {
                    DiscussionHeader(discussion, owner, repo)
                }

                when (val current = comments) {
                    is LoadState.Loading -> item(key = "comments-loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    }

                    is LoadState.Failure -> item(key = "comments-failure") {
                        Box(
                            Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { vm.refreshComments() }) {
                                Text(stringResource(R.string.m3_load_more_failed))
                            }
                        }
                    }

                    is LoadState.Ready -> items(current.value, key = { it.id }) { comment ->
                        CommentCard(comment, owner, repo, onOpenUser)
                    }
                }

                if (commentsLoadingMore) {
                    item(key = "comments-more") {
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
private fun DiscussionHeader(discussion: Discussion, owner: String, repo: String) {
    val context = LocalContext.current
    var pendingLink by remember { mutableStateOf<String?>(null) }

    Column {
        Text(
            text = discussion.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = if (discussion.isClosed) {
                    stringResource(R.string.m3_issue_closed)
                } else {
                    stringResource(R.string.m3_issue_open)
                },
                leadingDot = if (discussion.isClosed) null else Color(0xFF1A7F37),
            )
            discussion.category?.name?.takeIf { it.isNotBlank() }?.let { Pill(it) }
            if (discussion.isAnswered) {
                Pill(stringResource(R.string.m3_discussion_answered), leadingDot = Color(0xFF1A7F37))
            }
        }
        Spacer(Modifier.height(6.dp))
        val meta = buildString {
            append("#${discussion.number}")
            discussion.user?.login?.let { append(" · @$it") }
            append(" · ${relativeTime(discussion.createdAt)}")
            if (discussion.comments > 0) append(" · ${discussion.comments} 💬")
        }
        MetaText(meta)

        if (!discussion.body.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            MarkdownText(
                markdown = discussion.body,
                baseUrl = repoRawBaseUrl(owner, repo),
                linkBaseUrl = repoBlobBaseUrl(owner, repo),
                repo = "$owner/$repo",
            )
        }

        if (discussion.comments > 0) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.m3_comments_count, discussion.comments),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    pendingLink?.let { LinkCopyDialog(url = it, onDismiss = { pendingLink = null }) }
}

@Composable
private fun CommentCard(
    comment: DiscussionComment,
    owner: String,
    repo: String,
    onOpenUser: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    url = comment.user?.avatarUrl,
                    size = if (tokens.compact) 22.dp else 26.dp,
                    modifier = Modifier.clickable(enabled = comment.user?.login != null) {
                        comment.user?.login?.let(onOpenUser)
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = comment.user?.login?.let { "@$it" } ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    MetaText(relativeTime(comment.createdAt))
                }
                // The chosen answer has no marker in the REST payload, so only the association is shown.
                associationLabel(comment.authorAssociation)?.let { Pill(it) }
            }
            if (!comment.body.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                MarkdownText(
                    markdown = comment.body,
                    baseUrl = repoRawBaseUrl(owner, repo),
                    linkBaseUrl = repoBlobBaseUrl(owner, repo),
                    repo = "$owner/$repo",
                )
            }
        }
    }
}

private fun associationLabel(association: String?): String? = when (association?.lowercase()) {
    "owner" -> "Owner"
    "member" -> "Member"
    "collaborator" -> "Collaborator"
    "contributor" -> "Contributor"
    else -> null
}
