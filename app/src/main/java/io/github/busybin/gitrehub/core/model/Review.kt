package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** A pull-request review (`GET /pulls/{n}/reviews`); [state] stays raw. */
@Serializable
data class Review(
    val id: Long = 0,
    val user: User? = null,
    val body: String? = null,
    val state: String? = null,
    @SerialName("commit_id") val commitId: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("submitted_at") @Serializable(with = InstantSerializer::class) val submittedAt: Instant? = null,
)

/**
 * An inline review comment (`GET /pulls/{n}/comments`). [line]/[side] are the modern anchors; the
 * older [position] fields are what GitHub still fills for comments on outdated diffs.
 */
@Serializable
data class ReviewComment(
    val id: Long = 0,
    val body: String? = null,
    val path: String? = null,
    val line: Int? = null,
    @SerialName("original_line") val originalLine: Int? = null,
    val side: String? = null,
    @SerialName("start_line") val startLine: Int? = null,
    @SerialName("in_reply_to_id") val inReplyToId: Long? = null,
    @SerialName("diff_hunk") val diffHunk: String? = null,
    @SerialName("pull_request_review_id") val pullRequestReviewId: Long = 0,
    @SerialName("author_association") val authorAssociation: String? = null,
    val user: User? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)

/**
 * One check run of a commit: a GitHub Actions job or any other app's check. The merge box shows
 * whether checks pass, which is half of "can I merge this?".
 */
@Serializable
data class CheckRun(
    val id: Long = 0,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
) {
    /** The word to show: `queued`/`in_progress` while it runs, the conclusion once it is done. */
    val word: String? get() = conclusion ?: status

    val isDone: Boolean get() = status == "completed"

    val isOk: Boolean
        get() = conclusion == "success" || conclusion == "neutral" || conclusion == "skipped"
}

/** `GET /commits/{ref}/check-runs` wraps the list in an object. */
@Serializable
data class CheckRuns(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("check_runs") val checkRuns: List<CheckRun> = emptyList(),
)
