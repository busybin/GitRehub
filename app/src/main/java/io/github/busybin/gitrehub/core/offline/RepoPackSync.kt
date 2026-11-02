package io.github.busybin.gitrehub.core.offline

import android.content.Context
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.core.model.GitTreeEntry
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.provider.tasks.RepoPackSyncService
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

    /** Below this a text file counts as "small" (the switch users leave on by default). */
    const val SMALL_MAX_BYTES = 2L * 1024 * 1024

    /** Absolute ceiling: anything bigger is never packed, even with "large files" on. */
    const val LARGE_MAX_BYTES = 20L * 1024 * 1024

    private val BINARY = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tif", "tiff", "psd", "ai",
        "pdf", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "apk", "aar", "ipa",
        "so", "dll", "exe", "dylib", "class", "o", "a", "obj", "wasm",
        "ttf", "otf", "woff", "woff2", "eot",
        "mp3", "mp4", "m4a", "wav", "flac", "ogg", "webm", "mkv", "avi", "mov",
        "blend", "fbx", "glb", "gltf", "stl", "dae",
        "db", "sqlite", "bin", "dat", "iso", "img",
        "jks", "keystore", "p12", "pfx",
    )

    fun isTextPath(path: String, size: Long): Boolean {
        if (size <= 0 || size > LARGE_MAX_BYTES) return false
        val clean = path.trim('/')
        if (clean.isBlank() || clean.startsWith(".git/") || "/node_modules/" in "/$clean/") return false
        val ext = clean.substringAfterLast('.', "").lowercase()
        return ext !in BINARY
    }

    fun isLarge(size: Long): Boolean = size > SMALL_MAX_BYTES

    /**
     * Blobs in the pack given the two switches. Small and large are independent on purpose: a
     * repository with a few giant generated files should still be savable without them.
     */
    fun textFiles(
        tree: GitTree,
        includeSmall: Boolean,
        includeLarge: Boolean,
    ): Triple<List<GitTreeEntry>, Long, Long> {
        val candidates = tree.tree.filter {
            it.type == "blob" && isTextPath(it.path, it.size) && !it.sha.isNullOrBlank()
        }
        val small = candidates.filter { !isLarge(it.size) }
        val large = candidates.filter { isLarge(it.size) }
        val chosen = small.takeIf { includeSmall }.orEmpty() + large.takeIf { includeLarge }.orEmpty()
        return Triple(chosen, small.sumOf { it.size }, large.sumOf { it.size })
    }
}

/** What the dialog asks the user, and what a sync then does. */
data class RepoPackOptions(
    val includeSmall: Boolean = true,
    val includeLarge: Boolean = false,
    val includeIssues: Boolean = false,
    val autoSync: Boolean = true,
)

/**
 * Downloads and refreshes whole-repository packs.
 *
 * One sync at a time, on a process-wide scope, with a foreground service behind it so the progress
 * notification survives leaving the app.
 */
object RepoPackSyncer {

    data class Progress(
        val fullName: String,
        val step: String,
        /** 0 while the size is still unknown (indeterminate bar). */
        val done: Int = 0,
        val total: Int = 0,
        val bytes: Long = 0L,
    )

    data class Estimate(
        val smallCount: Int = 0,
        val smallBytes: Long = 0,
        val largeCount: Int = 0,
        val largeBytes: Long = 0,
        val issueCount: Int = 0,
    )

    /** Cap on packed issues: a repository with 5k issues would otherwise own the whole quota. */
    const val MAX_ISSUES = 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** Sizes for the dialog's three switches; one tree request and one issue-count request. */
    suspend fun estimate(owner: String, repo: String, branch: String?): Estimate =
        withContext(Dispatchers.IO) {
            val tree = GitHubApi.gitTree(owner, repo, branch)
            val result = RepoPackRules.textFiles(tree, includeSmall = true, includeLarge = true)
            val small = result.first.filter { !RepoPackRules.isLarge(it.size) }
            val large = result.first.filter { RepoPackRules.isLarge(it.size) }
            Estimate(
                smallCount = small.size,
                smallBytes = result.second,
                largeCount = large.size,
                largeBytes = result.third,
                issueCount = issueCount(owner, repo),
            )
        }

