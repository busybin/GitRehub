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
 * The directory name is a hash, which keeps paths short and stable; the JSON inside still names the
 * account and repository (it has to, the pack is readable offline without the account store).
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
    /** Total size on disk: packed file bytes plus the JSON/README metadata written with them. */
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
    /** Per account: one account saving packs must never evict another account's packs. */
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024

    /** Serializes the read-modify-write of the index and file deletion with publishing. */
    private val lock = Any()

    /** In-progress download. Never indexed and never read; only renamed into place on success. */
    private const val STAGING_SUFFIX = ".staging"

    /** Previous pack during the rename; restored if publishing is interrupted. */
    private const val BACKUP_SUFFIX = ".old"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun digest(account: String, owner: String, repo: String): String {
        val raw = "$account|$owner/$repo"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun dir(context: Context, meta: RepoPackMeta): File =
        File(File(context.filesDir, "offline/repos"), digest(meta.account, meta.owner, meta.repo))

    private fun stagingDir(context: Context, meta: RepoPackMeta): File =
        File(File(context.filesDir, "offline/repos"), digest(meta.account, meta.owner, meta.repo) + STAGING_SUFFIX)

    private fun backupDir(context: Context, meta: RepoPackMeta): File =
        File(File(context.filesDir, "offline/repos"), digest(meta.account, meta.owner, meta.repo) + BACKUP_SUFFIX)

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

    /** Raw bytes of one packaged file; used by the code screen and tests. */
    fun bytes(context: Context, meta: RepoPackMeta, path: String): ByteArray? {
        val safe = safePath(path) ?: return null
        return runCatching { File(File(dir(context, meta), "files"), safe).readBytes() }.getOrNull()
    }

    /** Whether the published pack already has this file on disk (incremental sync reuses it). */
    fun hasFile(context: Context, meta: RepoPackMeta, path: String): Boolean {
        val safe = safePath(path) ?: return false
        return File(File(dir(context, meta), "files"), safe).isFile
    }

    /**
     * Creates an empty staging tree for the download; returns that directory.
     *
     * The published pack is left untouched until [finish] succeeds, so a cancelled or failed
     * refresh can never destroy the copy the user already had.
     */
    fun prepare(context: Context, meta: RepoPackMeta): File {
        val staging = stagingDir(context, meta)
        runCatching { staging.deleteRecursively() }
        File(staging, "files").mkdirs()
        return staging
    }

    /** Writes one fetched file into the staging tree; a failed write aborts the whole sync. */
    fun writeText(context: Context, meta: RepoPackMeta, path: String, text: String) {
        writeBytes(context, meta, path, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Byte-level write used by the zipball path. The archive already contains UTF-8 bytes, so this
     * avoids decoding and re-encoding every file just to compute its size.
     */
    fun writeBytes(context: Context, meta: RepoPackMeta, path: String, data: ByteArray) {
        val safe = safePath(path) ?: return
        val out = File(File(stagingDir(context, meta), "files"), safe)
        out.parentFile?.mkdirs()
        out.writeBytes(data)
    }

    /** Flips auto-sync for one published pack without touching its files. */
    fun setAutoSync(context: Context, meta: RepoPackMeta, enabled: Boolean) {
        synchronized(lock) {
            val next = readIndex().map {
                if (it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo) {
                    it.copy(autoSync = enabled)
                } else {
                    it
                }
            }
            writeIndex(next, context)
        }
    }

    /** Drops the staged download after a failure or a cancel. Published packs are untouched. */
    fun abort(context: Context, meta: RepoPackMeta) {
        runCatching { stagingDir(context, meta).deleteRecursively() }
    }

    /** Writes the metadata into the staging tree and publishes it to the index. */
    fun finish(
        context: Context,
        meta: RepoPackMeta,
        repo: Repo,
        readmeHtml: String?,
        files: List<RepoPackFile>,
        issues: List<Issue> = emptyList(),
    ) {
        val staging = stagingDir(context, meta)
        if (!staging.isDirectory) File(staging, "files").mkdirs()
        val repoJson = json.encodeToString(repo)
        val treeJson = json.encodeToString(RepoPackTree(files))
        val issuesJson = json.encodeToString(issues)
        val readme = readmeHtml.orEmpty()
        // The quota must cover the metadata too: an issues-only pack has no file bytes and would
        // otherwise be free, and tree.json grows with every file in the repository.
        val metadataBytes = listOf(repoJson, treeJson, issuesJson, readme)
            .sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } + 512
        val finalMeta = meta.copy(bytes = meta.bytes + metadataBytes)
        File(staging, "meta.json").writeText(json.encodeToString(finalMeta))
        File(staging, "repo.json").writeText(repoJson)
        File(staging, "tree.json").writeText(treeJson)
        readmeHtml?.let { File(staging, "readme.html").writeText(it) }
        File(staging, "issues.json").writeText(issuesJson)
        publish(context, finalMeta)
    }

    /**
     * Swaps the staging tree in. The old directory is kept as `.old` until the rename succeeded and
     * is restored if it did not, so a crash mid-publish leaves a recoverable copy (see [recover]).
     */
    private fun publish(context: Context, meta: RepoPackMeta) {
        synchronized(lock) {
            val staging = stagingDir(context, meta)
            val target = dir(context, meta)
            val backup = backupDir(context, meta)
            runCatching { backup.deleteRecursively() }
            val hadTarget = target.exists()
            if (hadTarget && !target.renameTo(backup)) {
                throw IllegalStateException("Cannot replace the offline pack")
            }
            if (!staging.renameTo(target)) {
                if (hadTarget) runCatching { backup.renameTo(target) }
                throw IllegalStateException("Cannot publish the offline pack")
            }
            runCatching { backup.deleteRecursively() }
            val next = (readIndex().filterNot {
                it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo
            } + meta).sortedByDescending { it.syncedAt }
            writeIndex(next, context)
        }
    }

    fun remove(context: Context, meta: RepoPackMeta) {
        synchronized(lock) {
            deleteFiles(context, meta)
            writeIndex(
                readIndex().filterNot {
                    it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo
                },
                context,
            )
        }
    }

    /** Drops every pack of [account]; called whenever the account changes or signs out. */
    fun clearAccount(account: String) {
        synchronized(lock) {
            val context = App.getInstance()
            readIndex().filter { it.account == account }.forEach { meta -> deleteFiles(context, meta) }
            // A download killed by the process leaves a directory that is not in the index yet. Its
            // meta.json names the account once publishing started; without one it is deleted on the
            // next [recover] instead of being published for an account that is gone.
            File(context.filesDir, "offline/repos").listFiles()?.forEach { file ->
                if (!file.isDirectory) return@forEach
                val owner = runCatching {
                    json.decodeFromString(RepoPackMeta.serializer(), File(file, "meta.json").readText()).account
                }.getOrNull()
                if (owner == account) runCatching { file.deleteRecursively() }
            }
            writeIndex(readIndex().filterNot { it.account == account }, context)
        }
    }

    fun clearAll() {
        synchronized(lock) {
            val context = App.getInstance()
            runCatching { File(context.filesDir, "offline/repos").deleteRecursively() }
            writeIndex(emptyList(), context)
        }
    }

    /**
     * Finishes or rolls back a publish that the process interrupted. Called once at startup:
     * a complete staging tree is published, an incomplete one is thrown away, and a `.old`
     * directory is restored only when the target is missing.
     */
    fun recover(context: Context) {
        val root = File(context.filesDir, "offline/repos")
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        dirs.filter { it.name.endsWith(STAGING_SUFFIX) }.forEach { staging ->
            val target = File(root, staging.name.removeSuffix(STAGING_SUFFIX))
            val complete = File(staging, "meta.json").isFile && File(staging, "files").isDirectory
            when {
                !complete -> runCatching { staging.deleteRecursively() }
                target.exists() -> runCatching { staging.deleteRecursively() }
                !staging.renameTo(target) -> runCatching { staging.deleteRecursively() }
            }
        }
        dirs.filter { it.name.endsWith(BACKUP_SUFFIX) }.forEach { backup ->
            val target = File(root, backup.name.removeSuffix(BACKUP_SUFFIX))
            if (target.exists()) {
                runCatching { backup.deleteRecursively() }
            } else {
                runCatching { backup.renameTo(target) }
            }
        }
        // A crash between the rename and the index write leaves a complete pack that the index
        // does not know about. It would be invisible and impossible to delete from the UI, so put
        // it back into the index (writeIndex applies the per-account quota).
        val known = readIndex()
        val orphans = root.listFiles()?.filter {
            it.isDirectory && !it.name.endsWith(STAGING_SUFFIX) && !it.name.endsWith(BACKUP_SUFFIX)
        }.orEmpty().mapNotNull { dir ->
            runCatching {
                json.decodeFromString(RepoPackMeta.serializer(), File(dir, "meta.json").readText())
            }.getOrNull()
        }.filter { meta ->
            known.none {
                it.account == meta.account && it.owner == meta.owner && it.repo == meta.repo
            }
        }
        if (orphans.isNotEmpty()) writeIndex(known + orphans, context)
    }

    private fun deleteFiles(context: Context, meta: RepoPackMeta) {
        runCatching { dir(context, meta).deleteRecursively() }
        runCatching { stagingDir(context, meta).deleteRecursively() }
        runCatching { backupDir(context, meta).deleteRecursively() }
    }

    /** Path traversal guard: packaged paths must stay inside the pack directory. */
    fun safePath(path: String): String? {
        val clean = path.trim('/').replace('\\', '/')
        if (clean.isBlank()) return null
        if (clean.split('/').any { it == ".." || it == "." }) return null
        return clean
    }

    private fun writeIndex(items: List<RepoPackMeta>, context: Context) {
        synchronized(lock) {
            _revision.value = _revision.value + 1
            val kept = items.sortedByDescending { it.syncedAt }
            // Enforce the quota per account: one account saving packs must never delete another
            // account's packs. Within an account keep the newest until the budget is used up.
            val used = HashMap<String, Long>()
            val survivors = kept.filter { meta ->
                val current = used[meta.account] ?: 0L
                val fits = current + meta.bytes <= MAX_TOTAL_BYTES
                if (fits) {
                    used[meta.account] = current + meta.bytes
                } else {
                    deleteFiles(context, meta)
                }
                fits
            }
            runCatching { PrefHelper.putAny(INDEX_KEY, json.encodeToString(survivors)) }
        }
    }

    private fun readIndex(): List<RepoPackMeta> = runCatching {
        val raw = PrefHelper.getString(INDEX_KEY) ?: return emptyList()
        json.decodeFromString<List<RepoPackMeta>>(raw)
    }.getOrDefault(emptyList())
}
