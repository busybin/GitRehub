package io.github.busybin.gitrehub.core.offline

import android.content.Context
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.core.model.GitTreeEntry
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.provider.tasks.RepoPackSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    /** The files that fit the per-pack quota, plus how many candidates were dropped for it. */
    data class Selection(val files: List<GitTreeEntry>, val skipped: Int)

    /**
     * Files whose blob did not move since the published pack: same path, same SHA. The incremental
     * sync copies these out of the old pack instead of downloading them again.
     */
    fun unchanged(files: List<RepoPackFile>, oldSha: Map<String, String>): List<RepoPackFile> =
        files.filter { file ->
            file.sha.isNotBlank() && oldSha[file.path].equals(file.sha, ignoreCase = true)
        }

    /**
     * Trims [candidates] to [maxBytes] **before** anything is downloaded, so the archive path and
     * the blob fallback both stop at the same set of files.
     *
     * A file that does not fit is skipped, not used as a cut-off: GitHub sorts the tree by path, so
     * one large file near the top would otherwise leave the pack nearly empty.
     */
    fun select(candidates: List<GitTreeEntry>, maxBytes: Long): Selection {
        var used = 0L
        val chosen = ArrayList<GitTreeEntry>(candidates.size)
        for (entry in candidates) {
            if (used + entry.size > maxBytes) continue
            chosen += entry
            used += entry.size
        }
        return Selection(chosen, candidates.size - chosen.size)
    }
}

/** What the dialog asks the user, and what a sync then does. */
data class RepoPackOptions(
    val includeSmall: Boolean = true,
    val includeLarge: Boolean = false,
    val includeIssues: Boolean = false,
    val autoSync: Boolean = true,
)

/** One queued pack download. */
data class RepoPackTask(
    val owner: String,
    val repo: String,
    val options: RepoPackOptions,
    /** True when the task was queued by the branch-moved watcher rather than by a tap. */
    val auto: Boolean = false,
) {
    val fullName: String get() = "$owner/$repo"
}

/**
 * FIFO queue of pack tasks.
 *
 * A repository that is already queued is replaced in place instead of stacked, so tapping "save"
 * twice (or tapping it while the branch watcher has already queued the same pack) cannot double the
 * work. Pure Kotlin so the ordering rules are unit-tested.
 */
class RepoPackTaskQueue {
    private val items = ArrayList<RepoPackTask>()

    fun enqueue(task: RepoPackTask) {
        val index = items.indexOfFirst { it.fullName == task.fullName }
        if (index < 0) {
            items += task
            return
        }
        val existing = items[index]
        // A watcher refresh must never downgrade the options the user explicitly chose: keep a
        // manual task, and if only an automatic one is queued replace it with the manual request.
        if (task.auto && !existing.auto) return
        items[index] = if (existing.auto && !task.auto) task.copy(auto = false) else task
    }

    fun snapshot(): List<RepoPackTask> = items.toList()

    fun poll(): RepoPackTask? = items.removeFirstOrNull()

    /** Drops waiting automatic refreshes; manual taps stay queued. */
    fun removeAuto(): Boolean {
        val before = items.size
        items.removeAll { it.auto }
        return items.size != before
    }

    fun clear() = items.clear()
}

/**
 * Downloads and refreshes whole-repository packs.
 *
 * One worker drains the queue. Each task downloads into a staging directory and replaces the
 * previous pack only after every file arrived, so cancelling, failing or switching accounts never
 * destroys a pack that already worked. [RepoPackSyncService] keeps the progress in the notification
 * shade while the app is elsewhere.
 */
object RepoPackSyncer {

    enum class Phase { META, ARCHIVE, CODE, ISSUES }

    data class Progress(
        val fullName: String,
        val phase: Phase,
        /** 0 while the total is still unknown (indeterminate bar). */
        val done: Int = 0,
        val total: Int = 0,
        val bytes: Long = 0L,
    )

    data class Outcome(
        val fullName: String,
        val saved: Boolean,
        val cancelled: Boolean = false,
    )

