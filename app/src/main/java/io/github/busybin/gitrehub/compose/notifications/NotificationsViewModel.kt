package io.github.busybin.gitrehub.compose.notifications

import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.TabBadges
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.net.ApiResponse
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsViewModel : PagedViewModel<Notification>() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    override suspend fun fetch(page: Int): PageResult<Notification> =
        toPage(GitHubApi.notifications(all = true))

    override suspend fun fetchCached(page: Int): PageResult<Notification>? =
        runCatching { GitHubApi.notifications(all = true, cacheOnly = true) }
            .getOrNull()
            ?.let { toPage(it) }
            ?.takeIf { it.items.isNotEmpty() }

    private fun toPage(response: ApiResponse<List<Notification>>): PageResult<Notification> {
        // GitHub caps this endpoint, so there is never a second page.
        val items = response.value
        // Threads read elsewhere are recorded at their last update, so activity that arrives later
        // (server sends them back as unread) is not suppressed by the local read state.
        items.filter { !it.unread }.forEach { item ->
            ReadState.markReadKey(
                ThreadKey.fromApiUrl(item.subject?.url),
                at = item.updatedAt?.toEpochMilli() ?: System.currentTimeMillis(),
            )
        }
        TabBadges.setNotifications(unreadCount(items))
        return PageResult(items, hasMore = false)
    }

    private fun unreadCount(items: List<Notification>): Int {
        val keys = ReadState.readKeys.value
        val times = ReadState.readKeyTimes.value
        return items.count { item ->
            item.unread && !ThreadKey.isReadLocally(
                apiUrl = item.subject?.url,
                updatedAt = item.updatedAt?.toEpochMilli(),
                keys = keys,
                readAt = times,
            )
        }
    }

    /** PUT /notifications marks every thread as read — same call the activity feed makes. */
    fun markAllAsRead() {
        val current = (state.value as? LoadState.Ready)?.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.markAllNotificationsRead() }
            }.isSuccess
            if (ok) {
                publish(current.map { it.copy(unread = false) })
                ReadState.markReadKeys(current.mapNotNull { ThreadKey.fromApiUrl(it.subject?.url) })
                TabBadges.setNotifications(0)
            }
            _busy.value = false
        }
    }

    /**
     * Swipe action: mark a single thread as read. The row and the shared read state change first,
     * so a process death while the PATCH is in flight cannot bring the row back as unread; the
     * server call then either confirms it or rolls both back.
     */
    fun markAsRead(item: Notification) {
        if (!item.unread) return
        val key = ThreadKey.fromApiUrl(item.subject?.url)
        val at = maxOf(System.currentTimeMillis(), item.updatedAt?.toEpochMilli() ?: 0L)
        replaceItem({ it.id == item.id }) { it.copy(unread = false) }
        ReadState.markReadKey(key, at = at)
        TabBadges.notificationRead()
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.markThreadRead(item.id) }
            }.isSuccess
            if (!ok) {
                // the PATCH failed: put the row and the shared state back so the UI stays honest
                replaceItem({ it.id == item.id }) { it.copy(unread = true) }
                ReadState.unmarkReadKey(key)
                TabBadges.notificationUnread()
            }
        }
    }

    /** Swipe action: mark a single thread as unread again (DELETE /notifications/threads/{id}). */
    fun markAsUnread(item: Notification) {
        if (item.unread) return
        val key = ThreadKey.fromApiUrl(item.subject?.url)
        val previous = key?.let { ReadState.readKeyTimes.value[it] }
            ?: maxOf(System.currentTimeMillis(), item.updatedAt?.toEpochMilli() ?: 0L)
        replaceItem({ it.id == item.id }) { it.copy(unread = true) }
        ReadState.unmarkReadKey(key)
        TabBadges.notificationUnread()
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.markThreadUnread(item.id) }
            }.isSuccess
            if (!ok) {
                replaceItem({ it.id == item.id }) { it.copy(unread = false) }
                ReadState.markReadKey(key, at = previous)
                TabBadges.notificationRead()
            }
        }
    }
}
