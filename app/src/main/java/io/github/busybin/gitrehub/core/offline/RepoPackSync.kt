package io.github.busybin.gitrehub.core.offline

import android.content.Context
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.core.model.GitTreeEntry
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Which files of a tree belong in a pack.
 *
 * Pure functions so the rules (skip binaries, skip huge files, skip `.git`) are unit-testable
 * without a network or a device.
 */
object RepoPackRules {

    /** Single-file ceiling: a 30 MB minified bundle would blow the per-repo quota in one file. */
    const val MAX_FILE_BYTES = 2L * 1024 * 1024

    private val BINARY = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tif", "tiff", "psd", "ai",
        "pdf", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "apk", "aar", "ipa",
        "so", "dll", "exe", "dylib", "class", "o", "a", "obj", "wasm",
        "ttf", "otf", "woff", "woff2", "eot",
        "mp3", "mp4", "m4a", "wav", "flac", "ogg", "webm", "mkv", "avi", "mov",
        "blend", "fbx", "glb", "gltf", "stl", "obj", "dae",
        "db", "sqlite", "bin", "dat", "iso", "img",
        "jks", "keystore", "p12", "pfx",
    )

    fun isTextPath(path: String, size: Long): Boolean {
        if (size <= 0 || size > MAX_FILE_BYTES) return false
        val clean = path.trim('/')
        if (clean.isBlank() || clean.startsWith(".git/") || "/node_modules/" in "/$clean/") return false
        val ext = clean.substringAfterLast('.', "").lowercase()
        return ext !in BINARY
    }

    /** Blobs that belong in the pack, with the total their sizes add up to. */
    fun textFiles(tree: GitTree): Pair<List<GitTreeEntry>, Long> {
        val files = tree.tree.filter {
            it.type == "blob" && isTextPath(it.path, it.size) && !it.sha.isNullOrBlank()
        }
        return files to files.sumOf { it.size }
    }
}

/**
 * Downloads and refreshes whole-repository packs.
 *
 * One sync at a time, on a process-wide scope: the user may leave the repository screen while the
 * download continues. Everything is sequential on purpose — a burst of blob requests is exactly
 * what triggers GitHub's secondary rate limit.
 */
object RepoPackSyncer {

    data class Progress(
        val fullName: String,
        val step: String,
        val done: Int = 0,
        val total: Int = 0,
        val bytes: Long = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** Size of the text files of [branch], used by the "include source code" switch. */
    suspend fun estimate(owner: String, repo: String, branch: String?): Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            val tree = GitHubApi.gitTree(owner, repo, branch)
            val (files, bytes) = RepoPackRules.textFiles(tree)
            files.size to bytes
        }

    /**
     * Starts a sync in the background. Returns false when another sync is already running; packs
     * are small enough that queueing would only confuse the progress UI.
     */
    fun start(
        context: Context,
        owner: String,
        repo: String,
        includeCode: Boolean,
    ): Boolean {
        if (_progress.value != null) return false
        val app = context.applicationContext
        scope.launch {
            mutex.withLock { runSync(app, owner, repo, includeCode) }
        }
        return true
    }

    private suspend fun runSync(context: Context, owner: String, repo: String, includeCode: Boolean) {
        try {
            sync(context, owner, repo, includeCode)
        } catch (_: Throwable) {
            // A failed pack must not leave the UI stuck on "syncing"; the old pack stays as it was.
        } finally {
            _progress.value = null
        }
    }

    /** True when the default branch moved since the pack was taken. */
    suspend fun isStale(meta: RepoPackMeta): Boolean = withContext(Dispatchers.IO) {
        val branch = meta.branch.ifBlank { "main" }
        val head = runCatching { GitHubApi.branchHeadSha(meta.owner, meta.repo, branch) }.getOrNull()
            ?: return@withContext false
        head != meta.headSha
    }

    /** Refreshes every pack of the current account whose branch moved (at most [limit] per pass). */
    suspend fun syncStale(context: Context, account: String, limit: Int = 5) = withContext(Dispatchers.IO) {
        if (_progress.value != null) return@withContext
        mutex.withLock {
            RepoPacks.index(account).take(limit).forEach { meta ->
                if (runCatching { isStale(meta) }.getOrDefault(false)) {
                    runSync(context, meta.owner, meta.repo, meta.includeCode)
                }
            }
        }
    }

    private suspend fun sync(context: Context, owner: String, repo: String, includeCode: Boolean) {
        val account = Session.currentAccount()?.login ?: return
        _progress.value = Progress("$owner/$repo", "meta")
        val detail = GitHubApi.repo(owner, repo)
        val branch = detail.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
        val head = runCatching { GitHubApi.branchHeadSha(owner, repo, branch) }.getOrNull()
            ?: GitHubApi.gitTree(owner, repo, branch).sha
        val readme = runCatching { GitHubApi.readmeHtml(owner, repo) }.getOrNull()
        val releaseCount = runCatching { GitHubApi.releases(owner, repo, 1).value.size }.getOrDefault(0)

        val meta = RepoPackMeta(
            account = account,
            owner = owner,
            repo = repo,
            branch = branch,
            headSha = head,
            syncedAt = System.currentTimeMillis(),
            includeCode = includeCode,
            releaseCount = releaseCount,
        )
        RepoPacks.prepare(context, meta)

        var files = emptyList<RepoPackFile>()
        var bytes = 0L
        var skipped = 0
        if (includeCode) {
            val tree = GitHubApi.gitTree(owner, repo, head)
            val (candidates, _) = RepoPackRules.textFiles(tree)
            val allBlobs = tree.tree.count { it.type == "blob" }
            skipped += allBlobs - candidates.size
            _progress.value = Progress(meta.fullName, "code", 0, candidates.size)
            val packed = ArrayList<RepoPackFile>(candidates.size)
            for ((index, entry) in candidates.withIndex()) {
                val path = entry.path
                val sha = entry.sha ?: continue
                if (bytes + entry.size > RepoPacks.MAX_REPO_BYTES) {
                    skipped += candidates.size - index
                    break
                }
                // Signing out mid-download must not resurrect a pack for the old account.
                if (runCatching { Session.currentAccount()?.login }.getOrNull() != account) break
                val text = runCatching { GitHubApi.blobText(owner, repo, sha) }.getOrNull()
                if (text == null) {
                    skipped++
                } else {
                    RepoPacks.writeText(context, meta, path, text)
                    bytes += text.toByteArray(Charsets.UTF_8).size.toLong()
                    packed += RepoPackFile(path = path, size = entry.size, sha = sha)
                }
                _progress.value = Progress(meta.fullName, "code", index + 1, candidates.size, bytes)
            }
            files = packed
        }

        RepoPacks.finish(
            context = context,
            meta = meta.copy(bytes = bytes, fileCount = files.size, skipped = skipped),
            repo = detail,
            readmeHtml = readme,
            files = files,
        )
    }
}
