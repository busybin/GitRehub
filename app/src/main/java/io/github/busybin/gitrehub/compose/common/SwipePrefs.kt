package io.github.busybin.gitrehub.compose.common

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a horizontal swipe on a notification row does.
 *
 * One of the things users miss most in the official client is configurable gestures ("no gestures",
 * "the third-party clients let you pick swipe actions"). Rather than hard-coding "swipe = mark read"
 * we make both directions pickable.
 */
enum class SwipeAction(@StringRes val labelRes: Int) {
    MARK_READ(R.string.m3_swipe_mark_read),
    MARK_UNREAD(R.string.m3_swipe_mark_unread),
    NONE(R.string.m3_swipe_none),
    ;

    companion object {
        fun from(name: String?): SwipeAction =
            entries.firstOrNull { it.name == name } ?: MARK_READ
    }
}

/**
 * The two swipe directions of a list row, persisted through [PrefHelper] (so the legacy
 * backup/restore carries them like every other modern-UI setting).
 *
 * Default: **both** directions mark the thread as read — that is what the app did before this
 * setting existed, so upgrades keep behaving the way users already learned.
 */
object SwipePrefs {
    /** Swipe left to right (M3 StartToEnd). */
    internal const val KEY_START = "m3_swipe_start"

    /** Swipe right to left (M3 EndToStart). */
    internal const val KEY_END = "m3_swipe_end"

    private val _start = MutableStateFlow(read(KEY_START))
    val start: StateFlow<SwipeAction> = _start.asStateFlow()

    private val _end = MutableStateFlow(read(KEY_END))
    val end: StateFlow<SwipeAction> = _end.asStateFlow()

    fun setStart(action: SwipeAction) {
        PrefHelper.putAny(KEY_START, action.name)
        _start.value = action
    }

    fun setEnd(action: SwipeAction) {
        PrefHelper.putAny(KEY_END, action.name)
        _end.value = action
    }

    private fun read(key: String): SwipeAction =
        SwipeAction.from(PrefHelper.getString(key))
}