    data class State(
        val running: RepoPackTask? = null,
        val queued: List<RepoPackTask> = emptyList(),
        val progress: Progress? = null,
        val outcome: Outcome? = null,
    ) {
        val hasWork: Boolean get() = running != null || queued.isNotEmpty()

        /** Name shown by interruption prompts, including the brief gap between two tasks. */
        val activeName: String? get() = running?.fullName ?: queued.firstOrNull()?.fullName
    }

    data class Estimate(
        val smallCount: Int = 0,
        val smallBytes: Long = 0,
        val largeCount: Int = 0,
        val largeBytes: Long = 0,
        val issueCount: Int = 0,
    )

    enum class Enqueue { STARTED, QUEUED, ALREADY_RUNNING }

    /** Cap on packed issues: a repository with 5k issues would otherwise own the whole quota. */
    const val MAX_ISSUES = 1000

    /** Above this an archive costs more traffic than the per-file fallback it replaces. */
    private const val MAX_ARCHIVE_BYTES = 150L * 1024 * 1024

    /**
     * Tiny packs use blobs directly: a handful of requests is cheap, and it avoids pulling a big
     * archive full of binaries just to find ten text files.
     */
    private const val MIN_FILES_FOR_ARCHIVE = 8

    /**
     * Parallel blob downloads for the fallback path. OkHttp's shared client allows five requests
     * per host, so six workers would only queue; five is also well below GitHub's secondary limit.
     */
    private const val BLOB_CONCURRENCY = 5

    /**
     * Incremental refresh: copying a file from the old pack is much cheaper than one blob request,
     * so above this many moved files the zipball is the better deal (one request for everything).
     */
    private const val INCREMENTAL_MAX_FILES = 150

    /** Same idea in bytes: past this the archive is likely to cost less than hundreds of blobs. */
    private const val INCREMENTAL_MAX_BYTES = 64L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = RepoPackTaskQueue()
    private val lock = Any()
    private var worker: Job? = null

    /** Kept for [cancelAuto], which may have to restart the worker for the manual queue. */
    private var appContext: Context? = null

