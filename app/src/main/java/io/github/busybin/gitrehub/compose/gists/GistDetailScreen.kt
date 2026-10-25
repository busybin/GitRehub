package io.github.busybin.gitrehub.compose.gists

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.GistFile
import io.github.busybin.gitrehub.helper.AppHelper
import io.github.busybin.gitrehub.helper.BrowserLauncher

/** How many lines of one file are laid out inline; longer files point at the raw link. */
private const val MAX_INLINE_LINES = 400

/**
 * One gist: description, then one card per file with its text.
 *
 * Files GitHub truncated are only partly in the response; the rest is fetched from the raw URL
 * (a public host, so the token is never sent there).
 */
@Composable
fun GistDetailScreen(
    id: String,
    onDeleted: () -> Unit,
) {
    val vm: GistDetailViewModel = viewModel(key = "gist-$id")
    val state by vm.state.collectAsStateWithLifecycle()
    val full by vm.full.collectAsStateWithLifecycle()
    val loadingFull by vm.loadingFull.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val myLogin by vm.myLogin.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val messageText = message?.render()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }
    LaunchedEffect(deleted) { if (deleted) onDeleted() }
    LaunchedEffect(id) { vm.load(id) }

    AsyncContent(state = state, onRetry = { vm.load(id, force = true) }) { gist ->
        val mine = myLogin != null && gist.owner?.login.equals(myLogin, ignoreCase = true)
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(R.string.m3_gist_delete_confirm_title),
                message = stringResource(R.string.m3_gist_delete_confirm_message),
                confirmLabel = stringResource(R.string.m3_gist_delete),
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    vm.delete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GistHeader(
                    gist = gist,
                    canDelete = mine,
                    onOpenBrowser = {
                        gist.htmlUrl?.let { BrowserLauncher.open(context, it) }
                    },
                    onDelete = { confirmDelete = true },
                )
            }
            gist.filesSorted.forEach { file ->
                item(key = file.filename) {
                    GistFileCard(
                        file = file,
                        full = full[file.filename],
                        loadingFull = file.filename in loadingFull,
                        onLoadFull = { vm.loadFull(file) },
                        onOpenRaw = {
                            file.rawUrl?.let { BrowserLauncher.open(context, it) }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GistHeader(
    gist: Gist,
    canDelete: Boolean,
    onOpenBrowser: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text = gist.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                gist.owner?.let { owner ->
                    Avatar(owner.avatarUrl, size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = owner.login,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                MetaText(relativeTime(gist.updatedAt ?: gist.createdAt))
                Spacer(Modifier.weight(1f))
                if (!gist.public) Pill(stringResource(R.string.m3_gist_secret))
                IconButton(onClick = onOpenBrowser) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.m3_open_in_browser),
                    )
                }
                IconButton(
                    onClick = {
                        val text = gist.filesSorted.joinToString("\n\n") { file ->
                            "${file.filename}\n${file.content.orEmpty()}"
                        }
                        AppHelper.copyToClipboard(context, text)
                    },
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.m3_gist_copy_all),
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.m3_gist_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (gist.comments > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.m3_gist_comments, gist.comments),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GistFileCard(
    file: GistFile,
    full: String?,
    loadingFull: Boolean,
    onLoadFull: () -> Unit,
    onOpenRaw: () -> Unit,
) {
    val context = LocalContext.current
    val text = full ?: file.content
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (file.language != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(file.language)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatBytes(file.size.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (text != null) {
                    TextButton(
                        onClick = { AppHelper.copyToClipboard(context, text) },
                    ) { Text(stringResource(R.string.m3_copy)) }
                }
                TextButton(onClick = onOpenRaw) {
                    Text(stringResource(R.string.m3_gist_raw))
                }
            }
            when {
                text == null -> {
                    Text(
                        text = stringResource(R.string.m3_gist_no_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    GistText(text)
                }
            }
            if (file.truncated && full == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.m3_gist_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLoadFull, enabled = !loadingFull) {
                    Text(
                        stringResource(
                            if (loadingFull) R.string.m3_loading_file else R.string.m3_gist_load_full
                        )
                    )
                }
            }
        }
    }
}

/** Monospace body, capped so a giant gist does not lay out tens of thousands of text lines. */
@Composable
private fun GistText(text: String) {
    val lines = remember(text) { text.lineSequence().toList() }
    val shown = lines.take(MAX_INLINE_LINES)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(8.dp),
    ) {
        Text(
            text = shown.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        if (lines.size > MAX_INLINE_LINES) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.m3_gist_more_lines, lines.size - MAX_INLINE_LINES),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
