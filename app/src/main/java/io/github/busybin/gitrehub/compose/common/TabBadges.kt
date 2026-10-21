package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.compose.mywork.MyWorkSection
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Counts for the bottom-bar badges (notifications and "my work").
 *
 * Kept outside the screens because the bottom bar needs them even when neither screen was opened.
 * Local read actions update the notification count directly; [refreshAll] is only the slow path
 * (app start / resume), and it is throttled so switching tabs does not burn search rate limit.
 */
object TabBadges {

    private const val STALE_MS = 60_000L

    private val _notifications = MutableStateFlow(0)
    val notifications: StateFlow<Int> = _notifications.asStateFlow()

    private val _myWork = MutableStateFlow(0)
    val myWork: StateFlow<Int> = _myWork.asStateFlow()

    private val myWorkSections = mutableMapOf<MyWorkSection, Set<String>>()

    @Volatile
    private var refreshing = false

    @Volatile
    private var lastRefresh = 0L

    fun setNotifications(count: Int) {
        _notifications.value = count.coerceAtLeast(0)
    }

    /** A single row was marked read (already counted as unread before the action). */
    fun notificationRead() = setNotifications(_notifications.value - 1)

    fun notificationUnread() = setNotifications(_notifications.value + 1)

    /**
     * The badge counts unread rows, not every open row: GitHub's `total_count` never goes down when
     * you read something, so using it made the bubble impossible to clear.
     */
    @Synchronized
    fun setMyWorkSection(section: MyWorkSection, unreadKeys: Collection<String>) {
        myWorkSections[section] = unreadKeys.toSet()
        publishMyWork()
    }

    /** One row was read (or opened); drop it from every section, it can appear in more than one. */
    @Synchronized
    fun myWorkRead(key: String?) {
        if (key.isNullOrBlank()) return
        var changed = false
        myWorkSections.keys.toList().forEach { section ->
            val current = myWorkSections[section] ?: return@forEach
            if (key in current) {
                myWorkSections[section] = current - key
                changed = true
            }
        }
        if (changed) publishMyWork()
    }

    @Synchronized
    fun myWorkReadAll(keys: Collection<String>) {
        if (keys.isEmpty()) return
        var changed = false
        myWorkSections.keys.toList().forEach { section ->
            val current = myWorkSections[section] ?: return@forEach
            val rest = current - keys.toSet()
            if (rest.size != current.size) {
                myWorkSections[section] = rest
                changed = true
            }
        }
        if (changed) publishMyWork()
    }

    private fun publishMyWork() {
        _myWork.value = myWorkSections.values.flatten().toSet().size
    }

    fun reset() {
        myWorkSections.clear()
        _notifications.value = 0
        _myWork.value = 0
        lastRefresh = 0L
    }

    /** Reload both counts; pass [force] after login/account switch, the default is cheap to call often. */
    suspend fun refreshAll(force: Boolean = false) {
        if (refreshing) return
        if (!force && System.currentTimeMillis() - lastRefresh < STALE_MS) return
        refreshing = true
        try {
            withContext(Dispatchers.IO) {
                if (!Session.isLoggedIn) {
                    reset()
                    return@withContext
                }
                val login = Session.currentAccount()?.login
                if (login.isNullOrBlank()) return@withContext
                runCatching { refreshNotifications() }
                runCatching { refreshMyWork(login) }
                lastRefresh = System.currentTimeMillis()
            }
        } finally {
            refreshing = false
        }
    }

    private suspend fun refreshNotifications() {
        val items = GitHubApi.notifications(all = true).value
        val keys = ReadState.readKeys.value
        val times = ReadState.readKeyTimes.value
        setNotifications(
            items.count { item ->
                item.unread && !ThreadKey.isReadLocally(
                    apiUrl = item.subject?.url,
                    updatedAt = item.updatedAt?.toEpochMilli(),
                    keys = keys,
                    readAt = times,
                )
            },
        )
    }

    private suspend fun refreshMyWork(login: String) = coroutineScope {
        val readKeys = ReadState.readKeys.value
        val readTimes = ReadState.readKeyTimes.value
        val unread = MyWorkSection.entries.map { section ->
            async {
                section to runCatching {
                    GitHubApi.searchIssues(section.query(login), page = 1, sort = "updated", order = "desc")
                        .value
                        .items
                        .mapNotNull { item ->
                            val key = ThreadKey.ofIssue(item) ?: return@mapNotNull null
                            key.takeIf { !ThreadKey.isRead(key, item.updatedAt?.toEpochMilli(), readKeys, readTimes) }
                        }
                }.getOrDefault(emptyList())
            }
        }
        unread.forEach { deferred ->
            val (section, keys) = deferred.await()
            setMyWorkSection(section, keys)
        }
    }
}