    /** Total issue + PR count; `per_page=1` keeps the payload tiny, the total is the last page. */
    private suspend fun issueCount(owner: String, repo: String): Int = runCatching {
        val page = GitHubApi.issuesSince(owner, repo, "1970-01-01T00:00:00Z")
        page.links.last ?: page.value.size
    }.getOrDefault(0)

    /** Starts a sync (foreground service + notification). False when one is already running. */
    fun start(context: Context, owner: String, repo: String, options: RepoPackOptions): Boolean {
        if (_progress.value != null) return false
        val app = context.applicationContext
        RepoPackSyncService.start(app, owner, repo, options)
        return true
    }

    /** Runs the sync on the caller's coroutine; used by [RepoPackSyncService]. */
    suspend fun syncNow(
        context: Context,
        owner: String,
        repo: String,
        options: RepoPackOptions,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock { runSync(context, owner, repo, options) }
    }

    /** True when the default branch moved since the pack was taken. */
    suspend fun isStale(meta: RepoPackMeta): Boolean = withContext(Dispatchers.IO) {
        val branch = meta.branch.ifBlank { "main" }
        val head = runCatching { GitHubApi.branchHeadSha(meta.owner, meta.repo, branch) }.getOrNull()
            ?: return@withContext false
        head != meta.headSha
    }

    /** Refreshes packs that opted into auto-sync and whose branch moved (at most [limit] a pass). */
    suspend fun syncStale(context: Context, account: String, limit: Int = 5) = withContext(Dispatchers.IO) {
        if (_progress.value != null) return@withContext
        RepoPacks.index(account).filter { it.autoSync }.take(limit).forEach { meta ->
            if (runCatching { isStale(meta) }.getOrDefault(false)) {
                val options = RepoPackOptions(
                    includeSmall = meta.includeSmall,
                    includeLarge = meta.includeLarge,
                    includeIssues = meta.includeIssues,
                    autoSync = true,
                )
                mutex.withLock { runSync(context, meta.owner, meta.repo, options) }
            }
        }
    }

    private suspend fun runSync(
        context: Context,
        owner: String,
        repo: String,
        options: RepoPackOptions,
    ) {
        try {
            sync(context, owner, repo, options)
        } catch (_: Throwable) {
            // A failed pack must not leave the UI stuck on "syncing"; the old pack stays as it was.
        } finally {
            _progress.value = null
        }
    }

    private suspend fun sync(
        context: Context,
        owner: String,
        repo: String,
        options: RepoPackOptions,
    ) {
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
            includeSmall = options.includeSmall,
            includeLarge = options.includeLarge,
            includeIssues = options.includeIssues,
            autoSync = options.autoSync,
            releaseCount = releaseCount,
        )
        RepoPacks.prepare(context, meta)

        var files = emptyList<RepoPackFile>()
        var bytes = 0L
        var skipped = 0
        if (options.includeSmall || options.includeLarge) {
            val tree = GitHubApi.gitTree(owner, repo, head)
            val (candidates, _, _) = RepoPackRules.textFiles(
                tree, options.includeSmall, options.includeLarge,
            )
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

        var issues: List<Issue> = emptyList()
        if (options.includeIssues) {
            issues = fetchIssues(context, owner, repo, account, meta.fullName)
        }

        RepoPacks.finish(
            context = context,
            meta = meta.copy(
                bytes = bytes,
                fileCount = files.size,
                skipped = skipped,
                issueCount = issues.size,
            ),
            repo = detail,
            readmeHtml = readme,
            files = files,
            issues = issues,
        )
    }

    /** Pages through `state=all` (issues and PRs together), capped by [MAX_ISSUES]. */
    private suspend fun fetchIssues(
        context: Context,
        owner: String,
        repo: String,
        account: String,
        fullName: String,
    ): List<Issue> {
        val collected = ArrayList<Issue>()
        val expected = issueCount(owner, repo).coerceAtMost(MAX_ISSUES)
        var page = 1
        while (collected.size < MAX_ISSUES) {
            _progress.value = Progress(fullName, "issues", collected.size, expected)
            val response = runCatching {
                GitHubApi.repoIssues(
                    owner = owner,
                    repo = repo,
                    state = "all",
                    sort = "updated",
                    page = page,
                )
            }.getOrNull() ?: break
            if (response.value.isEmpty()) break
            collected += response.value
            if (response.links.next == null) break
            if (runCatching { Session.currentAccount()?.login }.getOrNull() != account) break
            page += 1
        }
        return collected.take(MAX_ISSUES)
    }
}
