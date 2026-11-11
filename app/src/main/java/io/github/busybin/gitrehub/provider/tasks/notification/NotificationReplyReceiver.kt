package io.github.busybin.gitrehub.provider.tasks.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.BundleConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Inline reply from the notification shade, sent as the account the thread belongs to. */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationActions.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return
        val owner = intent.getStringExtra("owner").orEmpty()
        val repo = intent.getStringExtra("repo").orEmpty()
        val number = intent.getIntExtra("number", 0)
        val threadId = intent.getLongExtra("thread", 0L)
        val login = intent.getStringExtra(BundleConstant.ACCOUNT)
        if (owner.isBlank() || repo.isBlank() || number <= 0) return
        val token = login?.takeIf { it.isNotBlank() }?.let { TokenStore.tokenFor(it) }
        // The account was removed since the notification was posted: do not post as the current one.
        if (!login.isNullOrBlank() && token == null) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                GitHubApi.createComment(owner, repo, number, text, token = token)
                if (threadId > 0) {
                    runCatching { GitHubApi.markThreadRead(threadId.toString(), token) }
                }
                NotificationManagerCompat.from(context)
                    .cancel(NotificationActions.managerId(threadId))
            } catch (_: Throwable) {
                // Keep the notification: the user can still open the thread and try again there.
            } finally {
                pending.finish()
            }
        }
    }
}
