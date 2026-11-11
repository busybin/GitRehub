package io.github.busybin.gitrehub.provider.tasks.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.os.bundleOf
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.helper.BundleConstant
import io.github.busybin.gitrehub.helper.InputHelper

/**
 * Notification actions shared by the two pollers: mark read, Done (unsubscribe) and an inline reply
 * for issue / pull-request threads.
 *
 * Done is GitHub's own "Done" button: `DELETE /notifications/threads/{id}/subscription` also marks
 * the thread read and takes it out of the inbox, which is why the service handles both in one call.
 *
 * Every action carries the login of the account the thread belongs to: the pollers watch all stored
 * accounts, so "as the current account" is not good enough for mark-read, Done or reply.
 */
object NotificationActions {

    const val KEY_REPLY = "gitrehub_reply"

    /** Where a reply goes; null for commit/release threads that have no comment endpoint. */
    data class ReplyTarget(
        val owner: String,
        val repo: String,
        val number: Int,
        val threadId: Long,
        val login: String? = null,
    )

    /** Parses `…/repos/{owner}/{repo}/issues|pulls/{number}`. */
    fun replyTarget(thread: Notification, login: String? = null): ReplyTarget? {
        val url = thread.subject?.url ?: return null
        val parts = url.removeSuffix("/").split("/")
        val repos = parts.indexOf("repos")
        if (repos < 0 || parts.size < repos + 5) return null
        if (parts[repos + 3] != "issues" && parts[repos + 3] != "pulls") return null
        val number = parts[repos + 4].toIntOrNull() ?: return null
        return ReplyTarget(parts[repos + 1], parts[repos + 2], number, thread.numericId, login)
    }

    /** Appends the three actions to [builder]; callers set the content intent themselves. */
    fun attach(
        context: Context,
        builder: NotificationCompat.Builder,
        thread: Notification,
        login: String?,
        readIntent: PendingIntent,
    ) {
        builder.addAction(
            R.drawable.ic_eye_off,
            context.getString(R.string.mark_as_read),
            readIntent,
        )
        builder.addAction(
            R.drawable.ic_done,
            context.getString(R.string.m3_notif_done),
            doneIntent(context, thread.numericId, login),
        )
        val target = replyTarget(thread, login) ?: return
        val remote = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.getString(R.string.m3_notif_reply_hint))
            .build()
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            context.getString(R.string.m3_notif_reply),
            replyIntent(context, target),
        ).addRemoteInput(remote).build()
        builder.addAction(action)
    }

    /**
     * Direct reply. Android requires the broadcast to be mutable for RemoteInput to carry the text
     * back, so this one PendingIntent is not IMMUTABLE (the receiver is not exported).
     */
    fun replyIntent(context: Context, target: ReplyTarget): PendingIntent {
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtras(
                bundleOf(
                    "owner" to target.owner,
                    "repo" to target.repo,
                    "number" to target.number,
                    "thread" to target.threadId,
                    BundleConstant.ACCOUNT to target.login,
                )
            )
        }
        return PendingIntent.getBroadcast(
            context,
            target.threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun doneIntent(context: Context, id: Long, login: String?): PendingIntent {
        val intent = Intent(context, ReadNotificationService::class.java).apply {
            action = ReadNotificationService.ACTION_DONE
        }.putExtras(
            bundleOf(
                BundleConstant.EXTRA_TYPE to ReadNotificationService.UN_SUBSCRIBE,
                BundleConstant.ID to id,
                BundleConstant.ACCOUNT to login,
            )
        )
        return PendingIntent.getService(
            context,
            (id + 500_000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Mark one thread read from the shade (READ_SINGLE). */
    fun readIntent(context: Context, id: Long, login: String?): PendingIntent {
        val intent = Intent(context, ReadNotificationService::class.java).apply {
            action = ReadNotificationService.ACTION_READ
        }.putExtras(
            bundleOf(
                BundleConstant.EXTRA_TYPE to ReadNotificationService.READ_SINGLE,
                BundleConstant.ID to id,
                BundleConstant.ACCOUNT to login,
            )
        )
        return PendingIntent.getService(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Notification ids are longs; `NotificationManager` takes an int. */
    fun managerId(id: Long): Int = InputHelper.getSafeIntId(id)
}
