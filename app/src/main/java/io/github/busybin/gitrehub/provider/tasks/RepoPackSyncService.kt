package io.github.busybin.gitrehub.provider.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.compose.Routes
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import io.github.busybin.gitrehub.provider.scheme.SchemeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps the pack queue alive while the user is elsewhere and mirrors it into the notification
 * shade. The queue and the worker live in [RepoPackSyncer]; this service only provides the
 * foreground lifetime, the notification and the cancel action.
 */
class RepoPackSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // Must happen before anything can stop the service: Android requires startForeground()
        // right after a foreground-service start.
        startForegroundCompat(build(RepoPackSyncer.state.value))
        scope.launch {
            RepoPackSyncer.state.collect { state ->
                if (!state.hasWork) {
                    stopSelf()
                    return@collect
                }
                notify(build(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            RepoPackSyncer.cancelAll()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // A service torn down by the system while a task is still running must not keep pretending
        // to download; cancel quietly (no "cancelled" toast).
        if (RepoPackSyncer.state.value.hasWork) RepoPackSyncer.cancelAll(silent = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun build(state: RepoPackSyncer.State): Notification {
        val task = state.running ?: state.queued.firstOrNull()
        val fullName = task?.fullName.orEmpty()
        val progress = state.progress
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (fullName.isBlank()) getString(R.string.m3_pack_title)
                else getString(R.string.m3_pack_sync_notification_title, fullName),
            )
            .setContentIntent(openIntent(task))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
        if (state.queued.isNotEmpty()) {
            builder.setSubText(getString(R.string.m3_pack_queue_waiting, state.queued.size))
        }
        if (progress != null) {
            if (progress.total > 0) {
                builder.setContentText(
                    getString(
                        R.string.m3_pack_sync_notification_text,
                        progress.done,
                        progress.total,
                        formatBytes(progress.bytes),
                    ),
                )
                builder.setProgress(progress.total, progress.done, false)
            } else {
                builder.setContentText(getString(R.string.m3_pack_syncing_meta))
            }
        } else {
            builder.setContentText(getString(R.string.m3_pack_syncing_meta))
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.m3_pack_cancel_save),
            PendingIntent.getService(
                this,
                REQUEST_CANCEL,
                Intent(this, RepoPackSyncService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return builder.build()
    }

    /** Opens the repository being saved instead of dropping the user on the feed. */
    private fun openIntent(task: io.github.busybin.gitrehub.core.offline.RepoPackTask?): PendingIntent {
        val intent = Intent(this, MainComposeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (task != null) intent.putExtra(SchemeParser.EXTRA_NAVIGATE, Routes.repo(task.owner, task.repo))
        return PendingIntent.getActivity(
            this,
            task?.fullName?.hashCode() ?: REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.m3_pack_title),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL = "gitrehub_sync"
        private const val NOTIFICATION_ID = 0x6301
        private const val REQUEST_OPEN = 0x6302
        private const val REQUEST_CANCEL = 0x6303
        private const val ACTION_CANCEL = "io.github.busybin.gitrehub.CANCEL_PACK_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, RepoPackSyncService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
