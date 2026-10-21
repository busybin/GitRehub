package io.github.busybin.gitrehub.provider.tasks

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import io.github.busybin.gitrehub.R

/**
 * Completion notification that carries the saved location, since [DownloadManager]'s own completion
 * notification only shows a title (its progress notification is set to `VISIBILITY_VISIBLE`).
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0L) return
        runCatching {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return@use
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    ?: "download"
                notifyDone(context, title)
            }
        }
    }

    private fun notifyDone(context: Context, title: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                context.getString(R.string.m3_download_done_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        // Show the public Downloads path, not DownloadManager's file:// URI.
        val location = "${Environment.DIRECTORY_DOWNLOADS}/$title"
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.m3_download_done_title))
            .setContentText(context.getString(R.string.m3_download_done_message, location))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.m3_download_done_message, location))
            )
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL = "gitrehub_downloads"
    }
}
