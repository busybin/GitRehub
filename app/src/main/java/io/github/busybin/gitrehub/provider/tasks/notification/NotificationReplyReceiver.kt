package io.github.busybin.gitrehub.provider.tasks.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inline reply from the notification shade (`RemoteInput`): posts a comment and marks the thread
 * read. Running in a receiver means the process can die at any moment, hence [goAsync] + a scope
 * that survives `onReceive` returning. Failures are silent on purpose — the reply text is gone by
 * then, and a toast from the background would only confuse.
 */
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
        if (owner.isBlank() || repo.isBlank() || number <= 0) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                GitHubApi.createComment(owner, repo, number, text)
                if (threadId > 0) runCatching { GitHubApi.markThreadRead(threadId.toString()) }
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
