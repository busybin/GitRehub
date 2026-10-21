package io.github.busybin.gitrehub.provider.tasks.notification

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
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.notify.NotifiedThreads
import io.github.busybin.gitrehub.helper.InputHelper
import io.github.busybin.gitrehub.helper.ParseDateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Always-on foreground service for near-real-time notifications: GitHub offers no push channel to
 * third-party clients, so "seconds" means polling `GET /notifications` every [INTERVAL_SECONDS]. A
 * foreground service is not deferred like JobScheduler during Doze, at the cost of a permanent
 * "listening" notification. [NotificationSchedulerJobTask] remains for power-saving mode.
 */
class NotificationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    /** Unread ids seen last round, so only newly appeared ones notify. */
    private var previousUnread: Set<Long> = emptySet()
    private var firstRound = true

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(persistentNotification(0))
        loop = scope.launch {
            while (isActive) {
                runCatching { poll() }
                delay(INTERVAL_SECONDS * 1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /**
     * Android 15+ caps `dataSync` foreground services at six hours per 24, then calls this. Stop
     * ourselves right away (missing the short grace period means a system ANR) and re-arm the
     * interval job so notifications still arrive until the next app launch restarts this service.
     */
    override fun onTimeout(startId: Int) {
        NotificationSchedulerJobTask.scheduleNext(this)
        stopSelf(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun poll() {
        if (!Session.isLoggedIn) return
        val items = runCatching {
            GitHubApi.notifications(since = ParseDateFormat.lastWeekDate).value
        }.getOrNull() ?: return
        val unread = items.filter { it.unread }
        val unreadIds = unread.map { it.numericId }.toSet()
        if (firstRound) {
            // First round records the baseline only; never fire for the historical backlog.
            firstRound = false
            NotifiedThreads.remember(unreadIds)
        } else {
            // Skip what the interval job already showed: after an Android 15 timeout the two pollers
            // hand over to each other, and nothing should pop up twice.
            val shown = NotifiedThreads.ids()
            val fresh = unread.filter { it.numericId !in previousUnread && it.numericId !in shown }
            if (fresh.isNotEmpty()) {
                notifyNew(fresh)
                NotifiedThreads.remember(fresh.map { it.numericId })
            }
        }
        previousUnread = unreadIds
        updatePersistent(unread.size)
    }

    private fun notifyNew(items: List<Notification>) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, CHANNEL_NEW, getString(R.string.m3_section_notifications),
            NotificationManager.IMPORTANCE_DEFAULT)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        items.take(5).forEach { thread ->
            val title = thread.subject?.title ?: thread.repoFullName ?: getString(R.string.m3_notification)
            val text = thread.repoFullName.orEmpty()
            val notification = NotificationCompat.Builder(this, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setGroup(CHANNEL_NEW)
                .build()
            manager.notify(InputHelper.getSafeIntId(thread.numericId), notification)
        }
        if (items.size > 1) {
            val summary = NotificationCompat.Builder(this, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.m3_section_notifications))
                .setContentText(getString(R.string.m3_notif_new_count, items.size))
                .setGroup(CHANNEL_NEW)
                .setGroupSummary(true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(SUMMARY_ID, summary)
        }
    }

    private fun updatePersistent(unread: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PERSISTENT_ID, persistentNotification(unread))
    }

    private fun persistentNotification(unread: Int) =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.m3_notif_fg_title))
            .setContentText(
                if (unread > 0) getString(R.string.m3_notif_new_count, unread)
                else getString(R.string.m3_notif_fg_idle)
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1,
                    Intent(this, MainComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun startForegroundCompat(notification: android.app.Notification) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, CHANNEL_SERVICE, getString(R.string.m3_notif_fg_title),
            NotificationManager.IMPORTANCE_MIN)
        ensureChannel(manager, CHANNEL_NEW, getString(R.string.m3_section_notifications),
            NotificationManager.IMPORTANCE_DEFAULT)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, PERSISTENT_ID, notification, type)
    }

    private fun ensureChannel(manager: NotificationManager, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    companion object {
        /** Poll interval: 15s ≈ 240 requests/hour, well under GitHub's 5000/hour authenticated limit. */
        const val INTERVAL_SECONDS = 15L
        private const val CHANNEL_SERVICE = "gitrehub_watcher"
        private const val CHANNEL_NEW = "gitrehub_new"
        private const val PERSISTENT_ID = 0x6101
        private const val SUMMARY_ID = 0x6102

        fun start(context: Context) {
            val intent = Intent(context, NotificationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, NotificationForegroundService::class.java)) }
        }
    }
}
