package io.github.busybin.gitrehub.compose.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
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
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        vm.search("", scope)
                    }) { Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.m3_clear)) }
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

        Spacer(Modifier.height(8.dp))

        when (val current = state) {
            null -> EmptyView(stringResource(R.string.m3_search_hint), icon = Icons.Filled.Search)
            else -> AsyncContent(state = current, onRetry = { vm.retry() }) { items ->
                if (items.isEmpty()) {
                    EmptyView(stringResource(R.string.m3_no_results))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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

@Composable
private fun RepoRow(repo: Repo, onOpenRepo: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRepo(repo.nameWithOwner) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = repo.nameWithOwner,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = repo.description!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!repo.language.isNullOrBlank()) Pill(repo.language!!)
                MetaText("★ ${compactNumber(repo.stargazersCount)}")
            }
        }
    }
}

@Composable
private fun UserRow(user: User, onOpenUser: (String) -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !user.login.isNullOrBlank()) {
                user.login?.let(onOpenUser)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.avatarUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Column {
                    Text(
                        text = user.login.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.type != null) MetaText(user.type!!)
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !owner.isNullOrBlank() && !name.isNullOrBlank()) {
                if (owner != null && name != null) onOpenIssue(owner, name, issue.number)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = issue.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!fullName.isNullOrBlank()) MetaText(fullName)
                Pill(
                    stringResource(
                        when {
                            issue.isClosed -> R.string.m3_issue_closed
                            isPull -> R.string.m3_notif_pr
                            else -> R.string.m3_issue_open
                        }
                    )
                )
                MetaText("#${issue.number}")
            }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !owner.isNullOrBlank() && !name.isNullOrBlank() && !file.path.isNullOrBlank()) {
                if (owner != null && name != null && file.path != null) {
                    onOpenCode(owner, name, file.path!!, ref)
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = file.path.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (!fullName.isNullOrBlank()) MetaText(fullName)
        }
    }
}
