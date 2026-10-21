package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The pull-request object (`GET /repos/{o}/{r}/pulls/{n}`): the numbers the issue shape does not
 * carry (merge state, diff size, branches). `state` stays raw for the same reason as [Issue.state].
 */
@Serializable
data class PullRequest(
    val id: Long = 0,
    val number: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val state: String = "open",
    val locked: Boolean = false,
    val merged: Boolean = false,
    val mergeable: Boolean? = null,
    val draft: Boolean = false,
    val commits: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    @SerialName("changed_files") val changedFiles: Int = 0,
    val comments: Int = 0,
    @SerialName("review_comments") val reviewComments: Int = 0,
    val user: User? = null,
    val assignee: User? = null,
    val assignees: List<User> = emptyList(),
    val labels: List<Label> = emptyList(),
    val milestone: Milestone? = null,
    val reactions: Reactions? = null,
    val head: PullRequestBranch? = null,
    val base: PullRequestBranch? = null,
    @SerialName("merge_commit_sha") val mergeCommitSha: String? = null,
    @SerialName("mergeable_state") val mergeableState: String? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("merged_at") @Serializable(with = InstantSerializer::class) val mergedAt: Instant? = null,
) {
    val isClosed: Boolean get() = state.equals("closed", ignoreCase = true)
}

/**
 * `PUT /pulls/{n}/merge` answers `{merged, sha, message}`. A 200 with `merged=false` happens when
 * the server refuses after accepting the request, so callers must check the flag, not the status.
 */
@Serializable
data class MergeResult(
    val merged: Boolean = false,
    val sha: String? = null,
    val message: String? = null,
)

/** `head`/`base` of a PR: the ref name is what the write paths and the diff view need. */
@Serializable
data class PullRequestBranch(
    val label: String? = null,
    val ref: String? = null,
    val sha: String? = null,
)
