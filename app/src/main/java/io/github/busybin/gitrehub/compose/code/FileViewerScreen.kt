package io.github.busybin.gitrehub.compose.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.compose.common.shareText
import io.github.busybin.gitrehub.compose.theme.CodePalette
import io.github.busybin.gitrehub.compose.theme.LocalCodePalette
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.provider.syntax.Syntax
import io.github.busybin.gitrehub.provider.syntax.TokenKind
import kotlinx.coroutines.launch

/**
 * Reads one file of a repository inside the app.
 *
 * Syntax highlighting is done here, with **the app's current theme** ([LocalCodePalette]).
 *
 * Markdown files render by default, switchable to the raw text; the header shows the last modification
 * time. Line numbers and in-file search are supported, with the body rendered line by line in a
 * LazyColumn so a match can be scrolled to directly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileViewerScreen(
    owner: String,
    repo: String,
    path: String,
    ref: String? = null,
    onOpenHistory: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
) {
    val vm: FileViewerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val wrapLines by CodePrefs.wrap.collectAsStateWithLifecycle()
    var rawMode by remember(path) { mutableStateOf(false) }
    var searchOpen by remember(path) { mutableStateOf(false) }
    var query by remember(path) { mutableStateOf("") }
    var matchCursor by remember(path) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(owner, repo, path, ref) { vm.load(owner, repo, path, ref) }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            vm.load(owner, repo, path, ref, force = true)
            onRefreshConsumed()
        }
    }

    AsyncContent(state = state, onRetry = { vm.load(owner, repo, path, ref, force = true) }) { file ->
        val isMarkdown = isMarkdown(file.path)
        val showCode = !file.isImage && !file.tooBig && !file.text.isNullOrEmpty() &&
            !(isMarkdown && !rawMode)

        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                MetaText(
                    buildString {
                        append(file.path)
                        append(" · ")
                        append(humanSize(file.size))
                        if (!file.ref.isNullOrBlank()) append(" · ${file.ref}")
                        file.lastModified?.let {
                            append(" · ${stringResource(R.string.m3_last_modified, relativeTime(it))}")
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val url = file.htmlUrl
                                ?: "https://github.com/$owner/$repo/blob/${file.ref ?: "HEAD"}/${file.path}"
                            BrowserLauncher.open(context, url)
                        }
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.m3_browser))
                    }
                    val text = file.text
                    if (!text.isNullOrEmpty()) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_copy))
                        }
                    }
                    if (onEdit != null && !file.isImage && !file.tooBig && !file.text.isNullOrEmpty()) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_edit_file))
                        }
                    }
                    if (onOpenHistory != null) {
                        TextButton(onClick = onOpenHistory) {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_file_history))
                        }
                    }
                    TextButton(
                        onClick = {
                            val url = file.htmlUrl
                                ?: "https://github.com/$owner/$repo/blob/${file.ref ?: "HEAD"}/${file.path}"
                            shareText(context, url, subject = "$owner/$repo/${file.path}")
                        }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.m3_share))
                    }
                    if (showCode) {
                        TextButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) query = ""
                            }
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_search))
                        }
                        FilterChip(
                            selected = wrapLines,
                            onClick = { CodePrefs.toggle() },
                            label = { Text(stringResource(R.string.m3_wrap_lines)) },
                        )
                    }
                    if (isMarkdown && !file.text.isNullOrEmpty()) {
                        // Toggle between rendered and raw (rendered by default).
                        FilterChip(
                            selected = !rawMode,
                            onClick = { rawMode = false },
                            label = { Text(stringResource(R.string.m3_rendered)) },
                        )
                        FilterChip(
                            selected = rawMode,
                            onClick = { rawMode = true },
                            label = { Text(stringResource(R.string.m3_raw)) },
                        )
                    }
                }
            }

            HorizontalDivider()

            when {
                file.isImage && !file.rawUrl.isNullOrBlank() ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SubcomposeAsyncImage(
                            model = file.rawUrl,
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                            loading = {
                                Box(
                                    Modifier.fillMaxWidth().height(160.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            },
                        )
                    }

                file.isImage ->
                    NoticeCard(stringResource(R.string.m3_file_image))

                file.tooBig ->
                    NoticeCard(stringResource(R.string.m3_file_large, humanSize(file.size)))

                file.text.isNullOrEmpty() ->
                    NoticeCard(stringResource(R.string.m3_file_binary))

                // Markdown: the rendered pipeline by default (same as README), switchable to raw.
                isMarkdown && !rawMode -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
                        item {
                            MarkdownText(
                                markdown = file.text,
                                baseUrl = repoRawBaseUrl(owner, repo, file.ref),
                                linkBaseUrl = repoBlobBaseUrl(owner, repo, file.ref),
                                repo = "$owner/$repo",
                            )
                        }
                    }
                }

                else -> {
                    val text = file.text!!
                    val code = LocalCodePalette.current
                    val language = Syntax.languageOf(file.path)
                    val lines = remember(text) { splitLines(text) }
                    val tokensByLine = remember(text, language) {
                        tokenizeByLine(text, lines, language)
                    }
                    val matches = remember(text, query) {
                        if (query.isBlank()) emptyList<Int>()
                        else lines.indices.filter { index ->
                            lines[index].text.contains(query, ignoreCase = true)
                        }
                    }

                    // Query changed: put the cursor back on the first match.
                    LaunchedEffect(query) { matchCursor = 0 }

                    if (searchOpen) {
                        SearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            matchCount = matches.size,
                            current = if (matches.isEmpty()) 0 else matchCursor + 1,
                            onPrev = {
                                if (matches.isNotEmpty()) {
                                    matchCursor = (matchCursor - 1 + matches.size) % matches.size
                                    scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                                }
                            },
                            onNext = {
                                if (matches.isNotEmpty()) {
                                    matchCursor = (matchCursor + 1) % matches.size
                                    scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                                }
                            },
                            onClose = {
                                searchOpen = false
                                query = ""
                            },
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(lines.size, key = { it }) { index ->
                            val line = lines[index]
                            CodeLine(
                                number = line.number,
                                annotated = highlightLine(
                                    line = line,
                                    tokens = tokensByLine[index].orEmpty(),
                                    code = code,
                                    query = query,
                                    highlight = MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                                gutterWidth = lines.size.toString().length,
                                background = code.background,
                                plain = code.plain,
                                matched = matches.contains(index),
                                wrap = wrapLines,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line: line number on the left, highlighted source on the right. When wrapping is off the line
 * stays on one row and scrolls horizontally, which is how a diff / code reviewer reads fixed columns.
 */
