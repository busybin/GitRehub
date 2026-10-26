package io.github.busybin.gitrehub.compose.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.ToggleActionButton
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.data.PinnedRepo
import io.github.busybin.gitrehub.compose.gists.GistCard
import io.github.busybin.gitrehub.compose.repos.RepoCard
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.User

/**
 * Another user's page — what tapping a feed row's avatar or name opens.
 *
 * It used to be impossible to get here at all: the feed row was a single tap target for the
 * *repository* the event was about, so "who did this?" had nowhere to go.
 */
@Composable
fun UserProfileScreen(
    login: String,
    onOpenRepo: (String) -> Unit,
    onOpenUser: (String) -> Unit = {},
    onOpenGist: (Gist) -> Unit = {},
) {
    val vm: UserProfileViewModel = viewModel(key = "user-$login")
    val state by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val followers by vm.followers.collectAsStateWithLifecycle()
    val gists by vm.gists.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val followBusy by vm.followBusy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val messageText = message?.render()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    LaunchedEffect(login) { vm.load(login) }

    AsyncContent(state = state, onRetry = { vm.load(login) }) { page ->
        val user = page.user.toUserInfo()
        val noStarred = stringResource(R.string.m3_no_starred)
        val noFollowing = stringResource(R.string.m3_no_following)
        val noFollowers = stringResource(R.string.m3_no_followers)
        val noRepos = stringResource(R.string.m3_no_public_repos)
        val noMembers = stringResource(R.string.m3_no_members)
        val noGists = stringResource(R.string.m3_no_gists)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ProfileHeader(
                    user = user,
                    onOpenUrl = { url -> openInBrowser(context, url) },
                    actions = {
                        if (!page.isSelf) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToggleActionButton(
                                    active = page.following,
                                    enabled = !followBusy,
                                    icon = if (page.following) Icons.Filled.Person else Icons.Outlined.PersonAdd,
                                    label = stringResource(
                                        if (page.following) R.string.m3_follow_done else R.string.m3_follow
                                    ),
                                    onClick = { vm.toggleFollow() },
                                )
                            }
                        }
                    },
                )
            }

            if (page.pinned.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.m3_pinned_repos),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(page.pinned, key = { it.nameWithOwner }) { repo ->
                            PinnedRepoCard(repo) { onOpenRepo(repo.nameWithOwner) }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    UserTab.entries.filter { entry ->
                        if (page.isOrg) {
                            entry == UserTab.REPOS || entry == UserTab.MEMBERS
                        } else {
                            entry != UserTab.MEMBERS
                        }
                    }.forEach { entry ->
                        FilterChip(
                            selected = tab == entry,
                            onClick = { vm.selectTab(login, entry) },
                            label = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }

            when (tab) {
                UserTab.REPOS -> {
                    if (page.repos.isEmpty()) {
                        item { EmptyNote(noRepos) }
                    } else {
                        items(page.repos, key = { it.id }) { repo -> RepoCard(repo, onOpenRepo) }
                    }
                }

                UserTab.STARRED -> ListSection(
                    state = starred,
                    onRetry = { vm.selectTab(login, UserTab.STARRED, force = true) },
                    empty = noStarred,
                ) { repos ->
                    items(repos, key = { it.id }) { repo -> RepoCard(repo, onOpenRepo) }
                }

                UserTab.GISTS -> ListSection(
                    state = gists,
                    onRetry = { vm.selectTab(login, UserTab.GISTS, force = true) },
                    empty = noGists,
                ) { list ->
                    items(list, key = { it.id }) { gist ->
                        GistCard(gist) { onOpenGist(gist) }
                    }
                }

                UserTab.FOLLOWING -> UserSection(
                    state = following,
                    onRetry = { vm.selectTab(login, UserTab.FOLLOWING, force = true) },
                    empty = noFollowing,
                    onOpenUser = onOpenUser,
                )

                UserTab.FOLLOWERS -> UserSection(
                    state = followers,
                    onRetry = { vm.selectTab(login, UserTab.FOLLOWERS, force = true) },
                    empty = noFollowers,
                    onOpenUser = onOpenUser,
                )

                UserTab.MEMBERS -> UserSection(
                    state = members,
                    onRetry = { vm.selectTab(login, UserTab.MEMBERS, force = true) },
                    empty = noMembers,
                    onOpenUser = onOpenUser,
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { openInBrowser(context, user.htmlUrl ?: "https://github.com/$login") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.m3_open_profile_browser)) }
            }
        }
    }
}

/** One list section (starred repos, or users): loading / failed / empty all end up here. */
private fun <T> LazyListScope.ListSection(
    state: LoadState<List<T>>?,
    onRetry: () -> Unit,
    empty: String,
    content: LazyListScope.(List<T>) -> Unit,
) {
    when (state) {
        null, LoadState.Loading -> item { LoadingNote() }
        is LoadState.Failure -> item { FailureNote(state.message.render(), onRetry) }
        is LoadState.Ready ->
            if (state.value.isEmpty()) item { EmptyNote(empty) } else content(state.value)
    }
}

/** Following / followers: one column of users, tap to open their profile. */
private fun LazyListScope.UserSection(
    state: LoadState<List<User>>?,
    onRetry: () -> Unit,
    empty: String,
    onOpenUser: (String) -> Unit,
) = ListSection(state, onRetry, empty) { users ->
    items(users, key = { it.id }) { user -> UserRow(user, onOpenUser) }
}

@Composable
private fun UserRow(user: User, onOpenUser: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = user.login.isNotBlank()) { onOpenUser(user.login) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.avatarUrl, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = user.login,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun LoadingNote() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun FailureNote(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.m3_retry)) }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** One pinned repository: name, description, stars and language, in a fixed-width card. */
@Composable
private fun PinnedRepoCard(repo: PinnedRepo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(230.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = repo.nameWithOwner,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val description = repo.description
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${repo.stars}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val language = repo.language
                if (!language.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
