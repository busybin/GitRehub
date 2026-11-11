package io.github.busybin.gitrehub.core.notify

import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.net.GitHubApi

/**
 * Polls every stored account, not only the one in use.
 *
 * GitHub has no "all my accounts" endpoint, so a round is one `GET /notifications` per account.
 * Thread ids are global, so a thread watched by two accounts is shown once (the current account
 * wins, because it is queried first).
 */
object NotificationPoller {

    data class Inbox(val login: String, val threads: List<Notification>)

    /**
     * One round over every declared account, current first. [failed] names the accounts whose
     * request failed: their rows must survive, because a missing inbox is not the same as "read".
     */
    data class Round(val inboxes: List<Inbox>, val failed: Set<String>)

    /**
     * Returns null when no request succeeded, so callers can tell "no news" from "network down".
     * No stored account is a valid empty round, not a failure: retrying it would loop forever.
     */
    suspend fun fetch(since: String?): Round? {
        val current = TokenStore.current()?.login
        val accounts = TokenStore.accounts()
            .filter { !it.isEnterprise && it.token.isNotBlank() }
            .sortedByDescending { it.login.equals(current, ignoreCase = true) }
        if (accounts.isEmpty()) return Round(emptyList(), emptySet())
        var anySucceeded = false
        val inboxes = ArrayList<Inbox>(accounts.size)
        val failed = HashSet<String>()
        for (account in accounts) {
            val threads = runCatching {
                GitHubApi.notifications(since = since, token = account.token).value
            }.getOrNull()
            if (threads == null) {
                failed += account.login
                continue
            }
            anySucceeded = true
            inboxes += Inbox(account.login, threads)
        }
        return if (anySucceeded) Round(inboxes, failed) else null
    }

    /**
     * The unread threads worth a notification: at most [limit], deduped by thread id, skipping
     * [skip] (threads an earlier round already raised).
     */
    fun unread(
        inboxes: List<Inbox>,
        limit: Int,
        skip: Set<Long> = emptySet(),
    ): List<Pair<String, Notification>> {
        val seen = HashSet<Long>()
        val result = ArrayList<Pair<String, Notification>>()
        for (inbox in inboxes) {
            for (thread in inbox.threads) {
                if (!thread.unread || thread.numericId in skip) continue
                if (!seen.add(thread.numericId)) continue
                if (result.size >= limit) return result
                result += inbox.login to thread
            }
        }
        return result
    }

    /**
     * Rows the shade still has but the inbox no longer marks unread. Only these are cancelled, so a
     * poll never wipes notifications it did not post (downloads, foreground-service rows).
     */
    fun stale(posted: Set<Long>, unread: Set<Long>): Set<Long> = posted - unread

    /** Unread count across accounts; a thread watched twice counts once (it is one badge). */
    fun unreadCount(inboxes: List<Inbox>): Int =
        inboxes.flatMap { inbox -> inbox.threads.filter { it.unread } }
            .distinctBy { it.numericId }
            .size
}
