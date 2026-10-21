package io.github.busybin.gitrehub.compose.data

import android.util.Base64
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.GitHubClient
import kotlinx.serialization.builtins.ListSerializer

/**
 * The repository code browser's data access: directory listings, single entries and file text.
 *
 * Nothing is cached: a directory listing is requested one level at a time, so browsing a huge
 * repository stays cheap (no recursive tree download).
 */
object RepoContent {

    /** Above this the raw response is not worth decoding into a Text composable. */
    const val MAX_PREVIEW_BYTES = 1_000_000L

    /**
     * Directory contents, directories first then files, each alphabetically (like github.com).
     * A file path answers with a single object, which is not a listing: callers use [file].
     */
    suspend fun list(
        owner: String,
        repo: String,
        path: String,
        ref: String?,
    ): List<CodeEntry> {
        val body = GitHubApi.contents(owner, repo, path, ref).body
        if (!body.trimStart().startsWith("[")) return emptyList()
        val entries = runCatching {
            GitHubClient.json.decodeFromString(ListSerializer(CodeEntry.serializer()), body)
        }.getOrNull().orEmpty()
        return entries.sortedWith(compareBy({ !it.isDir }, { it.name?.lowercase().orEmpty() }))
    }

    /** A single file entry (size/sha/html_url and, below 1 MB, base64 [CodeEntry.content]). */
    suspend fun file(
        owner: String,
        repo: String,
        path: String,
        ref: String?,
    ): CodeEntry? {
        val body = GitHubApi.contents(owner, repo, path, ref).body
        if (body.trimStart().startsWith("[")) return null
        return runCatching {
            GitHubClient.json.decodeFromString(CodeEntry.serializer(), body)
        }.getOrNull()
    }

    /**
     * File text. The contents API inlines base64 for files below 1 MB; everything else (and anything
     * the API decides not to inline) is fetched raw from the entry's own URL, which also works with
     * a token on private repositories.
     */
    suspend fun text(entry: CodeEntry): String? {
        val cached = decode(entry)
        if (cached != null) return cached
        val url = entry.url ?: return null
        return runCatching { GitHubApi.fileText(url) }.getOrNull()
    }

    /** Base64 payload of an inlined file, or null when the API did not inline it. */
    fun decode(entry: CodeEntry): String? {
        val raw = entry.content?.takeIf { it.isNotBlank() } ?: return null
        if (!entry.encoding.isNullOrEmpty() && entry.encoding != "base64") return raw
        return runCatching {
            String(Base64.decode(raw.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }
}
