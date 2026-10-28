package io.github.busybin.gitrehub.provider.tasks.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.notify.NotifiedThreads
import io.github.busybin.gitrehub.helper.AppHelper
import io.github.busybin.gitrehub.helper.InputHelper
import io.github.busybin.gitrehub.helper.ParseDateFormat
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.provider.markdown.MarkDownProvider
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
        val context = applicationContext
        val accentColor = ContextCompat.getColor(this, R.color.material_blue_700)
        val first = items[0]
        // Skip the newest thread (it is the summary) and anything already shown in an earlier run.
        val shown = NotifiedThreads.ids()
        val targets = unread
            .filter { first.id != it.id && it.numericId !in shown }
            .take(10)
        targets.forEach { thread ->
            val comment = thread.subject?.latestCommentUrl?.let { url ->
                runCatching { GitHubApi.commentAt(url) }.getOrNull()
            }
            if (comment?.user != null) {
                showWithComment(context, accentColor, thread, comment)
            } else {
                showWithoutComment(context, accentColor, thread)
            }
        }
        if (first.numericId !in shown) {
            val grouped = getSummaryGroupNotification(first, accentColor, items.size > 1)
            showNotification(first.numericId, grouped)
        }
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

    private fun showWithoutComment(context: Context, accentColor: Int, thread: Notification) {
        val title = thread.subject?.title ?: getString(R.string.m3_notification)
        val url = thread.subject?.url ?: thread.url.orEmpty()
        val toAdd = getNotification(title, thread.repoFullName ?: "general", thread.repoFullName ?: "general")
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentIntent(getPendingIntent(thread.numericId, url))
            .addAction(
                R.drawable.ic_open, context.getString(R.string.open),
                getPendingIntent(thread.numericId, url),
            )
            .addAction(
                R.drawable.ic_eye_off, context.getString(R.string.mark_as_read),
                getReadOnlyPendingIntent(thread.numericId, url),
            )
            .setWhen(thread.updatedAt?.toEpochMilli() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setColor(accentColor)
            .setGroup(NOTIFICATION_GROUP_ID)
            .build()
        showNotification(thread.numericId, toAdd)
    }

    private fun showWithComment(
        context: Context,
        accentColor: Int,
        thread: Notification,
        comment: Comment,
    ) {
        val login = comment.user?.login.orEmpty()
        val body = MarkDownProvider.stripMdText(comment.body)
        val url = thread.subject?.url ?: thread.url.orEmpty()
        @SuppressLint("LaunchActivityFromNotification") val toAdd = getNotification(
            login,
            body,
            thread.repoFullName ?: "general",
        )
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(login)
                    .bigText(body),
            )
            .setWhen(comment.createdAt?.toEpochMilli() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(
                R.drawable.ic_open, context.getString(R.string.open),
                getPendingIntent(thread.numericId, url),
            )
            .addAction(
                R.drawable.ic_eye_off, context.getString(R.string.mark_as_read),
                getReadOnlyPendingIntent(thread.numericId, url),
            )
            .setContentIntent(getPendingIntent(thread.numericId, url))
            .setColor(accentColor)
            .setGroup(NOTIFICATION_GROUP_ID)
            .build()
        showNotification(thread.numericId, toAdd)
    }

    private fun getSummaryGroupNotification(
        thread: Notification,
        accentColor: Int,
        toNotificationActivity: Boolean,
    ): android.app.Notification {
        @SuppressLint("UnspecifiedImmutableFlag") val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainComposeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val url = thread.subject?.url ?: thread.url.orEmpty()
        val builder = getNotification(
            thread.subject?.title ?: getString(R.string.m3_notification),
            thread.repoFullName ?: "general",
            thread.repoFullName ?: "general",
        )
            .setContentIntent(
                if (toNotificationActivity) pendingIntent else getPendingIntent(thread.numericId, url),
            )
            .addAction(
                R.drawable.ic_open, getString(R.string.open),
                getPendingIntent(thread.numericId, url),
            )
            .addAction(
                R.drawable.ic_eye_off, getString(R.string.mark_as_read),
                getReadOnlyPendingIntent(thread.numericId, url),
            )
            .setWhen(thread.updatedAt?.toEpochMilli() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setGroup(NOTIFICATION_GROUP_ID)
            .setGroupSummary(true)
        if (PrefGetter.isNotificationSoundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(PrefGetter.notificationSound, AudioManager.STREAM_NOTIFICATION)
        }
        return builder.build()
    }

    private fun getNotification(
        title: String,
        message: String,
        channelName: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(this, channelName)
            // A notification without a small icon does not show (or crashes) on Android.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)

    private fun showNotification(id: Long, notification: android.app.Notification) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                notification.channelId,
                notification.channelId,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationChannel.setShowBadge(true)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        notificationManager.notify(InputHelper.getSafeIntId(id), notification)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getReadOnlyPendingIntent(id: Long, url: String): PendingIntent {
        val intent = ReadNotificationService.start(applicationContext, id, url, true)
        return PendingIntent.getService(
            applicationContext, InputHelper.getSafeIntId(id) / 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
        private const val NOTIFICATION_GROUP_ID = "GitRehub"

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
