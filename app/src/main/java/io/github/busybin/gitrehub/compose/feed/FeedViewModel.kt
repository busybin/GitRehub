package io.github.busybin.gitrehub.compose.feed

import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.TabBadges
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMoreEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Unified activity feed, three sources merged into one list (no tabs):
 *  1. the user's received events (activity on *watched* repos / followed users),
 *  2. their own public activity (pushes, PRs, issues, releases…),
 *  3. [GitHubApi.repoInteractions] — "who starred / forked / opened a PR on *my* repos", which
 *     source 1 cannot deliver on its own: GitHub only sends received events for repos you *watch*,
 *     and owning a repo does not make you watch it.
 * De-duplicated by id (and by content between sources 1/2 and 3), newest first.
 */
class FeedViewModel : PagedViewModel<Event>() {

    private var login: String? = null
    private val seenIds = mutableSetOf<Long>()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Current login, used by the filter's source dimension (mine / actions on my repos). */
    private val _me = MutableStateFlow<String?>(null)
    val me: StateFlow<String?> = _me.asStateFlow()

    private val _interactionsLoaded = MutableStateFlow(false)

    /** Whether the "actions on my repos" source has loaded this round (the filter sheet hints with it). */
    val interactionsLoaded: StateFlow<Boolean> = _interactionsLoaded.asStateFlow()

    override suspend fun prepare() {
        if (login == null) {
            login = Session.currentAccount()?.login
            _me.value = login
        }
    }

    override suspend fun fetch(page: Int): PageResult<Event> = coroutineScope {
        val user = login ?: throw IllegalStateException("not signed in")
        if (page == 1) seenIds.clear()

        // The three sources are independent: fire them together instead of one after the other.
        // Sequentially this was two REST round-trips + a GraphQL round-trip stacked on top of each
        // other — the "several seconds of nothing on screen" users saw when opening the feed.
        val receivedCall = async { runCatching { GitHubApi.receivedEvents(user, page) }.getOrNull() }
        val mineCall = async { runCatching { GitHubApi.userEvents(user, page) }.getOrNull() }
        // "Actions on my repos" (star / fork / PR / issue): a snapshot of the newest N, so only on page 1.
        val interactionsCall = if (page == 1) async {
            runCatching { GitHubApi.repoInteractions() }.getOrNull()
        } else {
            null
        }

        val received = receivedCall.await()
        val mine = mineCall.await()
        val events = received?.value.orEmpty() + mine?.value.orEmpty()
        // A failed GraphQL round-trip must not drop the star/fork rows already painted from disk.
        val fetchedInteractions = interactionsCall?.await()
        val interactions = if (page == 1) {
            fetchedInteractions ?: InteractionsCache.load(user)
        } else {
            emptyList()
        }
        if (fetchedInteractions != null) InteractionsCache.save(user, fetchedInteractions)

        val merged = mergeFeedEvents(events, interactions).filter { seenIds.add(it.id) }

        if (page == 1) {
            _interactionsLoaded.value = interactions.isNotEmpty()
        }

        val hasMore = received?.hasMoreEvents() == true || mine?.hasMoreEvents() == true
        PageResult(merged, hasMore)
    }

    /**
     * Disk-only pass: the two event streams plus the last "actions on my repos" snapshot, so opening
     * the feed shows the previous visit complete — star/fork rows included — instead of having them
     * pop in after the network refresh. Enough to show the previous visit instantly.
     */
    override suspend fun fetchCached(page: Int): PageResult<Event>? {
        val user = login ?: return null
        val received = runCatching { GitHubApi.receivedEvents(user, page, cacheOnly = true) }.getOrNull()
        val mine = runCatching { GitHubApi.userEvents(user, page, cacheOnly = true) }.getOrNull()
        val interactions = if (page == 1) InteractionsCache.load(user) else emptyList()
        val merged = mergeFeedEvents(received?.value.orEmpty() + mine?.value.orEmpty(), interactions)
        if (merged.isEmpty()) return null
        if (page == 1 && interactions.isNotEmpty()) _interactionsLoaded.value = true
        return PageResult(merged, received?.hasMoreEvents() == true || mine?.hasMoreEvents() == true)
    }

    /**
     * "Mark everything as read", done exactly like the notifications screen: `PUT /notifications`
     * tells GitHub that every notification thread is read, and only once that succeeds do we also
     * record the currently loaded feed events as read. Keeping the two in step means reading here
     * shows up over there (and vice versa) instead of being purely cosmetic.
     */
    fun markAllRead() {
        val loaded = currentItems()
        if (loaded.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = runCatching { GitHubApi.markAllNotificationsRead() }.isSuccess
            if (ok) {
                ReadState.markAllRead(loaded.map { it.id })
                ReadState.markReadKeys(loaded.mapNotNull { ThreadKey.ofEvent(it) })
                ReadState.markReadPushes(loaded.mapNotNull { ThreadKey.ofPush(it) })
                // Deeper pages and events not loaded yet are older than this instant: without the
                // watermark they reappeared unread on the next scroll/refresh.
                ReadState.markAllBefore(System.currentTimeMillis())
                TabBadges.setNotifications(0)
            }
            _busy.value = false
        }
    }
}