@Composable
private fun CodeLine(
    number: Int,
    annotated: AnnotatedString,
    gutterWidth: Int,
    background: Color,
    plain: Color,
    matched: Boolean,
    wrap: Boolean,
) {
    val lineScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (matched) Color(0x22FFC107) else background),
    ) {
        Text(
            text = number.toString().padStart(gutterWidth),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier
                .width((gutterWidth * 9 + 18).dp)
                .padding(end = 8.dp, start = 6.dp, top = 1.dp, bottom = 1.dp),
        )
        SelectionContainer {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = plain,
                softWrap = wrap,
                maxLines = if (wrap) Int.MAX_VALUE else 1,
                modifier = Modifier
                    .weight(1f)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(lineScroll))
                    .padding(end = 12.dp, top = 1.dp, bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    current: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.m3_file_search_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (matchCount == 0) {
                stringResource(R.string.m3_file_search_none)
            } else {
                stringResource(R.string.m3_file_search_matches, current, matchCount)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onPrev, enabled = matchCount > 0) {
            Icon(Icons.Outlined.ExpandLess, contentDescription = stringResource(R.string.m3_prev_match))
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(Icons.Outlined.ExpandMore, contentDescription = stringResource(R.string.m3_next_match))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.m3_close))
        }
    }
}

private data class SourceLine(val number: Int, val text: String, val start: Int, val end: Int)

/** Split on `\n`, drop `\r`, and remember each line's absolute offset in the source (needed for highlighting). */
private fun splitLines(source: String): List<SourceLine> {
    val out = mutableListOf<SourceLine>()
    var index = 0
    var number = 1
    while (true) {
        var newline = source.indexOf('\n', index)
        if (newline < 0) newline = source.length
        var textEnd = newline
        if (textEnd > index && source[textEnd - 1] == '\r') textEnd--
        out += SourceLine(number++, source.substring(index, textEnd), index, newline)
        if (newline >= source.length) break
        index = newline + 1
    }
    return out
}

/** Spread the whole text's lexical tokens across the lines (tokens may span lines, so they are cut per line). */
private fun tokenizeByLine(
    source: String,
    lines: List<SourceLine>,
    language: String?,
): Map<Int, List<Pair<IntRange, TokenKind>>> {
    val result = HashMap<Int, MutableList<Pair<IntRange, TokenKind>>>()
    val tokens = Syntax.tokenize(source, language)
    var lineIndex = 0
    tokens.forEach { token ->
        while (lineIndex < lines.size && token.start >= lines[lineIndex].end) lineIndex++
        var cursor = lineIndex
        while (cursor < lines.size && lines[cursor].start < token.end) {
            val line = lines[cursor]
            val from = maxOf(token.start, line.start)
            val to = minOf(token.end, line.end)
            if (from < to) {
                result.getOrPut(cursor) { mutableListOf() } +=
                    (from - line.start) until (to - line.start) to token.kind
            }
            cursor++
        }
    }
    return result
}

private fun highlightLine(
    line: SourceLine,
    tokens: List<Pair<IntRange, TokenKind>>,
    code: CodePalette,
    query: String,
    highlight: Color,
): AnnotatedString = buildAnnotatedString {
    append(line.text)
    tokens.forEach { (range, kind) ->
        val start = range.first.coerceIn(0, line.text.length)
        val end = (range.last + 1).coerceIn(0, line.text.length)
        if (start < end) addStyle(SpanStyle(color = code.colorOf(kind)), start, end)
    }
    if (query.isNotEmpty()) {
        var from = 0
        while (true) {
            val at = line.text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            addStyle(SpanStyle(background = highlight), at, at + query.length)
            from = at + query.length
        }
    }
}

private fun isMarkdown(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown") ||
        lower.endsWith(".mkd")
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Token kind -> the current theme's code colour. */
private fun CodePalette.colorOf(kind: TokenKind): Color = when (kind) {
    TokenKind.COMMENT -> comment
    TokenKind.STRING -> string
    TokenKind.NUMBER -> number
    TokenKind.KEYWORD -> keyword
    TokenKind.FUNCTION -> function
    TokenKind.TYPE -> type
    // Annotations use the keyword colour; in Kotlin/Java they are the same kind of thing.
    TokenKind.ANNOTATION -> keyword
}
