package io.github.busybin.gitrehub.compose.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.DiscussionSetup
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creating a discussion is GraphQL-only (`createDiscussion`), and it needs two node ids that the
 * REST list does not carry: the repository and the category. Both arrive in one [DiscussionSetup].
 */
class NewDiscussionViewModel : ViewModel() {

    private val _setup = MutableStateFlow<DiscussionSetup?>(null)
    val setup: StateFlow<DiscussionSetup?> = _setup.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    /** Number of the created discussion; the UI navigates with it. */
    private val _created = MutableStateFlow<Int?>(null)
    val created: StateFlow<Int?> = _created.asStateFlow()

    private var key: String = ""

    fun load(owner: String, repo: String, force: Boolean = false) {
        val newKey = "$owner/$repo"
        if (!force && key == newKey && _setup.value != null) return
        key = newKey
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.discussionSetup(owner, repo) }
            }
            _loading.value = false
            val setup = result.getOrNull()
            _setup.value = setup
            // A repository with discussions disabled answers nothing usable; treat it as a failure
            // with a retry rather than an empty form that cannot submit.
            _failed.value = setup == null || setup.categories.isEmpty()
        }
    }

    fun create(owner: String, repo: String, categoryId: String?, title: String, body: String) {
        if (_submitting.value) return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _error.value = UiText.res(R.string.m3_new_discussion_need_title)
            return
        }
        val setup = _setup.value
        if (categoryId.isNullOrBlank() || setup == null) {
            _error.value = UiText.res(R.string.m3_new_discussion_need_category)
            return
        }
        _submitting.value = true
        _error.value = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.createDiscussion(
                        repositoryId = setup.repositoryId,
                        categoryId = categoryId,
                        title = cleanTitle,
                        body = body.trim(),
                    )
                }
            }
            _submitting.value = false
            result.onSuccess { _created.value = it }
                .onFailure { _error.value = it.toUiText() }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
