package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * One entry of `GET /repos/{o}/{r}/issues/{n}/timeline`.
 *
 * The endpoint returns a heterogeneous array (comments, review events and state changes). We only
 * render the state changes here because comments are already loaded from their own endpoint;
 * `commented` / `reviewed` entries are filtered out to avoid showing everything twice.
 *
 * Unknown fields and unknown event names are tolerated: a new GitHub event type must never crash
 * the screen, it is simply skipped.
 */
@Serializable
data class TimelineEvent(
    val id: Long = 0,
    val event: String? = null,
    val actor: User? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("commit_id") val commitId: String? = null,
    val label: Label? = null,
    val assignee: User? = null,
    val milestone: Milestone? = null,
    val rename: TimelineRename? = null,
    val source: TimelineSource? = null,
    @SerialName("requested_reviewer") val requestedReviewer: User? = null,
    /** `reviewed` / `committed` / `commented` entries reuse the comment body. */
    val body: String? = null,
)

@Serializable
data class TimelineRename(val from: String? = null, val to: String? = null)

@Serializable
data class TimelineSource(
    val type: String? = null,
    val issue: TimelineIssue? = null,
)

@Serializable
data class TimelineIssue(
    val number: Int = 0,
    val title: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    /** Present when the referenced thread is a pull request. */
    @SerialName("pull_request") val pullRequest: JsonObject? = null,
) {
    /** `#12` style reference, also used as the link label. */
    val reference: String get() = if (number > 0) "#$number" else title.orEmpty()
}
