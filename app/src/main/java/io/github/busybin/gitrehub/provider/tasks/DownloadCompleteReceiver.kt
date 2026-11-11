package io.github.busybin.gitrehub.provider.tasks

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.notify.DownloadNotifications
import java.net.URLConnection

/**
 * Completion notification for files handed to [DownloadManager]: its own progress entry is
 * `VISIBILITY_VISIBLE`, so it disappears the moment the download finishes. This adds the missing
 * "saved to Downloads/<name>" entry and gives it a tap target that opens the file itself.
 *
 * The manifest receiver must be exported, because the broadcast is sent by DownloadProvider
 * (`android.media` uid), not by the system.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0L) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return@use
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    ?.takeIf { it.isNotBlank() } ?: "download"
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                notifyDone(context, manager, id, title, mime)
            }
        }
    }

    private fun notifyDone(context: Context, manager: DownloadManager, id: Long, title: String, mime: String?) {
        // Show the public Downloads path, not DownloadManager's file:// URI.
        val location = "${Environment.DIRECTORY_DOWNLOADS}/$title"
        DownloadNotifications.post(
            context = context,
            id = DownloadNotifications.id(id),
            title = context.getString(R.string.m3_download_done_title),
            text = context.getString(R.string.m3_download_done_message, location),
            tap = tapIntent(context, manager, id, title, mime),
        )
    }

    /**
     * Open the finished file when something can handle it, fall back to the Downloads screen, and as a
     * last resort to the app itself, so the tap never silently does nothing.
     */
    private fun tapIntent(context: Context, manager: DownloadManager, id: Long, title: String, mime: String?): Intent {
        val uri = runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()
        if (uri != null) {
            val type = mime?.takeIf { it.isNotBlank() }
                ?: URLConnection.guessContentTypeFromName(title)
                ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (view.resolveActivity(context.packageManager) != null) return view
        }
        val downloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (downloads.resolveActivity(context.packageManager) != null) return downloads
        return context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: downloads
    }
}
