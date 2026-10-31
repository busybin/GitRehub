package io.github.busybin.gitrehub.compose.releases

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.rememberDownloader
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.core.model.Release
import io.github.busybin.gitrehub.core.model.ReleaseAsset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(
    owner: String,
    repo: String,
    onNewRelease: () -> Unit = {},
    onEditRelease: (Long) -> Unit = {},
    onCompare: (String, String) -> Unit = { _, _ -> },
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
) {
    val vm: ReleasesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val canWrite by vm.canWrite.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current
    val download = rememberDownloader()
    val context = LocalContext.current

    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            vm.refresh()
            onRefreshConsumed()
        }
    }
    val messageText = message?.render()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

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
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }) {
            AsyncContent(state = state, onRetry = { vm.refresh() }) { releases ->
                if (releases.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.m3_no_releases),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@AsyncContent
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(tokens.listPadding),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    itemsIndexed(releases) { index, release ->
                        ReleaseCard(
                            release = release,
                            previousTag = releases.getOrNull(index + 1)?.tagName,
                            canWrite = canWrite,
                            imageBaseUrl = repoRawBaseUrl(owner, repo),
                            linkBaseUrl = repoBlobBaseUrl(owner, repo),
                            repo = "$owner/$repo",
                            onDownload = { url, name -> download(url, name) },
                            onEdit = { onEditRelease(release.id) },
                            onDeleteAsset = { asset -> vm.deleteAsset(owner, repo, asset.id) },
                            onCompare = { base -> release.tagName?.let { onCompare(base, it) } },
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

        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = onNewRelease,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.m3_release_new)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
            )
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    previousTag: String?,
    canWrite: Boolean,
    imageBaseUrl: String,
    linkBaseUrl: String,
    repo: String? = null,
    onDownload: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onCompare: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    var expanded by rememberSaveable(release.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deletingAsset by remember { mutableStateOf<ReleaseAsset?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    MetaText(release.tagName.orEmpty())
                }
                if (canWrite || previousTag != null) {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.m3_more),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (canWrite) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.m3_release_edit)) },
                                    onClick = {
                                        menu = false
                                        onEdit()
                                    },
                                )
                            }
                            if (previousTag != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.m3_release_compare)) },
                                    onClick = {
                                        menu = false
                                        onCompare(previousTag)
                                    },
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.m3_collapse else R.string.m3_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (release.preRelease) Pill(stringResource(R.string.m3_prerelease))
                if (release.draft) Pill(stringResource(R.string.m3_draft))
                MetaText(relativeTime(release.published))
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (!release.body.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        MarkdownText(
                            markdown = release.body,
                            baseUrl = imageBaseUrl,
                            linkBaseUrl = linkBaseUrl,
                            repo = repo,
                        )
                    }
                    val assets = release.assets
                    if (!assets.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.m3_release_assets, assets.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        assets.forEach { asset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = asset.browserDownloadUrl != null) {
                                        asset.browserDownloadUrl?.let { url ->
                                            onDownload(url, asset.name.orEmpty())
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = asset.name.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                MetaText(stringResource(R.string.m3_downloads_count, asset.downloadCount))
                                if (canWrite) {
                                    IconButton(
                                        onClick = { deletingAsset = asset },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.m3_release_delete_asset),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletingAsset?.let { asset ->
        ConfirmDialog(
            title = stringResource(R.string.m3_release_delete_asset),
            message = asset.name.orEmpty(),
            confirmLabel = stringResource(R.string.m3_delete),
            destructive = true,
            onConfirm = {
                val target = deletingAsset
                deletingAsset = null
                if (target != null) onDeleteAsset(target)
            },
            onDismiss = { deletingAsset = null },
        )
    }
}

/** `itemsIndexed` without the import noise above. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    itemContent: @Composable (Int, T) -> Unit,
) {
    items(items.size) { index -> itemContent(index, items[index]) }
}
