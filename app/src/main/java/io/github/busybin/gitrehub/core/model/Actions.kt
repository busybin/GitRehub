package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** A workflow defined in the repository (a `workflows` file under `.github`). */
@Serializable
data class Workflow(
    val id: Long = 0,
    val name: String? = null,
    val path: String? = null,
    val state: String? = null,
) {
    val isActive: Boolean get() = state == "active" || state == null
}

/** `GET /actions/workflows` wraps the list in `{"workflows":[…],"total_count":n}`. */
@Serializable
data class Workflows(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<Workflow> = emptyList(),
)

@Serializable
data class ActionActor(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** One run (execution) of a workflow. */
@Serializable
data class WorkflowRun(
    val id: Long = 0,
    @SerialName("workflow_id") val workflowId: Long = 0,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("run_attempt") val runAttempt: Int = 0,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String? = null,
    val event: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val path: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val actor: ActionActor? = null,
) {
    /** Still queued / running — the conclusion is only known once it completes. */
    val isRunning: Boolean get() = status != null && status != "completed"

    /** The word shown on the status pill (falls back to the raw status while the run is going). */
    val statusWord: String? get() = conclusion ?: status

    val title: String get() = displayTitle?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Run #$runNumber"
}

/** `GET /actions/runs` wraps the list in `{"workflow_runs":[…],"total_count":n}`. */
@Serializable
data class WorkflowRuns(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList(),
)

/** One job of a run, including its steps. */
@Serializable
data class WorkflowJob(
    val id: Long = 0,
    @SerialName("run_id") val runId: Long = 0,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") @Serializable(with = InstantSerializer::class) val startedAt: Instant? = null,
    @SerialName("completed_at") @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val steps: List<WorkflowStep> = emptyList(),
) {
    val statusWord: String? get() = conclusion ?: status
}

/** `GET /actions/runs/{id}/jobs` wraps the list in `{"jobs":[…],"total_count":n}`. */
@Serializable
data class WorkflowJobs(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<WorkflowJob> = emptyList(),
)

@Serializable
data class WorkflowStep(
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val number: Int = 0,
) {
    val statusWord: String? get() = conclusion ?: status
}
