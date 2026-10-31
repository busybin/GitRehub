package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** `POST /repos/{o}/{r}/releases/generate-notes`: GitHub writes the changelog from merged PRs. */
@Serializable
data class GeneratedNotes(
    val name: String = "",
    val body: String = "",
)

/**
 * One review conversation of a pull request (GraphQL `reviewThreads`).
 *
 * REST has no thread ids, so resolving a conversation has to go through GraphQL: [id] is the opaque
 * node id the mutation takes, [path]/[line] place it in the diff, and [comments] is the first page
 * (enough for the "N unresolved" list; the full thread stays on the files screen).
 */
@Serializable
data class ReviewThread(
    val id: String = "",
    val path: String? = null,
    val line: Int? = null,
    val isResolved: Boolean = false,
    val isOutdated: Boolean = false,
    val comments: ReviewThreadComments = ReviewThreadComments(),
) {
    val firstComment: ReviewThreadComment? get() = comments.nodes.firstOrNull()
}

/** GraphQL connections wrap their rows in `nodes`; the UI only ever needs the first page. */
@Serializable
data class ReviewThreadComments(
    val nodes: List<ReviewThreadComment> = emptyList(),
)

@Serializable
data class ReviewThreadComment(
    val body: String = "",
    val author: ReviewThreadAuthor? = null,
)

@Serializable
data class ReviewThreadAuthor(
    val login: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
)

/** `GET /repos/{o}/{r}/compare/{base}...{head}`: what a branch/tag comparison shows. */
@Serializable
data class CompareResult(
    val status: String = "",
    @SerialName("ahead_by") val aheadBy: Int = 0,
    @SerialName("behind_by") val behindBy: Int = 0,
    @SerialName("total_commits") val totalCommits: Int = 0,
    val commits: List<Commit> = emptyList(),
    val files: List<CommitFile> = emptyList(),
) {
    val isEmpty: Boolean get() = totalCommits == 0 && files.isEmpty()
}

/** One day of repository traffic (views or clones). */
@Serializable
data class TrafficPoint(
    @Serializable(with = InstantSerializer::class) val timestamp: Instant? = null,
    val count: Long = 0,
    val uniques: Long = 0,
)

/** `GET /repos/{o}/{r}/traffic/views` and `.../clones`; the API only keeps the last 14 days. */
@Serializable
data class TrafficSeries(
    val count: Long = 0,
    val uniques: Long = 0,
    val views: List<TrafficPoint> = emptyList(),
    val clones: List<TrafficPoint> = emptyList(),
) {
    /** Both endpoints use their own key; callers merge into one object. */
    val points: List<TrafficPoint> get() = if (views.isNotEmpty()) views else clones
}

/** `GET .../traffic/popular/referrers`. */
@Serializable
data class TrafficReferrer(
    val referrer: String = "",
    val count: Long = 0,
    val uniques: Long = 0,
)

/** `GET .../traffic/popular/paths`. */
@Serializable
data class TrafficPath(
    val path: String = "",
    val title: String = "",
    val count: Long = 0,
    val uniques: Long = 0,
)

/**
 * One open Dependabot alert. The API nests the useful bits (`security_advisory`,
 * `dependency.package`), flattened here so the UI does not walk three levels.
 */
@Serializable
data class DependabotAlert(
    val number: Long = 0,
    val state: String = "",
    val dependency: DependabotDependency? = null,
    @SerialName("security_advisory") val advisory: DependabotAdvisory? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
) {
    val packageName: String get() = dependency?.packageInfo?.name.orEmpty()
    val ecosystem: String get() = dependency?.packageInfo?.ecosystem.orEmpty()
    val severity: String get() = advisory?.severity.orEmpty()
    val summary: String get() = advisory?.summary.orEmpty()
}

@Serializable
data class DependabotDependency(
    @SerialName("package") val packageInfo: DependabotPackage? = null,
)

@Serializable
data class DependabotPackage(
    val ecosystem: String = "",
    val name: String = "",
)

@Serializable
data class DependabotAdvisory(
    val severity: String = "",
    val summary: String = "",
    @SerialName("ghsa_id") val ghsaId: String? = null,
)

/** What one workflow dispatch needs; inputs are free-form key/value pairs. */
@Serializable
data class WorkflowDispatch(
    val ref: String = "",
    val inputs: Map<String, String> = emptyMap(),
)
