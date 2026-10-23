package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.KeepTopOnRefresh
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.compose.common.isWideLayout
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(
    onOpenRepo: (String) -> Unit,
    onOpenIssues: (String, String) -> Unit = { _, _ -> },
    onOpenReleases: (String, String) -> Unit = { _, _ -> },
    onOpenCode: (String, String) -> Unit = { _, _ -> },
    onOpenBranches: (String, String) -> Unit = { _, _ -> },
    onOpenDiscussions: (String, String) -> Unit = { _, _ -> },
    onOpenActions: (String, String) -> Unit = { _, _ -> },
    onOpenPulls: (String, String) -> Unit = { _, _ -> },
    onOpenTags: (String, String) -> Unit = { _, _ -> },
    onOpenContributors: (String, String) -> Unit = { _, _ -> },
    onOpenStargazers: (String, String) -> Unit = { _, _ -> },
    onOpenWatchers: (String, String) -> Unit = { _, _ -> },
    onOpenForks: (String, String) -> Unit = { _, _ -> },
    onOpenActivity: (String, String) -> Unit = { _, _ -> },
    onOpenUser: (String) -> Unit = {},
) {
    val wide = isWideLayout()
    // On wide screens the list and repo detail sit side by side; picking one only swaps the right column.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    if (wide) {
        // Wide screens always show two columns, left list and right detail (or a hint); previously the
        // split only appeared after selecting, which was inconsistent and made the viewport jump.
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f)) {
                ReposList(
                    onOpenRepo = { selected = it },
                    selected = selected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            VerticalDivider()
            Box(Modifier.weight(0.58f)) {
                val fullName = selected
                if (fullName == null) {
                    EmptyView(text = stringResource(R.string.m3_repo_pick_one))
                } else {
                    val owner = fullName.substringBefore('/')
                    val name = fullName.substringAfter('/')
                    // Key on the name so the detail ViewModel is recreated when the repo changes.
                    key(fullName) {
                        RepoDetailScreen(
                            owner = owner,
                            name = name,
                            onOpenIssues = onOpenIssues,
                            onOpenPulls = onOpenPulls,
                            onOpenReleases = onOpenReleases,
                            onOpenCode = onOpenCode,
                            onOpenBranches = onOpenBranches,
                            onOpenTags = onOpenTags,
                            onOpenContributors = onOpenContributors,
                            onOpenStargazers = onOpenStargazers,
                            onOpenWatchers = onOpenWatchers,
                            onOpenForks = onOpenForks,
                            onOpenDiscussions = onOpenDiscussions,
                            onOpenActions = onOpenActions,
                            onOpenActivity = onOpenActivity,
                            onOpenUser = onOpenUser,
                            onOpenRepo = { o, r -> onOpenRepo("$o/$r") },
                        )
                    }
                }
            }
        }
    } else {
        ReposList(
            onOpenRepo = onOpenRepo,
            selected = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReposList(
    onOpenRepo: (String) -> Unit,
    selected: String?,
    modifier: Modifier = Modifier,
) {
    val vm: ReposViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    KeepTopOnRefresh(listState, refreshing)
    val filter = vm.filter
    val tokens = LocalTokens.current

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

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RepoFilter.entries.forEach { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { vm.setFilter(entry) },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncContent(state = state, onRetry = { vm.refresh() }) { repos ->
                if (repos.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.m3_no_repos), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@AsyncContent
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(tokens.listPadding),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo,
                            onOpenRepo = onOpenRepo,
                            highlighted = repo.fullName.isNotBlank() && repo.fullName == selected,
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
    }
}

@Composable
fun RepoCard(repo: Repo, onOpenRepo: (String) -> Unit, highlighted: Boolean = false) {
    val tokens = LocalTokens.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = repo.fullName.isNotBlank()) { onOpenRepo(repo.nameWithOwner) },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = tokens.cardShape,
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.ForkRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = repo.fullName.ifBlank { repo.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (repo.fork) {
                    Pill("fork")
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!repo.language.isNullOrBlank()) {
                    Pill(repo.language, leadingDot = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    MetaText(compactNumber(repo.stargazersCount))
                }
                MetaText(stringResource(R.string.m3_repos_updated_ago, relativeTime(repo.pushedAt ?: repo.updatedAt)))
            }
        }
    }
}
