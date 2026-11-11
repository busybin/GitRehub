package io.github.busybin.gitrehub.compose.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.Hairline
import io.github.busybin.gitrehub.compose.common.Identifier
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.SavedPrefs
import io.github.busybin.gitrehub.compose.common.SavedSearch
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.SearchCode
import io.github.busybin.gitrehub.core.model.User

@Composable
fun SearchScreen(
    onOpenRepo: (String) -> Unit,
    onOpenUser: (String) -> Unit = {},
    onOpenIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onOpenCode: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
) {
    val vm: SearchViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.REPOS) }
    val keyboard = LocalSoftwareKeyboardController.current
    val saved by SavedPrefs.searches.collectAsStateWithLifecycle()
    val isSaved = SavedPrefs.isSaved(query, scope.name)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.m3_search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = {
                            if (isSaved) {
                                SavedPrefs.removeSearch(SavedSearch(query.trim(), scope.name))
                            } else {
                                SavedPrefs.saveSearch(query, scope.name)
                            }
                        }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark
                                else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (isSaved) R.string.m3_search_unsave else R.string.m3_search_save
                                ),
                            )
                        }
                        IconButton(onClick = {
                            query = ""
                            vm.search("", scope)
                        }) { Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.m3_clear)) }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                vm.search(query, scope)
            }),
        )

        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchScope.entries.forEach { entry ->
                FilterChip(
                    selected = scope == entry,
                    onClick = {
                        scope = entry
                        if (query.isNotBlank()) vm.search(query, entry)
                    },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        }

        if (saved.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(saved, key = { "${it.scope}\t${it.query}" }) { item ->
                    val targetScope = SearchScope.entries.firstOrNull { it.name == item.scope }
                        ?: SearchScope.REPOS
                    FilterChip(
                        selected = query == item.query && scope == targetScope,
                        onClick = {
                            query = item.query
                            scope = targetScope
                            keyboard?.hide()
                            vm.search(item.query, targetScope)
                        },
                        label = { Text(item.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            IconButton(
                                onClick = { SavedPrefs.removeSearch(item) },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.m3_search_unsave),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (val current = state) {
            null -> EmptyView(stringResource(R.string.m3_search_hint), icon = Icons.Filled.Search)
            else -> AsyncContent(state = current, onRetry = { vm.retry() }) { items ->
                if (items.isEmpty()) {
                    EmptyView(stringResource(R.string.m3_no_results))
                } else {
                    val tokens = LocalTokens.current
                    LazyColumn(contentPadding = PaddingValues(vertical = tokens.space2)) {
                        items(items.size) { index ->
                            when (val item = items[index]) {
                                is SearchItem.RepoHit -> RepoRow(item.repo, onOpenRepo)
                                is SearchItem.UserHit -> UserRow(item.user, onOpenUser)
                                is SearchItem.IssueHit -> IssueRow(item.issue, onOpenIssue)
                                is SearchItem.CodeHit -> CodeRow(item.file, onOpenCode)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One search hit, laid out like every other list in the app: a flat row with a hairline, no card.
 * Cards here made results heavier than the feed while carrying less information.
 */
@Composable
private fun ResultRow(onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = tokens.pagePadding, vertical = tokens.space3),
        content = content,
    )
    Hairline(Modifier.padding(horizontal = tokens.pagePadding))
}

@Composable
private fun RepoRow(repo: Repo, onOpenRepo: (String) -> Unit) {
    ResultRow(onClick = { onOpenRepo(repo.nameWithOwner) }) {
        Identifier(repo.nameWithOwner, emphasized = true, maxLines = 1)
        if (!repo.description.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = repo.description!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!repo.language.isNullOrBlank()) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(6.dp))
                MetaText(repo.language!!)
                Spacer(Modifier.width(12.dp))
            }
            Icon(
                imageVector = Icons.Outlined.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            MetaText(compactNumber(repo.stargazersCount))
        }
    }
}

@Composable
private fun UserRow(user: User, onOpenUser: (String) -> Unit = {}) {
    ResultRow(
        onClick = user.login?.takeIf { it.isNotBlank() }?.let { login -> { onOpenUser(login) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.avatarUrl, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Identifier(user.login.orEmpty(), emphasized = true, maxLines = 1)
                user.type?.let { MetaText(it) }
            }
        }
    }
}

/** A found issue/PR: open the native detail screen. */
@Composable
private fun IssueRow(issue: Issue, onOpenIssue: (String, String, Int) -> Unit) {
    val fullName = issue.repoFullName
    val owner = fullName?.substringBefore('/')
    val name = fullName?.substringAfter('/', "")
    val isPull = issue.pullRequest != null
    ResultRow(
        onClick = if (!owner.isNullOrBlank() && !name.isNullOrBlank()) {
            { onOpenIssue(owner, name, issue.number) }
        } else {
            null
        },
    ) {
        Text(
            text = issue.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!fullName.isNullOrBlank()) {
                MetaText(
                    if (isPull) "$fullName#${issue.number}" else fullName,
                    mono = true,
                )
            }
            Pill(
                stringResource(
                    when {
                        issue.isClosed -> R.string.m3_issue_closed
                        isPull -> R.string.m3_notif_pr
                        else -> R.string.m3_issue_open
                    }
                )
            )
            if (isPull) MetaText("#${issue.number}")
        }
    }
}

/** A found code hit: open the file viewer (with the ref when we know it). */
@Composable
private fun CodeRow(
    file: SearchCode,
    onOpenCode: (String, String, String, String?) -> Unit,
) {
    val repo = file.repository
    val fullName = file.repoFullName
    val owner = fullName?.substringBefore('/')
    val name = fullName?.substringAfter('/', "")
    val ref = repo?.defaultBranch
    val path = file.path
    ResultRow(
        onClick = if (!owner.isNullOrBlank() && !name.isNullOrBlank() && !path.isNullOrBlank()) {
            { onOpenCode(owner, name, path!!, ref) }
        } else {
            null
        },
    ) {
        if (!fullName.isNullOrBlank()) {
            Identifier("$fullName/${path.orEmpty()}", maxLines = 2)
        } else {
            Identifier(path.orEmpty(), maxLines = 2)
        }
    }
}
