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
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val text = zip.readBytes().toString(Charsets.UTF_8)
                out += LogFile(entry.name, text)
                if (out.size >= MAX_FILES) break
            }
        }
        return out.sortedBy { it.name }
    }

    private const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    private const val MAX_FILES = 80
}
