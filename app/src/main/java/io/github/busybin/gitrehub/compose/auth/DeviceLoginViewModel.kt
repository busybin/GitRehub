package io.github.busybin.gitrehub.compose.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.auth.DeviceFlow
import io.github.busybin.gitrehub.core.auth.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the device flow: fetch a user code, then poll until the user has authorised (or the code
 * expires). Polling lives in [viewModelScope], so leaving the screen cancels it.
 */
class DeviceLoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<DeviceFlow.Challenge>>(LoadState.Loading)
    val state: StateFlow<LoadState<DeviceFlow.Challenge>> = _state.asStateFlow()

    private val _status = MutableStateFlow(UiText.res(R.string.m3_device_waiting))
    val status: StateFlow<UiText> = _status.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true || _done.value) return
        _state.value = LoadState.Loading
        job = viewModelScope.launch {
            val challenge = try {
                withContext(Dispatchers.IO) { DeviceFlow.requestChallenge(SCOPES) }
            } catch (t: Throwable) {
                _state.value = LoadState.Failure(t.asUiText())
                return@launch
            }
            _state.value = LoadState.Ready(challenge)
            awaitAuthorization(challenge)
        }
    }

    fun restart() {
        job?.cancel()
        job = null
        _done.value = false
        _status.value = UiText.res(R.string.m3_device_waiting)
        start()
    }

    private suspend fun awaitAuthorization(challenge: DeviceFlow.Challenge) {
        // GitHub wants at least `interval` seconds between polls; slow_down means wait longer
        var intervalSeconds = challenge.intervalSeconds.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + challenge.expiresInSeconds * 1000L
        // Network blips (common in the background) must not abort the flow; a run of them does.
        var consecutiveErrors = 0

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)
            when (val poll = DeviceFlow.poll(challenge.deviceCode)) {
                is DeviceFlow.Poll.Pending -> {
                    consecutiveErrors = 0
                    _status.value = UiText.res(R.string.m3_device_waiting)
                }
                is DeviceFlow.Poll.SlowDown -> {
                    consecutiveErrors = 0
                    intervalSeconds += 5
                    _status.value = UiText.res(R.string.m3_device_slowdown)
                }
                is DeviceFlow.Poll.Expired -> {
                    _status.value = UiText.res(R.string.m3_device_expired)
                    return
                }
                is DeviceFlow.Poll.Denied -> {
                    _status.value = UiText.res(R.string.m3_device_denied)
                    return
                }
                is DeviceFlow.Poll.Error -> {
                    consecutiveErrors++
                    _status.value = poll.text()
                    if (consecutiveErrors >= 5) return
                    delay(intervalSeconds * 1000L)
                }
                is DeviceFlow.Poll.Token -> {
                    _status.value = UiText.res(R.string.m3_device_success)
                    val result = runCatching {
                        withContext(Dispatchers.IO) { Session.loginWithToken(poll.token) }
                    }
                    if (result.isSuccess) {
                        _done.value = true
                    } else {
                        _status.value = result.exceptionOrNull()?.asUiText()
                            ?: UiText.res(R.string.m3_login_failed)
                    }
                    return
                }
            }
        }
        _status.value = UiText.res(R.string.m3_device_expired)
    }

    private companion object {
        const val SCOPES = "repo,user,notifications,read:org,gist,workflow,read:packages"
    }
}

/** Device-flow reasons are localized here; everything else uses the generic status mapping. */
private fun DeviceFlow.Poll.Error.text(): UiText = when (reason) {
    DeviceFlow.Reason.DEVICE_FLOW_DISABLED -> UiText.res(R.string.m3_device_flow_disabled)
    DeviceFlow.Reason.CLIENT_ID_INVALID -> UiText.res(R.string.m3_device_client_id_invalid)
    DeviceFlow.Reason.GRANT_REJECTED -> UiText.res(R.string.m3_device_grant_rejected)
    DeviceFlow.Reason.NO_CODE -> UiText.res(R.string.m3_device_no_code)
    DeviceFlow.Reason.NO_TOKEN -> UiText.res(R.string.m3_device_no_token)
    DeviceFlow.Reason.NETWORK ->
        if (detail.isNullOrBlank()) UiText.res(R.string.m3_network_error)
        else UiText.res(R.string.m3_device_connect_failed, detail)

    DeviceFlow.Reason.OTHER -> UiText.raw(detail ?: "Unknown device flow error")
}

private fun Throwable.asUiText(): UiText =
    (this as? DeviceFlow.Failure)?.let { failure ->
        when (failure.reason) {
            DeviceFlow.Reason.DEVICE_FLOW_DISABLED -> UiText.res(R.string.m3_device_flow_disabled)
            DeviceFlow.Reason.CLIENT_ID_INVALID -> UiText.res(R.string.m3_device_client_id_invalid)
            DeviceFlow.Reason.GRANT_REJECTED -> UiText.res(R.string.m3_device_grant_rejected)
            DeviceFlow.Reason.NO_CODE -> UiText.res(R.string.m3_device_no_code)
            DeviceFlow.Reason.NO_TOKEN -> UiText.res(R.string.m3_device_no_token)
            DeviceFlow.Reason.NETWORK -> UiText.res(R.string.m3_device_connect_failed, failure.detail.orEmpty())
            DeviceFlow.Reason.OTHER -> UiText.raw(failure.detail ?: "Unknown device flow error")
        }
    } ?: this.toUiText()
