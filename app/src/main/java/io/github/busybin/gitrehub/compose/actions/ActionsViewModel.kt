package io.github.busybin.gitrehub.compose.actions

import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.data.Actions
import io.github.busybin.gitrehub.core.model.Workflow
import io.github.busybin.gitrehub.core.model.WorkflowJob
import io.github.busybin.gitrehub.core.model.WorkflowRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Actions runs of one repository, one workflow at a time (or all of them).
 *
 * The workflows list feeds the filter chips; a run's jobs are fetched only when its card is opened,
 * so a repository with hundreds of runs still opens instantly.
 */
class ActionsViewModel : PagedViewModel<WorkflowRun>() {

    private var owner: String = ""
    private var repo: String = ""

    private var workflowId: Long? = null

    private val _workflows = MutableStateFlow<LoadState<List<Workflow>>>(LoadState.Loading)
    val workflows: StateFlow<LoadState<List<Workflow>>> = _workflows.asStateFlow()

    private val _jobs = MutableStateFlow<Map<Long, LoadState<List<WorkflowJob>>>>(emptyMap())
    val jobs: StateFlow<Map<Long, LoadState<List<WorkflowJob>>>> = _jobs.asStateFlow()

    private val requestedJobs = mutableSetOf<Long>()

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        loadWorkflows()
        refresh()
    }

    /** null = every workflow of the repository. */
    fun currentWorkflowId(): Long? = workflowId

    fun selectWorkflow(id: Long?) {
        if (workflowId == id) return
        workflowId = id
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<WorkflowRun> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        return Actions.runs(owner, repo, page, workflowId)
    }

    override suspend fun fetchCached(page: Int): PageResult<WorkflowRun>? =
        runCatching { Actions.cachedRuns(owner, repo, page, workflowId) }.getOrNull()

    private fun loadWorkflows() {
        viewModelScope.launchLoad({ _workflows.value = it }) { Actions.workflows(owner, repo) }
    }

    /** Jobs (and their steps) of one run; N calls at most, kept for the lifetime of the screen. */
    fun loadJobs(runId: Long, force: Boolean = false) {
        if (!force && !requestedJobs.add(runId)) return
        viewModelScope.launchLoad({ state -> _jobs.update { it + (runId to state) } }) {
            Actions.jobs(owner, repo, runId)
        }
    }
}
