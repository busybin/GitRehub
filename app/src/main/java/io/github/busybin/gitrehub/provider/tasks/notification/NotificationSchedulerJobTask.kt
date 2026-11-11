package io.github.busybin.gitrehub.provider.tasks.notification

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.NotificationManager
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.notify.NotifiedThreads
import io.github.busybin.gitrehub.core.notify.NotifyChannel
import io.github.busybin.gitrehub.core.notify.NotificationPoller
import io.github.busybin.gitrehub.core.notify.NotificationPoster
import io.github.busybin.gitrehub.helper.InputHelper
import io.github.busybin.gitrehub.helper.ParseDateFormat
import io.github.busybin.gitrehub.helper.PrefGetter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Power-saving notification poller (see [NotificationForegroundService] for the real-time one).
 *
 * Each run schedules the next one (a one-shot `setMinimumLatency` task, see [scheduleNext]).
 * `setPeriodic` cannot work here: the system floors periodic jobs at 15 minutes, so the
 * 1/5/10-minute settings would never take effect. Deep Doze still defers the job — that is the
 * platform's power policy.
 *
 * A run covers **every stored account** ([NotificationPoller]), not just the one in use, so
 * switching accounts no longer stops the other account's notifications.
 */
class NotificationSchedulerJobTask : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards [jobFinished]: some OEMs throw when it runs twice for the same job. */
    private val finished = AtomicBoolean(false)

    override fun onStartJob(job: JobParameters): Boolean {
        finished.set(false)
        if (SINGLE_JOB_ID != job.jobId && PrefGetter.notificationIntervalMinutes == -1) {
            cancelAll(this)
            finishJob(job)
            return true
        }
        if (!Session.isLoggedIn) {
            // Not logged in (or account removed): nothing to pull, so don't let the job fail here.
            finishJob(job)
            return true
        }
        scope.launch {
            try {
                // null = nothing to poll or every account failed; in both cases keep the job chain
                // and let the system back off when it was a failure.
                val round = NotificationPoller.fetch(since = ParseDateFormat.lastWeekDate)
                if (round == null) {
                    retryLater(job)
                    return@launch
                }
                onSave(round, job)
            } catch (_: Throwable) {
                // A failed post must still release the job, or the platform eventually kills it.
                retryLater(job)
            }
        }
        return true
    }

    /** Job killed by the system (constraints etc.): return true so it is retried later. */
    override fun onStopJob(jobParameters: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun onSave(round: NotificationPoller.Round, job: JobParameters) {
        val inboxes = round.inboxes
        val unread = inboxes.flatMap { it.threads }.filter { it.unread }
        // Read on the web or through a notification action: the shade row is gone before this run.
        pruneStale(unread.mapTo(HashSet()) { it.numericId }, round.failed)
        if (unread.isEmpty()) {
            finishJob(job)
            return
        }
        onNotifyUser(inboxes, job)
    }

    /**
     * Removes only the rows the poller itself posted and that are no longer unread.
     *
     * The old code called `NotificationManager.cancelAll()` on every run, which also wiped download
     * notifications, and then skipped every thread it had ever seen — so the shade emptied out every
     * few minutes and only new threads came back.
     */
    private fun pruneStale(unreadIds: Set<Long>, failedLogins: Set<String>) {
        // A failed request leaves that account's inbox unknown; cancelling its rows would turn a
        // network error into "notifications disappeared". Rows it no longer has go on a later round.
        if (failedLogins.isNotEmpty()) return
        val posted = NotifiedThreads.ids()
        if (posted.isEmpty()) return
        val stale = NotificationPoller.stale(posted, unreadIds)
        val manager = getSystemService(NotificationManager::class.java) ?: return
        stale.forEach { manager.cancel(NotificationActions.managerId(it)) }
        if (stale.isNotEmpty()) NotifiedThreads.forget(stale)
        // Summaries only exist while their group has rows; an empty inbox clears all of them.
        if (unreadIds.isEmpty()) {
            NotifyChannel.entries.forEach { manager.cancel(NotifyChannel.summaryId(it)) }
        }
    }

    private suspend fun onNotifyUser(inboxes: List<NotificationPoller.Inbox>, job: JobParameters) {
        val unreadTotal = NotificationPoller.unreadCount(inboxes)
        if (unreadTotal == 0) {
            finishJob(job)
            return
        }
        // Quiet hours: leave the backlog untouched so it pops after the window instead of vanishing.
        if (NotificationPrefs.isQuietNow()) {
            finishJob(job)
            return
        }
        val accentColor = ContextCompat.getColor(this, R.color.material_blue_700)
        // Skip anything already shown in an earlier run (the two pollers hand over to each other).
        val targets = NotificationPoller.unread(
            inboxes,
            limit = 10,
            skip = NotifiedThreads.ids(),
        )
        if (targets.isEmpty()) {
            finishJob(job)
            return
        }
        val pending = targets.map { (login, thread) ->
            val comment = thread.subject?.latestCommentUrl?.let { url ->
                runCatching {
                    GitHubApi.commentAt(url, token = TokenStore.tokenFor(login))
                }.getOrNull()
            }
            NotificationPoster.Pending(thread, comment, login)
        }
        NotificationPoster.post(
            context = applicationContext,
            items = pending,
            unreadTotal = unreadTotal,
            contentIntent = { entry ->
                getPendingIntent(
                    entry.thread.numericId,
                    entry.thread.subject?.url ?: entry.thread.url.orEmpty(),
                    entry.login,
                )
            },
            accentColor = accentColor,
            largeIcon = true,
            showAccount = inboxes.size > 1,
        )
        runCatching {
            NotifiedThreads.remember(inboxes.flatMap { inbox -> inbox.threads.map { it.numericId } })
        }
        finishJob(job)
    }

    /** Finished a run: done, and immediately schedule the next one. */
    private fun finishJob(job: JobParameters) {
        if (!finished.compareAndSet(false, true)) return
        jobFinished(job, false)
        scheduleNext(this)
    }

    /** Fetch failed: let the system back off, and schedule the next run too so the chain survives. */
    private fun retryLater(job: JobParameters) {
        if (!finished.compareAndSet(false, true)) return
        jobFinished(job, true)
        scheduleNext(this)
    }

    private fun getPendingIntent(id: Long, url: String, login: String?): PendingIntent {
        val intent = ReadNotificationService.start(applicationContext, id, url, login = login)
        return PendingIntent.getService(
            applicationContext, InputHelper.getSafeIntId(id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val JOB_ID = 1
        private const val SINGLE_JOB_ID = 2

        /** Called after the settings toggle/interval changes: cancel all, or (re)schedule once. */
        @JvmStatic
        fun scheduleJob(context: Context) {
            val minutes = PrefGetter.notificationIntervalMinutes
            // Never run alongside the 15-second foreground service: two pollers would notify twice and
            // the job's cancelAll() would also clear the service's ongoing notification.
            if (minutes == -1 || PrefGetter.isNotificationForegroundEnabled) {
                cancelAll(context)
            } else {
                scheduleNext(context, minutes)
            }
        }

        /** Schedule the next run; [minutes] defaults to the current preference. */
        @JvmStatic
        fun scheduleNext(context: Context, minutes: Int = PrefGetter.notificationIntervalMinutes) {
            if (minutes == -1) return
            val interval = TimeUnit.MINUTES.toMillis(minutes.coerceAtLeast(1).toLong())
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val builder = JobInfo.Builder(
                JOB_ID,
                ComponentName(
                    context.packageName,
                    NotificationSchedulerJobTask::class.java.name,
                ),
            )
                .setBackoffCriteria(
                    JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS,
                    JobInfo.BACKOFF_POLICY_LINEAR,
                )
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                // One-shot: eligible at the deadline (the exact time is up to the system's power policy).
                .setMinimumLatency(interval)
            jobScheduler.schedule(builder.build())
        }

        @JvmStatic
        fun cancelAll(context: Context) {
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            jobScheduler.cancel(SINGLE_JOB_ID)
        }
    }
}
