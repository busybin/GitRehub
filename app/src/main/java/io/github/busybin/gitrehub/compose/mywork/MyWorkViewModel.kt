package io.github.busybin.gitrehub.compose.mywork

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.TabBadges
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMoreSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The four action lists the official client puts on its home screen. */
enum class MyWorkSection(@StringRes val labelRes: Int, val query: (String) -> String) {
    // Qualifiers are separated by spaces; GitHubClient percent-encodes them.
    ASSIGNED(R.string.m3_my_work_assigned, { "type:issue assignee:$it is:open" }),
    REVIEW(R.string.m3_my_work_review, { "type:pr review-requested:$it is:open" }),
    MENTIONS(R.string.m3_my_work_mentions, { "mentions:$it is:open" }),
    CREATED(R.string.m3_my_work_created, { "author:$it is:open" }),
}

/**
 * "My work": assigned issues, pull requests waiting for my review, mentions and my own open items,
 * across every repository.
 *
 * One search request per page is enough — GitHub's search API supports every qualifier this needs
 * (`assignee:`, `review-requested:`, `mentions:`, `author:`), so no repository walk is required.
 */
class MyWorkViewModel : PagedViewModel<Issue>() {

    private val _section = MutableStateFlow(MyWorkSection.ASSIGNED)
    val section: StateFlow<MyWorkSection> = _section.asStateFlow()

    private var login: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { refresh() }
    }

    override suspend fun prepare() {
        if (login == null) login = runCatching { Session.currentAccount()?.login }.getOrNull()
    }

    fun select(section: MyWorkSection) {
        if (_section.value == section) return
        _section.value = section
        refresh()
    }

    /** Tapping a row reads it: shared with notifications/feed, and drops it from the tab badge. */
    fun markRead(issue: Issue) {
        val key = ThreadKey.ofIssue(issue) ?: return
        val at = maxOf(System.currentTimeMillis(), issue.updatedAt?.toEpochMilli() ?: 0L)
        ReadState.markReadKey(key, at)
        TabBadges.myWorkRead(key)
    }

    fun markAllRead() {
        val items = currentItems()
        val keys = items.mapNotNull { ThreadKey.ofIssue(it) }
        if (keys.isEmpty()) return
        val at = maxOf(System.currentTimeMillis(), items.maxOf { it.updatedAt?.toEpochMilli() ?: 0L })
        ReadState.markReadKeys(keys, at = at)
        TabBadges.myWorkReadAll(keys)
    }

    override suspend fun fetch(page: Int): PageResult<Issue> {
        val user = login ?: return PageResult(emptyList(), false)
        val result = GitHubApi.searchIssues(
            query = _section.value.query(user),
            page = page,
            sort = "updated",
            order = "desc",
        )
        val items = result.value.items
        if (page == 1) {
            val readKeys = ReadState.readKeys.value
            val readTimes = ReadState.readKeyTimes.value
            val unread = items.mapNotNull { item ->
                val key = ThreadKey.ofIssue(item) ?: return@mapNotNull null
                key.takeIf { !ThreadKey.isRead(key, item.updatedAt?.toEpochMilli(), readKeys, readTimes) }
            }
            TabBadges.setMyWorkSection(_section.value, unread)
        }
        return PageResult(items, result.hasMoreSearch())
    }
}
