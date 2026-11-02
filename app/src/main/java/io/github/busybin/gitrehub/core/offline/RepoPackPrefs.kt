package io.github.busybin.gitrehub.core.offline

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Account-wide switch for automatic pack refreshes.
 *
 * Off means the startup watcher and the "branch moved" check on the repository screen do nothing;
 * manual saves and updates keep working. The per-pack [RepoPackMeta.autoSync] switch narrows this
 * further, so both must be true for a refresh.
 */
object RepoPackPrefs {

    private const val KEY = "m3_pack_auto_sync_enabled"

    private val _enabled = MutableStateFlow(true)

    /** Mirrors the stored value; the UI collects it so a toggle updates every screen. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Reads the stored value; MMKV must be initialised (see `App.init`). */
    fun load() {
        _enabled.value = if (PrefHelper.isExist<Boolean>(KEY)) PrefHelper.getBoolean(KEY) else true
    }

    fun isEnabled(): Boolean = _enabled.value

    /** Turning this off is expected to also drop queued automatic refreshes (see the caller). */
    fun setEnabled(enabled: Boolean) {
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        PrefHelper.putAny(KEY, enabled)
    }
}
