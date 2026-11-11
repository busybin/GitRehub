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
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.notify.NotifyChannel
import io.github.busybin.gitrehub.core.notify.NotifiedThreads
import io.github.busybin.gitrehub.core.notify.NotificationPoller
import io.github.busybin.gitrehub.core.notify.NotificationPoster
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
 *
 * Every stored account is polled ([NotificationPoller]); the first round of each account only
 * records a baseline, so adding an account does not fire its whole backlog.
 */
class NotificationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    /** Unread ids seen last round per account, so only newly appeared ones notify. */
    private val previousUnread = mutableMapOf<String, Set<Long>>()

    /** Accounts whose backlog has already been recorded without notifying. */
    private val baselined = mutableSetOf<String>()

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
        val round = NotificationPoller.fetch(since = ParseDateFormat.lastWeekDate) ?: return
        val inboxes = round.inboxes
        val total = NotificationPoller.unreadCount(inboxes)
        // Quiet hours: keep the baselines untouched, so everything that arrived overnight still
        // pops once the window ends (a single summary, not one alarm per thread).
        if (NotificationPrefs.isQuietNow()) {
            updatePersistent(total)
            return
        }
        // Skip what the interval job already showed: after an Android 15 timeout the two pollers
        // hand over to each other, and nothing should pop up twice.
        val shown = NotifiedThreads.ids()
        val fresh = ArrayList<Pair<String, Notification>>()
        for (inbox in inboxes) {
            val ids = inbox.threads.filter { it.unread }.map { it.numericId }.toSet()
            if (inbox.login !in baselined) {
                // First round of this account records the baseline only; never fire for its backlog.
                baselined += inbox.login
                NotifiedThreads.remember(ids)
            } else {
                val before = previousUnread[inbox.login].orEmpty()
                inbox.threads
                    .filter { it.unread && it.numericId !in before && it.numericId !in shown }
                    .forEach { thread -> fresh += inbox.login to thread }
            }
            previousUnread[inbox.login] = ids
        }
        val live = inboxes.map { it.login }.toSet()
        previousUnread.keys.retainAll(live)
        baselined.retainAll(live)
        if (fresh.isNotEmpty()) {
            val distinct = fresh.distinctBy { it.second.numericId }.take(5)
            notifyNew(distinct, total)
            NotifiedThreads.remember(fresh.map { it.second.numericId })
        }
        updatePersistent(total)
    }

    private fun notifyNew(items: List<Pair<String, Notification>>, unreadTotal: Int) {
        NotificationPoster.post(
            context = this,
            items = items.map { (login, thread) ->
                NotificationPoster.Pending(thread, login = login)
            },
            unreadTotal = unreadTotal,
            contentIntent = { entry ->
                PendingIntent.getService(
                    this,
                    InputHelper.getSafeIntId(entry.thread.numericId),
                    ReadNotificationService.start(
                        this,
                        entry.thread.numericId,
                        entry.thread.subject?.url ?: entry.thread.url.orEmpty(),
                        login = entry.login,
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            },
            showAccount = items.map { it.first }.distinct().size > 1,
        )
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
        NotifyChannel.ensure(this)
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
        /** Poll interval: 15s ≈ 240 requests/hour per account, well under GitHub's 5000/hour limit. */
        const val INTERVAL_SECONDS = 15L
        private const val CHANNEL_SERVICE = "gitrehub_watcher"
        private const val PERSISTENT_ID = 0x6101

        fun start(context: Context) {
            val intent = Intent(context, NotificationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, NotificationForegroundService::class.java)) }
        }
    }
}
