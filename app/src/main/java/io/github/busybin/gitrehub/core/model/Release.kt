package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** A release (`GET /repos/{o}/{r}/releases`); [body] is markdown. */
@Serializable
data class Release(
    val id: Long = 0,
    val name: String? = null,
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    @SerialName("prerelease") val preRelease: Boolean = false,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("published_at") @Serializable(with = InstantSerializer::class) val publishedAt: Instant? = null,
    val author: User? = null,
    val assets: List<ReleaseAsset> = emptyList(),
    @SerialName("target_commitish") val targetCommitish: String? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: tagName.orEmpty()

    val published: Instant? get() = publishedAt ?: createdAt
}

/** One downloadable asset of a release; the download goes through the system DownloadManager. */
@Serializable
data class ReleaseAsset(
    val id: Long = 0,
    val name: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    @SerialName("download_count") val downloadCount: Long = 0,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
)
