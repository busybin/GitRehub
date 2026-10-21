package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.content.Intent
import io.github.busybin.gitrehub.compose.MainComposeActivity

/**
 * Recreates the modern UI from scratch.
 *
 * Switching accounts changes what every screen should show, but the ViewModels are *retained* by
 * the navigation graph (the tab routes are restored with `saveState = true`), so the feed, repo and
 * notification lists would keep showing the previous account. `CLEAR_TASK` throws the whole task
 * away — new `Activity`, new `ViewModelStore`, fresh start at the feed.
 */
fun restartModernUi(context: Context) {
    context.startActivity(
        Intent(context, MainComposeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}
