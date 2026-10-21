package io.github.busybin.gitrehub.helper

import android.util.Log
import io.github.busybin.gitrehub.BuildConfig

/** Debug-only logging; a no-op in release builds. */
object Logger {
    private const val TAG = "GitRehub"

    @JvmStatic
    fun d(message: Any?) {
        if (BuildConfig.DEBUG) Log.d(TAG, message?.toString() ?: "null")
    }

    @JvmStatic
    fun i(message: Any?) {
        if (BuildConfig.DEBUG) Log.i(TAG, message?.toString() ?: "null")
    }

    @JvmStatic
    fun e(vararg parts: Any?) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, if (parts.isEmpty()) "null" else parts.joinToString(" ") { it?.toString() ?: "null" })
    }

    @JvmStatic
    fun e(parts: List<Any?>?) {
        if (BuildConfig.DEBUG) Log.e(TAG, parts?.joinToString(" ") { it?.toString() ?: "null" } ?: "null")
    }
}
