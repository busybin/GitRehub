package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One notification thread (`GET /notifications`).
 *
 * The thread id is a string on the wire even though it is numeric on github.com; keeping it raw
 * means a non-numeric id (mocks, enterprise) still decodes. Use [numericId] where an `Int`/`Long`
 * is required (notification manager ids, the local dedupe queue).
 */
@Serializable
data class Notification(
    val id: String = "",
    val repository: Repo? = null,
    val subject: NotificationSubject? = null,
    /** Why this thread found me: `mention`, `review_requested`, `ci_activity`, ... */
    val reason: String? = null,
    val url: String? = null,
    val unread: Boolean = false,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("last_read_at") @Serializable(with = InstantSerializer::class) val lastReadAt: Instant? = null,
    @SerialName("subscription_url") val subscriptionUrl: String? = null,
) {
    /** Stable `Long` for callers that need one; numeric ids stay exact. */
    val numericId: Long get() = id.toLongOrNull() ?: id.hashCode().toLong()

    /** `owner/repo`, empty for threads that do not belong to a repository. */
    val repoFullName: String? get() = repository?.nameWithOwner?.takeIf { it.isNotBlank() }
}

@Serializable
data class NotificationSubject(
    val title: String? = null,
    val url: String? = null,
    /** `Issue`, `PullRequest`, `Commit`, `Discussion`, `Release`, `CheckSuite`, ... */
    val type: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null,
)

/** `GET /notifications/threads/{id}/subscription`: `ignored` is GitHub's mute flag. */
@Serializable
data class ThreadSubscription(
    val subscribed: Boolean = false,
    val ignored: Boolean = false,
    val reason: String? = null,
)
