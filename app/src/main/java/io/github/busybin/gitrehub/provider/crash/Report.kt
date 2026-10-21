package io.github.busybin.gitrehub.provider.crash

import android.content.Context
import android.util.Log

class Report {
    companion object {
        private const val TAG = "GitRehub"

        fun init(context: Context) {
        }

        fun reportCatchException(e: Exception) {
            // Was Timber, dropped with the legacy dependency; back on the system Log.
            Log.e(TAG, "GitRehub Crash Report", e)
        }
    }
}
