package io.github.busybin.gitrehub.compose.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The editable subset of `PATCH /user`; every field is sent, so clearing one really clears it. */
data class ProfileEdit(
    val name: String,
    val bio: String,
    val company: String,
    val location: String,
    val blog: String,
    val email: String,
) {
    fun toBody(): Map<String, String> = mapOf(
        "name" to name.trim(),
        "bio" to bio.trim(),
        "company" to company.trim(),
        "location" to location.trim(),
        "blog" to blog.trim(),
        "email" to email.trim(),
    )
}

enum class ProfileSaveResult { SAVED, DENIED, FAILED }

class ProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<User>>(LoadState.Loading)
    val state: StateFlow<LoadState<User>> = _state.asStateFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    private val _canEdit = MutableStateFlow(Session.canEditProfile)
    val canEdit: StateFlow<Boolean> = _canEdit.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saveResult = MutableStateFlow<ProfileSaveResult?>(null)
    val saveResult: StateFlow<ProfileSaveResult?> = _saveResult.asStateFlow()

    fun load() {
        viewModelScope.launchLoad({ _state.value = it }) {
            val user = Session.remoteUser()
            _canEdit.value = Session.canEditProfile
            user
        }
    }

    /**
     * `PATCH /user` requires the token scope `user`; fine-grained tokens usually cannot do it. A
     * 403/404 is reported as [ProfileSaveResult.DENIED] instead of a generic failure.
     */
    fun save(edit: ProfileEdit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.updateProfile(edit.toBody()) }
            }
            val response = result.getOrNull()
            val failure = result.exceptionOrNull() as? ApiException
            val outcome = when {
                response != null -> ProfileSaveResult.SAVED
                failure?.error?.status == 403 || failure?.error?.status == 404 -> ProfileSaveResult.DENIED
                else -> ProfileSaveResult.FAILED
            }
            if (outcome == ProfileSaveResult.SAVED && response != null) {
                val stored = withContext(Dispatchers.IO) {
                    Session.storeRemoteUser(response.value, response.scopes)
                }
                _state.value = LoadState.Ready(stored)
                _canEdit.value = Session.canEditProfile
            }
            _saving.value = false
            _saveResult.value = outcome
        }
    }

    fun consumeSaveResult() {
        _saveResult.value = null
    }

    fun logout(onDone: () -> Unit) {
        if (_loggingOut.value) return
        _loggingOut.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Session.logout() } }
            _loggingOut.value = false
            onDone()
        }
    }
}
