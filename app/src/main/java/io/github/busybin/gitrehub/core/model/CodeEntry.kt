package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry of `GET /repos/{o}/{r}/contents/{path}`. The endpoint answers an array for a directory
 * and a single object for a file; [content] (base64) is only inlined for files below 1 MB.
 */
@Serializable
data class CodeEntry(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null,
    val size: Long = 0,
    val type: String? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val content: String? = null,
    val encoding: String? = null,
) {
    val isDir: Boolean get() = type == "dir"

    val isSymlink: Boolean get() = type == "symlink" || type == "submodule"

    val isImage: Boolean get() = IMAGE_EXTENSIONS.any { name?.lowercase()?.endsWith(it) == true }

    companion object {
        val IMAGE_EXTENSIONS = listOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico", ".svg",
        )
    }
}

/**
 * `GET /repos/{o}/{r}/git/trees/{ref}?recursive=1`: every path of one ref in a single answer.
 *
 * [truncated] means GitHub capped the list (very large repositories), so a file finder built on it
 * may be missing paths; the UI says so instead of pretending the file does not exist.
 */
@Serializable
data class GitTree(
    val sha: String = "",
    val tree: List<GitTreeEntry> = emptyList(),
    val truncated: Boolean = false,
) {
    /** Blob paths only (trees are directories) — what a file finder should offer. */
    val files: List<String>
        get() = tree.filter { it.type == "blob" && it.path.isNotBlank() }.map { it.path }
}

@Serializable
data class GitTreeEntry(
    val path: String = "",
    val mode: String? = null,
    val type: String? = null,
    val sha: String? = null,
    val size: Long = 0,
)
