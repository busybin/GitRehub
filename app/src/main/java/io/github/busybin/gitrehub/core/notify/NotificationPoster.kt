package io.github.busybin.gitrehub.core.notify

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.provider.markdown.MarkDownProvider
import io.github.busybin.gitrehub.provider.tasks.notification.NotificationActions

/**
 * The single place that builds notification-shade entries.
 *
 * Both pollers ([io.github.busybin.gitrehub.provider.tasks.notification.NotificationForegroundService]
 * and `NotificationSchedulerJobTask`) used to carry their own copy of the builder; they drifted
 * (different channels, different grouping, different text) and fixing a bug in one left the other
 * broken. Everything now goes through [post].
 */
object NotificationPoster {

    /** A thread plus, when the poller could fetch it, its latest comment. */
    data class Pending(val thread: Notification, val comment: Comment? = null)

    /**
     * Post one notification per thread, grouped per [NotifyChannel], with a per-channel summary
     * when that channel has more than one entry. [unreadTotal] drives the launcher badge.
     */
    fun post(
        context: Context,
        items: List<Pending>,
        unreadTotal: Int,
        contentIntent: (Notification) -> PendingIntent,
        accentColor: Int? = null,
        largeIcon: Boolean = false,
    ) {
        if (items.isEmpty()) return
        NotifyChannel.ensure(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        items.groupBy { NotifyChannel.of(it.thread.reason) }.forEach { (channel, group) ->
            group.forEach { pending ->
                manager.notify(
                    NotificationActions.managerId(pending.thread.numericId),
                    item(
                        context = context,
                        channel = channel,
                        pending = pending,
                        contentIntent = contentIntent,
                        accentColor = accentColor,
                        largeIcon = largeIcon,
                        badge = if (group.size == 1) unreadTotal else null,
                    ),
                )
            }
            if (group.size > 1) {
                manager.notify(
                    NotifyChannel.summaryId(channel),
                    summary(context, channel, group.size, unreadTotal, contentIntent, group.first().thread),
                )
            }
        }
    }

    private fun item(
        context: Context,
        channel: NotifyChannel,
        pending: Pending,
        contentIntent: (Notification) -> PendingIntent,
        accentColor: Int?,
        largeIcon: Boolean,
        badge: Int?,
    ): android.app.Notification {
        val thread = pending.thread
        val comment = pending.comment
        val title = comment?.user?.login?.takeIf { it.isNotBlank() }
            ?: thread.subject?.title
            ?: context.getString(R.string.m3_notification)
        val body = comment?.let { MarkDownProvider.stripMdText(it.body) }
            ?: thread.repoFullName.orEmpty()
        val builder = NotificationCompat.Builder(context, channel.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(thread))
            .setWhen((comment?.createdAt ?: thread.updatedAt)?.toEpochMilli() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setGroup(channel.channelId)
        badge?.let {
            builder.setNumber(it).setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        }
        if (comment != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body),
            )
        }
        accentColor?.let(builder::setColor)
        if (largeIcon) {
            builder.setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher),
            )
        }
        NotificationActions.attach(
            context, builder, thread, NotificationActions.readIntent(context, thread.numericId),
        )
        return builder.build()
    }

    private fun summary(
        context: Context,
        channel: NotifyChannel,
        count: Int,
        unreadTotal: Int,
        contentIntent: (Notification) -> PendingIntent,
        any: Notification,
    ): android.app.Notification = NotificationCompat.Builder(context, channel.channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.m3_section_notifications))
        .setContentText(context.getString(R.string.m3_notif_new_count, count))
        .setGroup(channel.channelId)
        .setGroupSummary(true)
        .setAutoCancel(true)
        .setContentIntent(contentIntent(any))
        // Launcher badge: Android shows at most a dot on most launchers, the number is launcher
        // dependent. Still the only standard way to hint at the count.
        .setNumber(unreadTotal)
        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        .build()
}
