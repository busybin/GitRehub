package io.github.busybin.gitrehub.compose.code

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.helper.BrowserLauncher
import java.util.Locale

/** The finder renders at most this many rows; typing one more character narrows it down. */
private const val MAX_FINDER_RESULTS = 100

/**
 * Repository file tree ("code browsing") — the biggest thing the official mobile client cannot do:
 * you can list directories and read files without being thrown into a browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeBrowserScreen(
    owner: String,
    repo: String,
    ref: String? = null,
    onOpenFile: (path: String) -> Unit = {},
    onOpenBranches: () -> Unit = {},
    /** Tapping the "latest commit" line of the listing opens that commit's diff. */
    onOpenCommit: (sha: String) -> Unit = {},
    /** See the commit history of this ref (`resolvedRef` already resolved the default branch). */
    onOpenCommits: (resolvedRef: String) -> Unit = {},
) {
    val vm: CodeBrowserViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = LocalTokens.current
    val fileIndex by vm.fileIndex.collectAsStateWithLifecycle()
    var showFinder by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(owner, repo, ref) { vm.setup(owner, repo, ref) }
    LaunchedEffect(showFinder) {
        if (showFinder) {
            vm.loadFileIndex()
            runCatching { focus.requestFocus() }
        }
    }

    AsyncContent(state = state, onRetry = { vm.refresh() }) { listing ->
            Column(Modifier.fillMaxWidth()) {
                // ----- ref + breadcrumb
                val segments = remember(listing.path) {
                    listing.path.split('/').filter { it.isNotEmpty() }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onOpenBranches) {
                            Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                listing.ref ?: stringResource(R.string.m3_default_branch),
                                maxLines = 1,
                            )
                        }
                        if (listing.ref != null) {
                            IconButton(onClick = { onOpenCommits(listing.ref.orEmpty()) }) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = stringResource(R.string.m3_commits),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Crumb(repo) { vm.open("") }
                        segments.forEachIndexed { index, segment ->
                            Crumb("/") {}
                            val target = segments.take(index + 1).joinToString("/")
                            Crumb(segment) { vm.open(target) }
                        }
                    }
                    IconButton(onClick = {
                        showFinder = !showFinder
                        if (!showFinder) query = ""
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.m3_find_file),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    TextButton(
                        onClick = {
                            val url = "https://github.com/$owner/$repo/tree/${listing.ref.orEmpty().ifBlank { "HEAD" }}/${listing.path}"
                            BrowserLauncher.open(context, url)
                        }
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.m3_open_in_browser),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // The listing's "who committed, when, message" line (like the web listing page).
                listing.lastCommit?.let { commit ->
                    val author = commit.authorName
                    val date = commit.date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = commit.sha.isNotBlank()) {
                                onOpenCommit(commit.sha)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(commit.author?.avatarUrl, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        if (author != null) {
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (date != null) {
                            Text(
                                text = stringResource(R.string.m3_last_modified, relativeTime(date)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = commit.messageTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (showFinder) {
                    FileFinder(
                        index = fileIndex,
                        query = query,
                        onQueryChange = { query = it },
                        focus = focus,
                        onRetry = { vm.loadFileIndex(force = true) },
                        onOpenFile = { path -> onOpenFile(path) },
                    )
                    return@AsyncContent
                }
                if (listing.entries.isEmpty()) {
                    EmptyView(text = stringResource(R.string.m3_dir_empty), icon = Icons.Outlined.FolderOpen)
                    return@AsyncContent
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = tokens.listPadding, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(listing.entries, key = { it.path ?: it.name ?: it.sha ?: "entry" }) { entry ->
                        EntryRow(entry) {
                            val path = entry.path ?: entry.name ?: return@EntryRow
                            if (entry.isDir) vm.open(path) else onOpenFile(path)
                        }
                    }
                }
            }
    }
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (text == "/") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clickable(enabled = text != "/") { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/** File finder: filters the recursive tree locally, which keeps typing instant. */
@Composable
private fun FileFinder(
    index: LoadState<GitTree>?,
    query: String,
    onQueryChange: (String) -> Unit,
    focus: FocusRequester,
    onRetry: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.m3_find_file_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .focusRequester(focus),
        )
        when (index) {
            null, LoadState.Loading -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }

            is LoadState.Failure -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = index.message.render(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.m3_retry)) }
            }

            is LoadState.Ready -> {
                val tree = index.value
                val all = tree.files
                val matches = remember(all, query) {
                    val needle = query.trim()
                    if (needle.isEmpty()) emptyList()
                    else all.filter { it.contains(needle, ignoreCase = true) }
                }
                Text(
                    text = if (query.isBlank()) {
                        stringResource(R.string.m3_find_file_index, all.size)
                    } else {
                        stringResource(R.string.m3_find_file_matches, matches.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
                if (tree.truncated) {
                    Text(
                        text = stringResource(R.string.m3_find_file_truncated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                when {
                    query.isBlank() -> Unit
                    matches.isEmpty() -> EmptyView(stringResource(R.string.m3_find_file_empty))
                    else -> Column {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(matches.take(MAX_FINDER_RESULTS), key = { it }) { path ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenFile(path) }
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = path,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        if (matches.size > MAX_FINDER_RESULTS) {
                            Text(
                                text = stringResource(R.string.m3_find_file_refine),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: CodeEntry, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(if (tokens.compact) 8.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(tokens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconFor(entry),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.isDir) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!entry.isDir) MetaText(humanSize(entry.size))
                    if (entry.isSymlink) Pill(stringResource(R.string.m3_symlink))
                    if (!entry.sha.isNullOrBlank() && !entry.isDir) MetaText(entry.sha.take(7))
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun iconFor(entry: CodeEntry): ImageVector = when {
    entry.isDir -> Icons.Outlined.Folder
    entry.isImage -> Icons.Outlined.Image
    isTextLike(entry.name) -> Icons.Outlined.Description
    else -> Icons.Outlined.InsertDriveFile
}

fun isTextLike(name: String?): Boolean {
    val lower = name?.lowercase() ?: return false
    if (!lower.contains('.')) return true
    return TEXT_EXTENSIONS.any { lower.endsWith(it) }
}

private val TEXT_EXTENSIONS = listOf(
    ".kt", ".kts", ".java", ".xml", ".gradle", ".md", ".txt", ".json", ".yml", ".yaml", ".toml",
    ".properties", ".pro", ".js", ".ts", ".tsx", ".jsx", ".py", ".rb", ".go", ".rs", ".c", ".h",
    ".cpp", ".hpp", ".cs", ".php", ".swift", ".sh", ".bash", ".zsh", ".fish", ".sql", ".html",
    ".css", ".scss", ".vue", ".dart", ".lua", ".pl", ".r", ".m", ".mm", ".ini", ".cfg", ".conf",
    ".env", ".gitignore", ".editorconfig", ".mk", ".makefile", ".dockerfile", ".rst", ".adoc",
)

fun humanSize(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
}
