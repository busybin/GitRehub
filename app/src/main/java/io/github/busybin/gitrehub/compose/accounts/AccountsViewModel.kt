package io.github.busybin.gitrehub.compose.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.auth.StoredAccounts
import io.github.busybin.gitrehub.core.auth.StoredAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountsViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<StoredAccounts>>(LoadState.Loading)
    val state: StateFlow<LoadState<StoredAccounts>> = _state.asStateFlow()

    /** True while a switch/removal is in flight, so the rows cannot be tapped twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A short message flashed on screen. */
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launchLoad({ _state.value = it }) { Session.accounts() }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Makes [account] the logged-in one. [onSwitched] runs on the main thread once the token is in
     * place — the caller restarts the UI from there, because the current ViewModels belong to the
     * previous account.
     */
    fun switchTo(account: StoredAccount, onSwitched: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Session.switchTo(account.login.orEmpty()) }
            _busy.value = false
            if (ok) {
                onSwitched()
            } else {
                _message.value = UiText.res(R.string.m3_accounts_switch_failed)
                load()
            }
        }
    }

    fun remove(account: StoredAccount) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Session.removeAccount(account.login.orEmpty()) }
            _busy.value = false
            if (ok) {
                _message.value = UiText.res(R.string.m3_accounts_removed, account.login.orEmpty())
            } else {
                _message.value = UiText.res(R.string.m3_accounts_remove_failed)
            }
            load()
        }
    }
}
