package io.github.busybin.gitrehub.compose.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<User>?>(null)
    val state: StateFlow<LoadState<User>?> = _state.asStateFlow()

    fun clearError() {
        if (_state.value is LoadState.Failure) _state.value = null
    }

    fun login(token: String) {
        if (token.isBlank()) {
            _state.value = LoadState.Failure(UiText.res(R.string.m3_login_need_token))
            return
        }
        viewModelScope.launchLoad({ _state.value = it }) { Session.loginWithToken(token) }
    }
}
