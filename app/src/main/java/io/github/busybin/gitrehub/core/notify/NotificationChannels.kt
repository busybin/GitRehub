package io.github.busybin.gitrehub.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R

/**
 * One Android notification channel per thread reason group.
 *
 * Everything used to land in a single channel, so muting CI noise meant muting review requests and
 * mentions too. The mapping is deliberately coarse: five groups users can reason about, not one per
 * repository (that would flood the system settings page).
 *
 * Channel importance is fixed at `IMPORTANCE_DEFAULT` for every group. Channels are immutable once
 * created on Android, so a user who already ran an older build keeps the same id and only gains the
 * ability to mute a group.
 */
enum class NotifyChannel(
    val channelId: String,
    @param:StringRes val titleRes: Int,
) {
    MENTION("gitrehub_mention", R.string.m3_notif_channel_mention),
    REVIEW("gitrehub_review", R.string.m3_notif_channel_review),
    CI("gitrehub_ci", R.string.m3_notif_channel_ci),
    COMMENT("gitrehub_comment", R.string.m3_notif_channel_comment),
    OTHER("gitrehub_other", R.string.m3_notif_channel_other),
    ;

    companion object {
        /**
         * Group a GitHub thread reason into a channel. Unknown and null reasons fall back to
         * [OTHER] so a new server-side reason never drops a notification.
         */
        fun of(reason: String?): NotifyChannel = when (reason?.lowercase()) {
            "mention", "team_mention" -> MENTION
            "review_requested" -> REVIEW
            "ci_activity" -> CI
            "comment", "state_change" -> COMMENT
            else -> OTHER
        }

        /** Stable summary id per channel: channel order never changes, so neither do the ids. */
        fun summaryId(channel: NotifyChannel): Int = SUMMARY_ID_BASE + channel.ordinal

        const val SUMMARY_ID_BASE = 0x6200

        /** Create every channel; re-creating an existing id only refreshes its localized name. */
        fun ensure(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            entries.forEach { channel ->
                val created = NotificationChannel(
                    channel.channelId,
                    context.getString(channel.titleRes),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                created.setShowBadge(true)
                manager.createNotificationChannel(created)
            }
        }
    }
}
