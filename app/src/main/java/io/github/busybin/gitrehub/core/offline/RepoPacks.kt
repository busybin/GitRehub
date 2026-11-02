package io.github.busybin.gitrehub.core.offline

import android.content.Context
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * A whole-repository offline pack: metadata, README and (optionally) every text file of the
 * default branch.
 *
 * Layout under `filesDir/offline/repos/<hash>/`:
 * `meta.json`, `repo.json`, `readme.html`, `tree.json`, `files/<path>`.
 * The directory name is a hash so nothing in the file system names a private repository.
 */
@Serializable
data class RepoPackMeta(
    val account: String = "",
    val owner: String = "",
    val repo: String = "",
    /** Branch the pack was taken from (normally the default branch). */
    val branch: String = "",
    val headSha: String = "",
    val syncedAt: Long = 0L,
    val bytes: Long = 0L,
    val fileCount: Int = 0,
    val skipped: Int = 0,
    /** Small text files (≤ [RepoPackRules.SMALL_MAX_BYTES]). */
    val includeSmall: Boolean = false,
    /** Large text files (between small and [RepoPackRules.LARGE_MAX_BYTES]). */
    val includeLarge: Boolean = false,
    /** Issues and pull requests (metadata + body, no comments). */
    val includeIssues: Boolean = false,
    /** Refresh this pack automatically when the default branch moves. */
    val autoSync: Boolean = true,
    val issueCount: Int = 0,
    val releaseCount: Int = 0,
) {
    val fullName: String get() = "$owner/$repo"
}

/** One file in the pack's tree; [sha] lets a later sync tell whether the blob changed. */
@Serializable
data class RepoPackFile(
    val path: String = "",
    val size: Long = 0L,
    val sha: String = "",
)

@Serializable
data class RepoPackTree(val files: List<RepoPackFile> = emptyList())

object RepoPacks {

