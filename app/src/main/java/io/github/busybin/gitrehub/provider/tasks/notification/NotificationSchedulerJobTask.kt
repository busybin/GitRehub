package io.github.busybin.gitrehub.provider.tasks.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.notify.NotifiedThreads
import io.github.busybin.gitrehub.core.notify.NotificationPoster
import io.github.busybin.gitrehub.helper.AppHelper
import io.github.busybin.gitrehub.helper.InputHelper
import io.github.busybin.gitrehub.helper.ParseDateFormat
import io.github.busybin.gitrehub.helper.PrefGetter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Power-saving notification poller (see [NotificationForegroundService] for the real-time one).
 *
 * Each run schedules the next one (a one-shot `setMinimumLatency` task, see [scheduleNext]).
 * `setPeriodic` cannot work here: the system floors periodic jobs at 15 minutes, so the
 * 1/5/10-minute settings would never take effect. Deep Doze still defers the job — that is the
 * platform's power policy.
 */
class NotificationSchedulerJobTask : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(job: JobParameters): Boolean {
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
                val items = GitHubApi.notifications(since = ParseDateFormat.lastWeekDate).value
                AppHelper.cancelAllNotifications(applicationContext)
                onSave(items, job)
            } catch (_: Throwable) {
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

    private suspend fun onSave(items: List<Notification>, job: JobParameters) {
        if (items.isNotEmpty()) onNotifyUser(items, job)
    }

    private suspend fun onNotifyUser(items: List<Notification>, job: JobParameters) {
        val unread = items.filter { it.unread }
        if (unread.isEmpty()) {
            AppHelper.cancelAllNotifications(applicationContext)
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
        val shown = NotifiedThreads.ids()
        val targets = unread.filter { it.numericId !in shown }.take(10)
        val pending = targets.map { thread ->
            val comment = thread.subject?.latestCommentUrl?.let { url ->
                runCatching { GitHubApi.commentAt(url) }.getOrNull()
            }
            NotificationPoster.Pending(thread, comment)
        }
        NotificationPoster.post(
            context = applicationContext,
            items = pending,
            unreadTotal = unread.size,
            contentIntent = { thread ->
                getPendingIntent(
                    thread.numericId,
                    thread.subject?.url ?: thread.url.orEmpty(),
                )
            },
            accentColor = accentColor,
            largeIcon = true,
        )
        runCatching { NotifiedThreads.remember(items.map { it.numericId }) }
        finishJob(job)
    }

    /** Finished a run: done, and immediately schedule the next one. */
    private fun finishJob(job: JobParameters) {
        jobFinished(job, false)
        scheduleNext(this)
    }

    /** Fetch failed: let the system back off, and schedule the next run too so the chain survives. */
    private fun retryLater(job: JobParameters) {
        jobFinished(job, true)
        scheduleNext(this)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntent(id: Long, url: String): PendingIntent {
        val intent = ReadNotificationService.start(applicationContext, id, url)
        return PendingIntent.getService(
            applicationContext, InputHelper.getSafeIntId(id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
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
