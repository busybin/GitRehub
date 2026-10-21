package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A commit as the commits endpoints return it: the web identity in [author]/[committer], the git
 * identity and message inside [commit]. Detail responses add [stats] and [files].
 */
@Serializable
data class Commit(
    val sha: String = "",
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    val author: User? = null,
    val committer: User? = null,
    val commit: GitCommit? = null,
    val stats: CommitStats? = null,
    val files: List<CommitFile> = emptyList(),
    val parents: List<CommitRef> = emptyList(),
) {
    val message: String get() = commit?.message.orEmpty()

    /** First line of the message, which is what every list shows. */
    val messageTitle: String get() = message.lineSequence().firstOrNull().orEmpty()

    /** Login when the author has an account, else the git name (unmatched e-mail commits). */
    val authorName: String? get() = author?.login ?: commit?.author?.name

    val date: Instant? get() = commit?.author?.date ?: commit?.committer?.date

    val shortSha: String get() = sha.take(7)
}

@Serializable
data class GitCommit(
    val message: String = "",
    val author: GitIdentity? = null,
    val committer: GitIdentity? = null,
)

@Serializable
data class GitIdentity(
    val name: String? = null,
    val email: String? = null,
    @Serializable(with = InstantSerializer::class) val date: Instant? = null,
)

@Serializable
data class CommitStats(
    val total: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
)

/**
 * One file of a commit or pull request. [patch] is the unified diff hunk; GitHub omits it for very
 * large diffs (the UI then points at the browser instead of showing an empty box).
 */
@Serializable
data class CommitFile(
    val sha: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    @SerialName("blob_url") val blobUrl: String? = null,
    @SerialName("raw_url") val rawUrl: String? = null,
    @SerialName("contents_url") val contentsUrl: String? = null,
    @SerialName("previous_filename") val previousFilename: String? = null,
    val patch: String? = null,
)

@Serializable
data class CommitRef(
    val sha: String = "",
    val url: String? = null,
)
