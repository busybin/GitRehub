package io.github.busybin.gitrehub.compose.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Label
import io.github.busybin.gitrehub.core.model.Milestone
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Candidates for creating an issue: repo labels, assignable users and milestones. */
data class NewIssueOptions(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
)

/**
 * Create an issue (`POST /repos/{o}/{r}/issues`). Only fields that fit in one request; issue
 * templates (issue forms) would need reading and parsing `ISSUE_TEMPLATE` and are not done here.
 */
class NewIssueViewModel : ViewModel() {

    private val _options = MutableStateFlow(NewIssueOptions())
    val options: StateFlow<NewIssueOptions> = _options.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    /** Issue number once created; the UI navigates with it. */
    private val _created = MutableStateFlow<Int?>(null)
    val created: StateFlow<Int?> = _created.asStateFlow()

    private var key: String = ""

    fun load(owner: String, repo: String, force: Boolean = false) {
        val newKey = "$owner/$repo"
        if (!force && key == newKey && !_options.value.loading) return
        key = newKey
        viewModelScope.launch {
            _options.value = _options.value.copy(loading = true, failed = false)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val labels = GitHubApi.repoLabels(owner, repo)
                    val assignees = GitHubApi.assignableUsers(owner, repo)
                    val milestones = runCatching { GitHubApi.repoMilestones(owner, repo) }
                        .getOrDefault(emptyList())
                    NewIssueOptions(false, false, labels, assignees, milestones)
                }
            }
            _options.value = result.getOrElse { NewIssueOptions(loading = false, failed = true) }
        }
    }

    fun create(
        owner: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String>,
        assignees: List<String>,
        milestone: Int?,
    ) {
        if (_submitting.value) return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _error.value = UiText.res(R.string.m3_new_issue_need_title)
            return
        }
        _submitting.value = true
        _error.value = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.createIssue(
                        owner = owner,
                        repo = repo,
                        title = cleanTitle,
                        body = body.trim().takeIf { it.isNotEmpty() },
                        labels = labels,
                        assignees = assignees,
                        milestone = milestone,
                    )
                }
            }
            _submitting.value = false
            result.onSuccess { _created.value = it.number }
                .onFailure { _error.value = it.toUiText() }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