    private const val INDEX_KEY = "m3_offline_repo_packs"

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever a pack is added or removed, so the UI can re-read the index. */
    val revision: StateFlow<Int> = _revision.asStateFlow()
    const val MAX_REPO_BYTES = 200L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun digest(account: String, owner: String, repo: String): String {
        val raw = "$account|$owner/$repo"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun dir(context: Context, meta: RepoPackMeta): File =
        File(File(context.filesDir, "offline/repos"), digest(meta.account, meta.owner, meta.repo))

    /** Packs of [account], newest sync first. */
    fun index(account: String): List<RepoPackMeta> = readIndex()
        .filter { it.account == account }
        .sortedByDescending { it.syncedAt }

    fun get(account: String, owner: String, repo: String): RepoPackMeta? =
        readIndex().firstOrNull { it.account == account && it.owner == owner && it.repo == repo }

    fun totalBytes(account: String): Long = index(account).sumOf { it.bytes }

    fun repoObject(context: Context, meta: RepoPackMeta): Repo? = runCatching {
        json.decodeFromString(Repo.serializer(), File(dir(context, meta), "repo.json").readText())
    }.getOrNull()

    fun readmeHtml(context: Context, meta: RepoPackMeta): String? = runCatching {
        File(dir(context, meta), "readme.html").readText().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Issues and PRs packed with the repository (metadata + body, no comments). */
    fun issues(context: Context, meta: RepoPackMeta): List<Issue> = runCatching {
        json.decodeFromString<List<Issue>>(File(dir(context, meta), "issues.json").readText())
    }.getOrDefault(emptyList())

    fun tree(context: Context, meta: RepoPackMeta): List<RepoPackFile> = runCatching {
        json.decodeFromString(
            RepoPackTree.serializer(),
            File(dir(context, meta), "tree.json").readText(),
        ).files
    }.getOrDefault(emptyList())

    /** Direct children of [path] (`""` = repository root), directories first then files. */
    fun entries(context: Context, meta: RepoPackMeta, path: String): List<CodeEntry> {
        val prefix = if (path.isBlank()) "" else path.trim('/') + "/"
        val dirs = LinkedHashSet<String>()
        val files = ArrayList<CodeEntry>()
        tree(context, meta).forEach { file ->
            if (!file.path.startsWith(prefix)) return@forEach
            val rest = file.path.removePrefix(prefix)
            if (rest.isBlank()) return@forEach
            val slash = rest.indexOf('/')
            if (slash >= 0) {
                dirs += rest.substring(0, slash)
            } else {
                val entry = CodeEntry(
                    name = rest,
                    path = file.path,
                    type = "file",
                    size = file.size,
                )
                files += entry
            }
        }
        return dirs.map { name ->
            CodeEntry(
                name = name,
                path = (prefix + name).trim('/'),
                type = "dir",
            )
        } + files.sortedBy { it.name.orEmpty().lowercase() }
    }

    /** Raw text of one packaged file; null when it was skipped or is not part of the pack. */
    fun text(context: Context, meta: RepoPackMeta, path: String): String? {
        val safe = safePath(path) ?: return null
        return runCatching { File(File(dir(context, meta), "files"), safe).readText() }.getOrNull()
    }

    /** Wipes the old pack and creates an empty `files/` tree; returns the pack directory. */
    fun prepare(context: Context, meta: RepoPackMeta): File {
        val target = dir(context, meta)
        runCatching { target.deleteRecursively() }
        File(target, "files").mkdirs()
        return target
    }

    /** Writes one fetched file; paths are sanitised so nothing escapes the pack directory. */
    fun writeText(context: Context, meta: RepoPackMeta, path: String, text: String) {
        val safe = safePath(path) ?: return
        val out = File(File(dir(context, meta), "files"), safe)
        out.parentFile?.mkdirs()
        runCatching { out.writeText(text) }
    }

    /** Writes the metadata files and publishes the pack to the index. */
    fun finish(
        context: Context,
        meta: RepoPackMeta,
        repo: Repo,
        readmeHtml: String?,
        files: List<RepoPackFile>,
        issues: List<Issue> = emptyList(),
    ) {
        val target = dir(context, meta)
        File(target, "meta.json").writeText(json.encodeToString(meta))
        File(target, "repo.json").writeText(json.encodeToString(repo))
        File(target, "tree.json").writeText(json.encodeToString(RepoPackTree(files)))
        readmeHtml?.let { File(target, "readme.html").writeText(it) }
        File(target, "issues.json").writeText(json.encodeToString(issues))
        val next = (readIndex().filterNot {
            it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo
        } + meta).sortedByDescending { it.syncedAt }
        writeIndex(next, context)
    }

    fun remove(context: Context, meta: RepoPackMeta) {
        runCatching { dir(context, meta).deleteRecursively() }
        writeIndex(
            readIndex().filterNot {
                it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo
            },
            context,
        )
    }

    /** Drops every pack of [account]; called whenever the account changes or signs out. */
    fun clearAccount(account: String) {
        val context = App.getInstance()
        readIndex().filter { it.account == account }.forEach { meta ->
            runCatching { dir(context, meta).deleteRecursively() }
        }
        writeIndex(readIndex().filterNot { it.account == account }, context)
    }

    fun clearAll() {
        val context = App.getInstance()
        runCatching { File(context.filesDir, "offline/repos").deleteRecursively() }
        writeIndex(emptyList(), context)
    }

    /** Path traversal guard: packaged paths must stay inside the pack directory. */
    fun safePath(path: String): String? {
        val clean = path.trim('/').replace('\\', '/')
        if (clean.isBlank()) return null
        if (clean.split('/').any { it == ".." || it == "." }) return null
        return clean
    }

    private fun writeIndex(items: List<RepoPackMeta>, context: Context) {
        _revision.value = _revision.value + 1
        val kept = items.sortedByDescending { it.syncedAt }
        // Enforce the global quota: keep the newest packs until the budget is used up, delete the rest.
        var used = 0L
        val survivors = kept.filter { meta ->
            val fits = used + meta.bytes <= MAX_TOTAL_BYTES
            if (fits) used += meta.bytes else runCatching { dir(context, meta).deleteRecursively() }
            fits
        }
        runCatching { PrefHelper.putAny(INDEX_KEY, json.encodeToString(survivors)) }
    }

    private fun readIndex(): List<RepoPackMeta> = runCatching {
        val raw = PrefHelper.getString(INDEX_KEY) ?: return emptyList()
        json.decodeFromString<List<RepoPackMeta>>(raw)
    }.getOrDefault(emptyList())
}
