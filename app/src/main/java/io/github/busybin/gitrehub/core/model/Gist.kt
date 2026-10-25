package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A gist (`GET /gists`). Every file's text rides along in [files]; the list endpoint already
 * carries the first part of each file, [GistFile.truncated] marks where it stops.
 */
@Serializable
data class Gist(
    val id: String = "",
    val description: String? = null,
    val public: Boolean = false,
    val owner: User? = null,
    val files: Map<String, GistFile> = emptyMap(),
    val comments: Int = 0,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
) {
    val filesSorted: List<GistFile> get() = files.values.sortedBy { it.filename }

    /** Description, else the first file name — some gists have neither. */
    val title: String
        get() = description?.takeIf { it.isNotBlank() }
            ?: filesSorted.firstOrNull()?.filename.orEmpty().ifBlank { id }
}

/**
 * One file of a gist. GitHub answers `truncated: true` for files over ~1 MB, in which case only
 * the beginning is in [content] and the whole file is behind [rawUrl].
 */
@Serializable
data class GistFile(
    val filename: String = "",
    val type: String? = null,
    val language: String? = null,
    @SerialName("raw_url") val rawUrl: String? = null,
    val size: Int = 0,
    val content: String? = null,
    val truncated: Boolean = false,
)
