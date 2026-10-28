package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.core.model.Workflow
import io.github.busybin.gitrehub.core.model.WorkflowJob
import io.github.busybin.gitrehub.core.model.WorkflowRun
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.GitHubClient
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * GitHub Actions facade: the workflows of a repository, their runs, and the jobs/steps of one run.
 *
 * Run logs have no JSON endpoint (the API hands out a zip through a 302), so per-step status is what
 * this client shows natively; the run page on github.com stays one tap away for the full output.
 */
object Actions {

    suspend fun workflows(owner: String, repo: String): List<Workflow> =
        GitHubApi.workflows(owner, repo)

    suspend fun runs(
        owner: String,
        repo: String,
        page: Int,
        workflowId: Long? = null,
    ): PageResult<WorkflowRun> = page(owner, repo, page, workflowId, cacheOnly = false)

    suspend fun cachedRuns(
        owner: String,
        repo: String,
        page: Int,
        workflowId: Long? = null,
    ): PageResult<WorkflowRun> = page(owner, repo, page, workflowId, cacheOnly = true)

    private suspend fun page(
        owner: String,
        repo: String,
        page: Int,
        workflowId: Long?,
        cacheOnly: Boolean,
    ): PageResult<WorkflowRun> {
        val response = GitHubApi.workflowRuns(owner, repo, page, workflowId, cacheOnly)
        return PageResult(response.value, response.hasMore())
    }

    suspend fun jobs(owner: String, repo: String, runId: Long): List<WorkflowJob> =
        GitHubApi.runJobs(owner, repo, runId)

    suspend fun rerun(owner: String, repo: String, runId: Long) =
        GitHubApi.rerunRun(owner, repo, runId)

    suspend fun cancel(owner: String, repo: String, runId: Long) =
        GitHubApi.cancelRun(owner, repo, runId)

    /** One file inside the run-log zip (`<job>/<n>_<step>.txt`). */
    data class LogFile(val name: String, val text: String)

    /**
     * Downloads and unpacks the run log zip. GitHub caps the archive per run; the reader stops at
     * [MAX_TOTAL_BYTES] so a runaway log cannot take the app down.
     */
    suspend fun logs(owner: String, repo: String, runId: Long): List<LogFile> {
        val bytes = GitHubClient.getBytes(
            "/repos/$owner/$repo/actions/runs/$runId/logs",
            maxBytes = MAX_TOTAL_BYTES,
        )
        val out = mutableListOf<LogFile>()
        var decompressed = 0L
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val (chunk, truncated) = zip.readCapped(MAX_ENTRY_BYTES)
                decompressed += chunk.size
                // One job log lives in memory with the rest, so cap each entry as well: the
                // compressed archive can be small and still expand into hundreds of megabytes.
                val text = chunk.toString(Charsets.UTF_8) + if (truncated) TRUNCATED else ""
                out += LogFile(entry.name, text)
                if (out.size >= MAX_FILES || decompressed >= MAX_DECOMPRESSED_BYTES) break
            }
        }
        return out.sortedBy { it.name }
    }

    private fun java.util.zip.ZipInputStream.readCapped(limit: Int): Pair<ByteArray, Boolean> {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - total))
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            total += read
        }
        // One more byte tells us whether the entry had more decompressed data.
        val truncated = total >= limit && read(chunk, 0, 1) > 0
        return buffer.toByteArray() to truncated
    }

    private const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    // Scale with the heap the device actually gave us so a low-end phone cannot be pushed into
    // an OOM by a large run log.
    private val MAX_DECOMPRESSED_BYTES: Long
        get() = (Runtime.getRuntime().maxMemory() / 8)
            .coerceIn(4L * 1024 * 1024, 32L * 1024 * 1024)
    private const val MAX_ENTRY_BYTES = 1 * 1024 * 1024
    private const val MAX_FILES = 80
    private const val TRUNCATED = "\n… [truncated]"
}
