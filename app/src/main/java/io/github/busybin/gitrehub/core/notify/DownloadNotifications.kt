package io.github.busybin.gitrehub.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R

/**
 * Completion notifications for downloads that finish outside of what the user is looking at: files
 * handed to the system `DownloadManager` and the self-update APK. Both need a tap target of their own,
 * because a download that completes in the background cannot start an activity by itself.
 *
 * [canNotify] also drives `DownloadManager`'s own notification visibility (see `Downloader`): when this
 * app is not allowed to post, the provider keeps its completion notification instead of the custom
 * one, so a download never ends silently.
 */
object DownloadNotifications {

    private const val CHANNEL = "gitrehub_downloads"

    /** Notification ids live above the other channels so they cannot replace a thread notification. */
    private const val BASE = 0x3A000000

    /** Id for the self-update APK notification (download ids are small, so they cannot collide). */
    const val UPDATE_ID = BASE + 0x00FFFFFF

    fun id(downloadId: Long): Int = BASE + (downloadId and 0xFFFFFF).toInt()

    /** Whether *this* app may post on the download channel right now. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun post(context: Context, id: Int, title: String, text: String, tap: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.m3_download_done_title), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(id, notification)
    }
}
