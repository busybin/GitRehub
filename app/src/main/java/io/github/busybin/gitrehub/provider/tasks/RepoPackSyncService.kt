package io.github.busybin.gitrehub.provider.tasks

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
import io.github.busybin.gitrehub.core.offline.RepoPackOptions
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a repository-pack download alive while the user is elsewhere, with its progress in the
 * notification shade. Only one sync runs at a time (enforced by [RepoPackSyncer]).
 */
class RepoPackSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val owner = intent?.getStringExtra(EXTRA_OWNER).orEmpty()
        val repo = intent?.getStringExtra(EXTRA_REPO).orEmpty()
        val options = RepoPackOptions(
            includeSmall = intent?.getBooleanExtra(EXTRA_SMALL, true) ?: true,
            includeLarge = intent?.getBooleanExtra(EXTRA_LARGE, false) ?: false,
            includeIssues = intent?.getBooleanExtra(EXTRA_ISSUES, false) ?: false,
            autoSync = intent?.getBooleanExtra(EXTRA_AUTO, true) ?: true,
        )
        if (owner.isBlank() || repo.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fullName = "$owner/$repo"
        startForegroundCompat(build(null, fullName))
        job = scope.launch {
            val watcher = launch {
                RepoPackSyncer.progress.collect { progress ->
                    if (progress != null && progress.fullName == fullName) {
                        notify(build(progress, fullName))
                    }
                }
            }
            runCatching { RepoPackSyncer.syncNow(this@RepoPackSyncService, owner, repo, options) }
            watcher.cancel()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun build(progress: RepoPackSyncer.Progress?, fullName: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.m3_pack_sync_notification_title, fullName))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
        if (progress != null) {
            if (progress.total > 0) {
                val bytes = io.github.busybin.gitrehub.compose.common.formatBytes(progress.bytes)
                builder.setContentText(
                    getString(R.string.m3_pack_sync_notification_text, progress.done, progress.total, bytes),
                )
                builder.setProgress(progress.total, progress.done, false)
            } else {
                builder.setContentText(getString(R.string.m3_pack_syncing_meta))
            }
        } else {
            builder.setContentText(getString(R.string.m3_pack_syncing_meta))
        }
        return builder.build()
    }

    private fun notify(notification: android.app.Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
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
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_REPO = "repo"
        private const val EXTRA_SMALL = "small"
        private const val EXTRA_LARGE = "large"
        private const val EXTRA_ISSUES = "issues"
        private const val EXTRA_AUTO = "auto"

        fun start(context: Context, owner: String, repo: String, options: RepoPackOptions) {
            val intent = Intent(context, RepoPackSyncService::class.java).apply {
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
                putExtra(EXTRA_SMALL, options.includeSmall)
                putExtra(EXTRA_LARGE, options.includeLarge)
                putExtra(EXTRA_ISSUES, options.includeIssues)
                putExtra(EXTRA_AUTO, options.autoSync)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
