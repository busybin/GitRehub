package io.github.busybin.gitrehub.compose.releases

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.rememberDownloader
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.core.model.Release

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(owner: String, repo: String) {
    val vm: ReleasesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current
    val download = rememberDownloader()

    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }

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

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }) {
        AsyncContent(state = state, onRetry = { vm.refresh() }) { releases ->
            if (releases.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.m3_no_releases), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@AsyncContent
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(tokens.listPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
            ) {
                items(releases, key = { it.id }) { release ->
                    ReleaseCard(
                        release = release,
                        imageBaseUrl = repoRawBaseUrl(owner, repo),
                        linkBaseUrl = repoBlobBaseUrl(owner, repo),
                    ) { url, name ->
                        download(url, name)
                    }
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

@Composable
private fun ReleaseCard(
    release: Release,
    imageBaseUrl: String,
    linkBaseUrl: String,
    onDownload: (String, String) -> Unit,
) {
    val tokens = LocalTokens.current
    var expanded by rememberSaveable(release.id) { mutableStateOf(false) }

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
                            }
                        }
                    }
                }
            }
        }
    }
}

