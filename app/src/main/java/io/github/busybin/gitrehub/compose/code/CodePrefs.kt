package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Code reader preferences. Line wrapping is the most requested feature of the official client, so
 * it is a real setting here: wrapped by default (mobile screens are narrow), switchable per user.
 */
object CodePrefs {
    private const val KEY_WRAP = "m3_code_wrap"

    private val _wrap = MutableStateFlow(read())
    val wrap: StateFlow<Boolean> = _wrap.asStateFlow()

    fun setWrap(value: Boolean) {
        PrefHelper.putAny(KEY_WRAP, if (value) "1" else "0")
        _wrap.value = value
    }

    fun toggle() = setWrap(!_wrap.value)

    private fun read(): Boolean = PrefHelper.getString(KEY_WRAP) != "0"
}
