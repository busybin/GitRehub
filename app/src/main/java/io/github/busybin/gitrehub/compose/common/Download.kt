package io.github.busybin.gitrehub.compose.common

import android.Manifest
import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R
import java.io.File
import java.net.URLConnection

/**
 * Single entry point for downloads. All of them go through the system [DownloadManager]
 * (background, progress/completion notifications, redirects, survives the app being killed), which
 * made the old SAF "pick a folder every time" machinery unnecessary.
 *
 * DownloadManager never overwrites: on a name clash it stores `foo-1.zip`. So a confirm dialog runs
 * first with overwrite / keep both / cancel; "overwrite" deletes the old file before enqueueing.
 */
object Downloader {

    /** Target file in the Downloads dir; on Android 10+ only used to check existence and best-effort delete. */
    private fun targetFile(context: Context, name: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)

    /** Strip path separators from a file name (path traversal) and fall back to a default. */
    fun safeName(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download" }

    /** Android 9 and below need legacy storage permission to write public Downloads; 10+ is written by the system. */
    fun needsStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED

    /** Whether a file with this name already exists in Downloads, used to decide whether to ask about overwriting. @return the existing file name, or null. */
    fun conflict(context: Context, fileName: String): String? {
        val name = safeName(fileName)
        val exists = completedDownloadIds(context, name).isNotEmpty() ||
            mediaStoreIds(context, name).isNotEmpty() ||
            runCatching { targetFile(context, name).exists() }.getOrDefault(false)
        return if (exists) name else null
    }

    /** Delete a same-named old file so the new download really lands under the same name. @return true if something was deleted (overwrite possible). */
    fun deleteExisting(context: Context, fileName: String): Boolean {
        val name = safeName(fileName)
        var deleted = false
        // 1) System download records: remove() deletes the file too (the common case for our downloads).
        completedDownloadIds(context, name).forEach { id ->
            val removed = runCatching {
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
            }.getOrDefault(0)
            if (removed > 0) deleted = true
        }
        // 2) MediaStore download entries (deleting someone else's throws and only affects that entry).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreIds(context, name).forEach { id ->
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                val ok = runCatching { context.contentResolver.delete(uri, null, null) > 0 }
                    .getOrDefault(false)
                if (ok) deleted = true
            }
        }
        // 3) Delete the file directly (Android 9 and below).
        val file = targetFile(context, name)
        val rawDeleted = runCatching { file.exists() && file.delete() }.getOrDefault(false)
        if (rawDeleted) deleted = true
        return deleted
    }

    /** Ids of finished system downloads whose title is exactly this file name. */
    private fun completedDownloadIds(context: Context, name: String): List<Long> = runCatching {
        val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(query)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val ids = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                if (cursor.getString(titleColumn) == name) ids += cursor.getLong(idColumn)
            }
            ids
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Same-named MediaStore download entries (Android 10+ only sees the ones we may access). */
    private fun mediaStoreIds(context: Context, name: String): List<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { cursor ->
                val column = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val ids = mutableListOf<Long>()
                while (cursor.moveToNext()) ids += cursor.getLong(column)
                ids
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Enqueue a download into the system Downloads dir. @return the task id, or null when permission is missing or enqueueing failed. */
    fun enqueue(context: Context, url: String, fileName: String): Long? {
        if (url.isBlank()) return null
        val name = safeName(fileName)
        val mime = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription(context.getString(R.string.m3_downloading))
            .setMimeType(mime)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            // Show only the in-progress entry; the completion notification comes from DownloadCompleteReceiver.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .addRequestHeader("User-Agent", "GitRehub")
        if (!needsStoragePermission(context)) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        }
        return runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }.onFailure { Log.e("GitRehubDL", "enqueue failed: $url", it) }.getOrNull()
    }

    fun perform(context: Context, url: String, fileName: String) {
        val name = safeName(fileName)
        val id = enqueue(context, url, name)
        val text = if (id != null) {
            context.getString(R.string.m3_download_started, name)
        } else {
            context.getString(R.string.m3_download_failed)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

/** A download waiting for the user's confirmation; a non-null [conflict] means the name already exists. */
private data class PendingDownload(val url: String, val fileName: String, val conflict: String?)

/**
 * Compose helper for a "start download" action: confirm first (overwrite / keep both / cancel on a
 * name clash), then handle the Android 9- storage permission.
 * Usage: `val download = rememberDownloader(); ... download(url, fileName)`.
 */
@Composable
fun rememberDownloader(): (String, String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingDownload?>(null) }
    // The download to resume once the permission dialog returns.
    var awaitingPermission by remember { mutableStateOf<PendingDownload?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val p = awaitingPermission
        awaitingPermission = null
        when {
            granted && p != null -> Downloader.perform(context, p.url, p.fileName)
            !granted -> Toast.makeText(context, R.string.m3_download_need_permission, Toast.LENGTH_LONG).show()
        }
    }

    fun start(request: PendingDownload) {
        if (Downloader.needsStoragePermission(context)) {
            awaitingPermission = request
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            Downloader.perform(context, request.url, request.fileName)
        }
    }

    pending?.let { request ->
        DownloadConfirmDialog(
            fileName = request.fileName,
            conflict = request.conflict != null,
            onDownload = {
                pending = null
                start(request)
            },
            onOverwrite = {
                pending = null
                // If the old file cannot be deleted, say so: DownloadManager would save `foo-1.zip` instead.
                if (!Downloader.deleteExisting(context, request.fileName)) {
                    Toast.makeText(context, R.string.m3_download_overwrite_failed, Toast.LENGTH_LONG).show()
                }
                start(request)
            },
            onDismiss = { pending = null },
        )
    }

    return { url, name ->
        if (url.isNotBlank()) {
            val fileName = Downloader.safeName(name)
            pending = PendingDownload(url, fileName, Downloader.conflict(context, fileName))
        }
    }
}

/**
 * Pre-download confirmation. On a name clash "download" alone is not enough: DownloadManager would
 * silently create `foo-1.zip` with no explanation, so overwrite / keep both / cancel are offered.
 */
@Composable
private fun DownloadConfirmDialog(
    fileName: String,
    conflict: Boolean,
    onDownload: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_download_confirm_title)) },
        text = {
            Text(
                stringResource(
                    if (conflict) R.string.m3_download_confirm_exists
                    else R.string.m3_download_confirm_message,
                    fileName,
                )
            )
        },
        confirmButton = {
            if (conflict) {
                TextButton(onClick = onOverwrite) {
                    Text(stringResource(R.string.m3_download_overwrite))
                }
            } else {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.m3_download_confirm))
                }
            }
        },
        dismissButton = {
            Row {
                if (conflict) {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.m3_download_keep_both))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.m3_cancel))
                }
            }
        },
    )
}
