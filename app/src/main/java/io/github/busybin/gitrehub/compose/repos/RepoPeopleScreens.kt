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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.CompactButton
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore
import io.github.busybin.gitrehub.helper.BrowserLauncher

/** Who starred a repository. */
class StargazersViewModel : PagedViewModel<User>() {
    private var owner = ""
    private var repo = ""

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<User> {
        val response = GitHubApi.stargazers(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }

    /** GitHub 404s the list for repositories this account does not administer; say so instead. */
    override fun errorText(t: Throwable): UiText =
        if ((t as? ApiException)?.error?.status == 404) UiText.res(R.string.m3_stargazers_unavailable)
        else super.errorText(t)
}

/** Who contributed to a repository (each row carries the commit count GitHub reports). */
class ContributorsViewModel : PagedViewModel<User>() {
    private var owner = ""
    private var repo = ""

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<User> {
        val response = GitHubApi.contributors(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }
}

/** The forks of a repository. */
class ForksViewModel : PagedViewModel<Repo>() {
    private var owner = ""
    private var repo = ""

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Repo> {
        val response = GitHubApi.forks(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StargazersScreen(owner: String, repo: String, onOpenUser: (String) -> Unit) {
    val vm: StargazersViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }
    val failure = state as? LoadState.Failure
    if ((failure?.cause as? ApiException)?.error?.status == 404) {
        EmptyView(
            text = stringResource(R.string.m3_stargazers_unavailable),
            action = {
                CompactButton(
                    text = stringResource(R.string.m3_open_on_github),
                    onClick = {
                        BrowserLauncher.open(context, "https://github.com/$owner/$repo/stargazers")
                    },
                )
            },
        )
        return
    }
    UserList(
        state = state,
        refreshing = vm.refreshing.collectAsStateWithLifecycle().value,
        hasMore = vm.hasMore.collectAsStateWithLifecycle().value,
        loadingMore = vm.loadingMore.collectAsStateWithLifecycle().value,
        onRefresh = { vm.refresh() },
        onLoadMore = { vm.loadMore() },
        emptyText = stringResource(R.string.m3_no_stargazers),
        onOpenUser = onOpenUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorsScreen(owner: String, repo: String, onOpenUser: (String) -> Unit) {
    val vm: ContributorsViewModel = viewModel()
    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }
    UserList(
        state = vm.state.collectAsStateWithLifecycle().value,
        refreshing = vm.refreshing.collectAsStateWithLifecycle().value,
        hasMore = vm.hasMore.collectAsStateWithLifecycle().value,
        loadingMore = vm.loadingMore.collectAsStateWithLifecycle().value,
        onRefresh = { vm.refresh() },
        onLoadMore = { vm.loadMore() },
        emptyText = stringResource(R.string.m3_no_contributors),
        showContributions = true,
        onOpenUser = onOpenUser,
    )
}

/**
 * Who watches a repository. REST `/subscribers` is admin-only in practice (404 for other people's
 * repositories), so this list comes from GraphQL, which is readable for every public repository.
 */
class WatchersViewModel : PagedViewModel<User>() {
    private var owner = ""
    private var repo = ""
    private var cursor: String? = null

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        cursor = null
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<User> {
        if (page == 1) cursor = null
        val result = GitHubApi.repoWatchers(owner, repo, cursor)
        cursor = result.cursor
        return PageResult(result.users, result.hasNext)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchersScreen(owner: String, repo: String, onOpenUser: (String) -> Unit) {
    val vm: WatchersViewModel = viewModel()
    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }
    UserList(
        state = vm.state.collectAsStateWithLifecycle().value,
        refreshing = vm.refreshing.collectAsStateWithLifecycle().value,
        hasMore = vm.hasMore.collectAsStateWithLifecycle().value,
        loadingMore = vm.loadingMore.collectAsStateWithLifecycle().value,
        onRefresh = { vm.refresh() },
        onLoadMore = { vm.loadMore() },
        emptyText = stringResource(R.string.m3_no_watchers),
        onOpenUser = onOpenUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserList(
    state: io.github.busybin.gitrehub.compose.common.LoadState<List<User>>,
    refreshing: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String,
    showContributions: Boolean = false,
    onOpenUser: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore) { if (shouldLoadMore && hasMore) onLoadMore() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        AsyncContent(state = state, onRetry = onRefresh) { users ->
            if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@AsyncContent
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(tokens.listPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
            ) {
                items(users, key = { it.id }) { user ->
                    UserRow(user, showContributions) { onOpenUser(user.login) }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: User, showContributions: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(tokens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.avatarUrl, size = tokens.avatarSize)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.login,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!user.name.isNullOrBlank()) {
                    MetaText(user.name)
                }
            }
            if (showContributions && user.contributions > 0) {
                Pill(stringResource(R.string.m3_commits_count, user.contributions))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForksScreen(owner: String, repo: String, onOpenRepo: (String) -> Unit) {
    val vm: ForksViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
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
    LaunchedEffect(shouldLoadMore, hasMore) { if (shouldLoadMore && hasMore) vm.loadMore() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        AsyncContent(state = state, onRetry = { vm.refresh() }) { forks ->
            if (forks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.m3_no_forks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@AsyncContent
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(tokens.listPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
            ) {
                items(forks, key = { it.id }) { fork ->
                    RepoCard(repo = fork, onOpenRepo = onOpenRepo)
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