    /**
     * Bumped by [cancelAll]. A drained worker whose generation is stale stops touching [State], so
     * a cancelled run cannot clear the progress of the run that replaced it.
     */
    private var generation = 0

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Sizes for the dialog's switches; the tree and the issue count are fetched in parallel. */
    suspend fun estimate(owner: String, repo: String, branch: String?): Estimate =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val treeDeferred = async { GitHubApi.gitTree(owner, repo, branch) }
                val issuesDeferred = async { issueCount(owner, repo) }
                val tree = treeDeferred.await()
                val result = RepoPackRules.textFiles(tree, includeSmall = true, includeLarge = true)
                val small = result.first.filter { !RepoPackRules.isLarge(it.size) }
                val large = result.first.filter { RepoPackRules.isLarge(it.size) }
                Estimate(
                    smallCount = small.size,
                    smallBytes = result.second,
                    largeCount = large.size,
                    largeBytes = result.third,
                    issueCount = issuesDeferred.await(),
                )
            }
        }

    /**
     * Adds a pack to the queue and makes sure the foreground service (and the worker) is up.
     *
     * Re-saving the pack that is already running is rejected; re-saving one that is only queued
     * replaces its options and keeps its place in line.
     */
    fun enqueue(
        context: Context,
        owner: String,
        repo: String,
        options: RepoPackOptions,
        auto: Boolean = false,
    ): Enqueue {
        val app = context.applicationContext
        val task = RepoPackTask(owner, repo, options, auto)
        val result: Enqueue
        synchronized(lock) {
            appContext = app
            val running = _state.value.running
            if (running?.fullName == task.fullName) {
                // A manual tap may supersede an automatic refresh of the same repository, but a
                // watcher refresh must never replace what the user explicitly asked for.
                if (!running.auto || auto) return Enqueue.ALREADY_RUNNING
                generation += 1
                val previous = worker
                previous?.cancel()
                _state.update { it.copy(running = null, progress = null) }
                queue.enqueue(task)
                _state.update { it.copy(queued = queue.snapshot(), outcome = null) }
                result = if (queue.snapshot().size > 1) Enqueue.QUEUED else Enqueue.STARTED
                worker = scope.launch {
                    previous?.join()
                    drain(app)
                }
            } else {
                queue.enqueue(task)
                _state.update { it.copy(queued = queue.snapshot(), outcome = null) }
                result = if (worker?.isActive == true) Enqueue.QUEUED else Enqueue.STARTED
                if (worker?.isActive != true) {
                    // A cancelled worker may still be deleting its staging tree. Wait for it before
                    // the next drain starts, or the new task's staging could be the one deleted.
                    val previous = worker
                    worker = scope.launch {
                        previous?.join()
                        drain(app)
                    }
                }
            }
        }
        runCatching { RepoPackSyncService.start(app) }
        return result
    }

    /** Drops the running task and everything queued. [silent] skips the "cancelled" toast. */
    fun cancelAll(silent: Boolean = false) {
        synchronized(lock) { cancelLocked(silent) }
    }

    /** Caller holds [lock]; returns the worker that was cancelled. */
    private fun cancelLocked(silent: Boolean): Job? {
        val active = _state.value.running ?: queue.snapshot().firstOrNull()
        generation += 1
        queue.clear()
        val cancelled = worker
        cancelled?.cancel()
        _state.value = State(
            outcome = if (silent || active == null) {
                null
            } else {
                Outcome(active.fullName, saved = false, cancelled = true)
            },
        )
        return cancelled
    }

    /**
     * Cancels everything and waits until the worker has really stopped.
     *
     * Account transitions must not clear a pack directory while a cancelled worker is still
     * extracting into its staging tree: the worker would recreate it after the wipe. Waiting also
     * lets [RepoPacks.abort] finish before the next account is authorized.
     */
    suspend fun cancelAndJoin(silent: Boolean = false) {
        val cancelled = synchronized(lock) { cancelLocked(silent) }
        cancelled?.join()
    }

    /**
     * Stops automatic refreshes only: waiting auto tasks are dropped and a running auto task is
     * cancelled. Manual tasks keep their place and are drained by a fresh worker.
     *
     * The old worker is joined before the replacement starts, so its staging cleanup cannot race
     * with the next task's staging directory.
     */
    fun cancelAuto() {
        synchronized(lock) {
            queue.removeAuto()
            _state.update { it.copy(queued = queue.snapshot()) }
            val runningAuto = _state.value.running?.auto == true
            if (!runningAuto) return
            generation += 1
            val previous = worker
            previous?.cancel()
            _state.update { it.copy(running = null, progress = null) }
            val app = appContext
            if (app != null && queue.snapshot().isNotEmpty()) {
                worker = scope.launch {
                    previous?.join()
                    drain(app)
                }
            }
        }
    }

    fun consumeOutcome() {
        _state.update { it.copy(outcome = null) }
    }

    /** True when [fullName] is running or waiting. */
    fun isKnown(fullName: String): Boolean = synchronized(lock) {
        _state.value.running?.fullName == fullName || _state.value.queued.any { it.fullName == fullName }
    }

    /** True when the default branch moved since the pack was taken. */
    suspend fun isStale(meta: RepoPackMeta): Boolean = withContext(Dispatchers.IO) {
        val branch = meta.branch.ifBlank { "main" }
        val head = runCatching { GitHubApi.branchHeadSha(meta.owner, meta.repo, branch) }.getOrNull()
            ?: return@withContext false
        head != meta.headSha
    }

    /**
     * Queues packs that opted into auto-sync and whose branch moved (at most [limit] a pass). They
     * go through the same queue as manual saves, so a manual tap never fails just because the
     * watcher got there first.
     */
    suspend fun enqueueStale(context: Context, account: String, limit: Int = 5) =
        withContext(Dispatchers.IO) {
            if (!RepoPackPrefs.isEnabled()) return@withContext
            RepoPacks.index(account).filter { it.autoSync }.take(limit).forEach { meta ->
                if (isKnown(meta.fullName)) return@forEach
                if (runCatching { isStale(meta) }.getOrDefault(false)) {
                    enqueue(context, meta.owner, meta.repo, meta.options(), auto = true)
                }
            }
        }

    private suspend fun drain(context: Context) {
        val startedAt = synchronized(lock) { generation }
        while (true) {
            val task = synchronized(lock) {
                if (generation != startedAt) return
                val next = queue.poll()
                if (next != null) _state.update { it.copy(running = next, queued = queue.snapshot()) }
                next
            } ?: break
            var outcome: Outcome? = null
            try {
                runTask(context, task)
                outcome = Outcome(task.fullName, saved = true)
            } catch (cancelled: CancellationException) {
                // cancelAll() already published the outcome; just let the coroutine die.
                throw cancelled
            } catch (_: Throwable) {
                outcome = Outcome(task.fullName, saved = false)
            } finally {
                synchronized(lock) {
                    if (generation == startedAt) {
                        _state.update { it.copy(running = null, progress = null) }
                    }
                }
            }
            synchronized(lock) {
                if (generation == startedAt) _state.update { it.copy(outcome = outcome) }
            }
        }
    }

    private suspend fun runTask(context: Context, task: RepoPackTask) {
        val account = Session.currentAccount()?.login
            ?: throw IllegalStateException("No account")
        val owner = task.owner
        val repo = task.repo
        val options = task.options
        report(task, Phase.META, 0, 0, 0)

        val detail = GitHubApi.repo(owner, repo)
        val branch = detail.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
        // Three independent requests: head, README and release count.
        val (head, readme, releaseCount) = coroutineScope {
            val headDeferred = async {
                runCatching { GitHubApi.branchHeadSha(owner, repo, branch) }.getOrNull()
                    ?: GitHubApi.gitTree(owner, repo, branch).sha
            }
            val readmeDeferred = async {
                runCatching { GitHubApi.readmeHtml(owner, repo) }.getOrNull()
            }
            val releasesDeferred = async {
                runCatching { GitHubApi.releases(owner, repo, 1).value.size }.getOrDefault(0)
            }
            Triple(headDeferred.await(), readmeDeferred.await(), releasesDeferred.await())
        }

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

        var published = false
        try {
            var files = emptyList<RepoPackFile>()
            var bytes = 0L
            var skipped = 0
            if (options.includeSmall || options.includeLarge) {
                val tree = GitHubApi.gitTree(owner, repo, head)
                val (candidates, _, _) = RepoPackRules.textFiles(
                    tree, options.includeSmall, options.includeLarge,
                )
                skipped += tree.tree.count { it.type == "blob" } - candidates.size
                val selection = RepoPackRules.select(candidates, RepoPacks.MAX_REPO_BYTES)
                skipped += selection.skipped
                val planned = selection.files.map { entry ->
                    RepoPackFile(path = entry.path, size = entry.size, sha = entry.sha.orEmpty())
                }
                report(task, Phase.CODE, 0, planned.size, 0)

                // A published pack of the same branch/options can keep every file whose blob did
                // not move. A one-line commit then costs one tree request plus one blob instead of
                // a fresh archive of the whole repository.
                val previous = RepoPacks.get(account, owner, repo)
                val incremental = previous?.let { old ->
                    tryIncremental(context, meta, task, account, owner, repo, old, planned, skipped)
                }
                if (incremental != null) {
                    files = incremental.files
                    bytes = incremental.bytes
                    skipped = incremental.skipped
                } else {
                    // The incremental attempt may have copied part of the tree before giving up.
                    RepoPacks.prepare(context, meta)
                    // One archive request beats hundreds of blob calls. When it is not usable
                    // (blocked codeload, oversized archive, broken zip) fall back to the old path.
                    val fromArchive = if (planned.size >= MIN_FILES_FOR_ARCHIVE) try {
                        extractArchive(context, meta, task, owner, repo, head, planned)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    } else {
                        null
                    }
                    if (fromArchive != null) {
                        files = fromArchive.files
                        bytes = fromArchive.bytes
                        skipped += fromArchive.skipped
                    } else {
                        // The archive may have written half of the staging tree before failing.
                        RepoPacks.prepare(context, meta)
                        val fetched = fetchBlobs(context, meta, task, account, owner, repo, planned)
                        files = fetched.first
                        bytes = fetched.second
                    }
                }
            }

            requireSameAccount(account)
            var issues: List<Issue> = emptyList()
            if (options.includeIssues) {
                issues = fetchIssues(task, account, owner, repo, bytes)
            }
            requireSameAccount(account)
            // A cancel that arrived while the last file was being written must not publish.
            currentCoroutineContext().ensureActive()

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
            published = true
        } finally {
            if (!published) RepoPacks.abort(context, meta)
        }
    }

    /** Result of a successful incremental refresh. */
    private data class Incremental(
        val files: List<RepoPackFile>,
        val bytes: Long,
        val skipped: Int,
    )

    /**
     * Refreshes [previous] in place: files whose blob SHA did not move are copied out of the old
     * pack, only the moved ones are downloaded. Returns null when the diff is not worth it (no
     * published pack, another branch/option set, or too many changed files) and the caller falls
     * back to the archive path.
     *
     * Blob SHAs are exact content hashes, so this needs no `compare` call: the branch tree already
     * says which files are new. It also survives a force-push, where `compare` would be truncated.
     */
    private suspend fun tryIncremental(
        context: Context,
        meta: RepoPackMeta,
        task: RepoPackTask,
        account: String,
        owner: String,
        repo: String,
        previous: RepoPackMeta,
        planned: List<RepoPackFile>,
        skipped: Int,
    ): Incremental? {
        if (previous.headSha.isBlank()) return null
        if (previous.branch != meta.branch) return null
        if (previous.includeSmall != meta.includeSmall) return null
        if (previous.includeLarge != meta.includeLarge) return null
        if (!RepoPacks.dir(context, previous).isDirectory) return null
        val oldSha = RepoPacks.tree(context, previous).associate { it.path to it.sha }
        if (oldSha.isEmpty()) return null

        val reusable = RepoPackRules.unchanged(planned, oldSha)
            .filter { RepoPacks.hasFile(context, previous, it.path) }
        val reusablePaths = reusable.mapTo(HashSet()) { it.path }
        val changed = planned.filterNot { it.path in reusablePaths }
        if (changed.size > INCREMENTAL_MAX_FILES) return null
        if (changed.sumOf { it.size } > INCREMENTAL_MAX_BYTES) return null

        val done = AtomicInteger()
        val bytes = AtomicLong()
        report(task, Phase.CODE, 0, planned.size, 0)
        reusable.forEach { file ->
            currentCoroutineContext().ensureActive()
            requireSameAccount(account)
            // A half-deleted old pack must degrade to a full download, not fail the whole sync.
            val data = RepoPacks.bytes(context, previous, file.path) ?: return null
            RepoPacks.writeBytes(context, meta, file.path, data)
            bytes.addAndGet(data.size.toLong())
            report(task, Phase.CODE, done.incrementAndGet(), planned.size, bytes.get())
        }
        val (fetched, fetchedBytes) = fetchBlobs(context, meta, task, account, owner, repo, changed)
        bytes.addAndGet(fetchedBytes)
        return Incremental(reusable + fetched, bytes.get(), skipped)
    }

    /**
     * Downloads the zipball of [ref] into the cache directory and publishes the planned files into
     * the staging tree.
     *
     * Returns `null` (never throws) when the archive is unavailable or unreadable, so the caller can
     * silently degrade to the parallel blob path. A cancellation is rethrown.
     */
    private suspend fun extractArchive(
        context: Context,
        meta: RepoPackMeta,
        task: RepoPackTask,
        owner: String,
        repo: String,
        ref: String,
        planned: List<RepoPackFile>,
    ): RepoPackZip.Result? {
        val archive = File(context.cacheDir, "pack-${System.nanoTime()}.zip")
        return try {
            report(task, Phase.ARCHIVE, 0, 0, 0)
            var reportedBytes = 0L
            GitHubApi.downloadZipball(owner, repo, ref, archive, MAX_ARCHIVE_BYTES) { downloaded, _ ->
                if (downloaded - reportedBytes >= 256 * 1024) {
                    reportedBytes = downloaded
                    report(task, Phase.ARCHIVE, 0, 0, downloaded)
                }
            }
            report(task, Phase.CODE, 0, planned.size, 0)
            val job = currentCoroutineContext()[Job]
            RepoPackZip.extract(
                archive = archive,
                candidates = planned.associateBy { it.path },
                maxBytes = RepoPacks.MAX_REPO_BYTES,
                write = { path, data -> RepoPacks.writeBytes(context, meta, path, data) },
                onProgress = { done, total, bytes -> report(task, Phase.CODE, done, total, bytes) },
                shouldContinue = { job?.isActive != false },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            runCatching { archive.delete() }
        }
    }

    /**
     * Fallback: one request per file, [BLOB_CONCURRENCY] at a time.
     *
     * Every worker checks the account before it touches the disk, so switching accounts halfway
     * through still aborts the whole pack instead of publishing it for the old account.
     */
    private suspend fun fetchBlobs(
        context: Context,
        meta: RepoPackMeta,
        task: RepoPackTask,
        account: String,
        owner: String,
        repo: String,
        planned: List<RepoPackFile>,
    ): Pair<List<RepoPackFile>, Long> = coroutineScope {
        if (planned.isEmpty()) return@coroutineScope emptyList<RepoPackFile>() to 0L
        val permits = Semaphore(BLOB_CONCURRENCY)
        val done = AtomicInteger()
        val bytes = AtomicLong()
        val packed = Collections.synchronizedList(ArrayList<RepoPackFile>(planned.size))
        planned.map { file ->
            async(Dispatchers.IO) {
                permits.withPermit {
                    currentCoroutineContext().ensureActive()
                    requireSameAccount(account)
                    val text = fetchBlob(owner, repo, file.sha)
                    if (text.isEmpty()) throw IOException("Empty blob for ${file.path}")
                    RepoPacks.writeText(context, meta, file.path, text)
                    val size = text.toByteArray(Charsets.UTF_8).size.toLong()
                    bytes.addAndGet(size)
                    packed += RepoPackFile(file.path, size, file.sha)
                    report(task, Phase.CODE, done.incrementAndGet(), planned.size, bytes.get())
                }
            }
        }.awaitAll()
        packed.sortedBy { it.path } to bytes.get()
    }

    /** Pages through `state=all` (issues and PRs together), capped by [MAX_ISSUES]. */
    private suspend fun fetchIssues(
        task: RepoPackTask,
        account: String,
        owner: String,
        repo: String,
        bytes: Long,
    ): List<Issue> {
        val collected = ArrayList<Issue>()
        val expected = issueCount(owner, repo).coerceAtMost(MAX_ISSUES)
        var page = 1
        while (collected.size < MAX_ISSUES) {
            currentCoroutineContext().ensureActive()
            requireSameAccount(account)
            report(task, Phase.ISSUES, collected.size, expected, bytes)
            val response = GitHubApi.repoIssues(
                owner = owner,
                repo = repo,
                state = "all",
                sort = "updated",
                page = page,
            )
            if (response.value.isEmpty()) break
            collected += response.value
            if (response.links.next == null) break
            page += 1
        }
        return collected.take(MAX_ISSUES)
    }

    /** One retry: GitHub occasionally drops a connection during a long run. */
    private suspend fun fetchBlob(owner: String, repo: String, sha: String): String {
        var last: Throwable? = null
        repeat(2) {
            try {
                return GitHubApi.blobText(owner, repo, sha)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                last = error
            }
        }
        throw last ?: IOException("Blob download failed")
    }

    private suspend fun requireSameAccount(account: String) {
        if (Session.currentAccount()?.login != account) throw AccountChangedException()
    }

    private fun report(task: RepoPackTask, phase: Phase, done: Int, total: Int, bytes: Long) {
        _state.update { state ->
            // A worker cancelled between two suspending calls can still report once; never let it
            // overwrite the progress of the task that replaced it.
            if (state.running?.fullName != task.fullName) {
                state
            } else {
                state.copy(progress = Progress(task.fullName, phase, done, total, bytes))
            }
        }
    }

    /** Total issue + PR count; `per_page=1` keeps the payload tiny, the total is the last page. */
    private suspend fun issueCount(owner: String, repo: String): Int = runCatching {
        val page = GitHubApi.issuesSince(owner, repo, "1970-01-01T00:00:00Z")
        page.links.last ?: page.value.size
    }.getOrDefault(0)

    private fun RepoPackMeta.options() = RepoPackOptions(
        includeSmall = includeSmall,
        includeLarge = includeLarge,
        includeIssues = includeIssues,
        autoSync = autoSync,
    )

    private class AccountChangedException : Exception()
}
