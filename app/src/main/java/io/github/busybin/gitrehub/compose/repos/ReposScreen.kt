package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.Hairline
import io.github.busybin.gitrehub.compose.common.Identifier
import io.github.busybin.gitrehub.compose.common.KeepTopOnRefresh
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.SavedPrefs
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
    onOpenLabels: (String, String) -> Unit = { _, _ -> },
    onOpenMilestones: (String, String) -> Unit = { _, _ -> },
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
                            onOpenLabels = onOpenLabels,
                            onOpenMilestones = onOpenMilestones,
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
    val favorites by SavedPrefs.favorites.collectAsStateWithLifecycle()
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
                .horizontalScroll(rememberScrollState())
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo,
                            onOpenRepo = onOpenRepo,
                            highlighted = repo.fullName.isNotBlank() && repo.fullName == selected,
                            favorite = repo.fullName in favorites,
                            onToggleFavorite = { SavedPrefs.toggleFavorite(repo.fullName) },
                            // Only meaningful in the starred list: elsewhere the row has nothing to un-star.
                            onUnstar = if (filter == RepoFilter.STARRED) ({ vm.unstar(repo) }) else null,
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
fun RepoCard(
    repo: Repo,
    onOpenRepo: (String) -> Unit,
    highlighted: Boolean = false,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onUnstar: (() -> Unit)? = null,
) {
    val tokens = LocalTokens.current
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = repo.fullName.isNotBlank(),
                onClick = { onOpenRepo(repo.nameWithOwner) },
                onLongClick = {
                    if (onUnstar != null || onToggleFavorite != null) menu = true
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = tokens.cardShape,
    ) {
        Column(
            Modifier.padding(
                start = tokens.space4,
                end = tokens.space4,
                top = tokens.space3,
                bottom = tokens.space3,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (repo.isPrivate || repo.fork) {
                    Icon(
                        imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.ForkRight,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Identifier(
                    text = repo.fullName.ifBlank { repo.name },
                    maxLines = 1,
                    emphasized = true,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (favorite) R.string.m3_repo_unfavorite else R.string.m3_repo_favorite
                            ),
                            modifier = Modifier.size(tokens.iconSize),
                            tint = if (favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(tokens.space2))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.space3),
            ) {
                if (!repo.language.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Spacer(Modifier.width(6.dp))
                        MetaText(repo.language)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    MetaText(compactNumber(repo.stargazersCount))
                }
                MetaText(
                    stringResource(R.string.m3_repos_updated_ago, relativeTime(repo.pushedAt ?: repo.updatedAt)),
                )
            }
            if (menu) {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onUnstar != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.m3_unstar)) },
                            onClick = {
                                menu = false
                                onUnstar()
                            },
                        )
                    }
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (favorite) R.string.m3_repo_unfavorite else R.string.m3_repo_favorite
                                    )
                                )
                            },
                            onClick = {
                                menu = false
                                onToggleFavorite()
                            },
                        )
                    }
                }
            }
        }
    }
}
