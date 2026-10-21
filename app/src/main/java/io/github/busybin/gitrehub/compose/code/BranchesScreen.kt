package io.github.busybin.gitrehub.compose.code

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill

/**
 * Branches / tags of a repository. Tapping one opens the code browser at that ref, which is how you
 * look at code on a phone without being thrown into a browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    owner: String,
    repo: String,
    initialKind: RefKind = RefKind.BRANCH,
    onOpenRef: (String) -> Unit = {},
) {
    val vm: BranchesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    var kind by rememberSaveable(owner, repo) { mutableStateOf(initialKind) }
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

    LaunchedEffect(owner, repo, kind) { vm.setup(owner, repo, kind) }

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

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = kind == RefKind.BRANCH,
                onClick = { kind = RefKind.BRANCH },
                label = { Text(stringResource(R.string.m3_branch)) },
                leadingIcon = { Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = kind == RefKind.TAG,
                onClick = { kind = RefKind.TAG },
                label = { Text(stringResource(R.string.m3_tag)) },
                leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }) {
            AsyncContent(state = state, onRetry = { vm.refresh() }) { refs ->
                if (refs.isEmpty()) {
                    EmptyView(
                        text = stringResource(
                            if (kind == RefKind.BRANCH) R.string.m3_no_branches else R.string.m3_no_tags
                        ),
                        icon = if (kind == RefKind.BRANCH) Icons.Outlined.CallSplit else Icons.Outlined.Label,
                    )
                    return@AsyncContent
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(tokens.listPadding),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    items(refs, key = { "${kind.name}:${it.name}" }) { ref ->
                        RefCard(ref) { onOpenRef(ref.name) }
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
private fun RefCard(ref: RefItem, onClick: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ref.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ref.isProtected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.m3_protected),
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!ref.message.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ref.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ref.isDefault) Pill(stringResource(R.string.m3_default_pill))
                    if (!ref.sha.isNullOrBlank()) MetaText(ref.sha.take(7))
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
