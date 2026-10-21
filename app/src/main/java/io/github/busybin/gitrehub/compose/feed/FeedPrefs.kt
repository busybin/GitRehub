package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [FeedFilterState] in MMKV (keys prefixed `m3_`), so filters survive a restart until
 * [clearFilter] resets them.
 */
object FeedPrefs {

    private const val KEY_BLOCKED = "m3_feed_blocked_keywords"
    private const val KEY_HIDE_READ = "m3_feed_hide_read"
    private const val KEY_GROUPS = "m3_feed_groups"
    private const val KEY_TIME_RANGE = "m3_feed_time_range"
    private const val KEY_SOURCE = "m3_feed_source"
    private const val KEY_ORDER = "m3_feed_order"
    private const val KEY_HIDE_BOTS = "m3_feed_hide_bots"

    private val _filter = MutableStateFlow(load())
    val filter: StateFlow<FeedFilterState> = _filter.asStateFlow()

    /** Raw keyword text (comma separated); kept for old callers. */
    val blocked: StateFlow<String>
        get() = blockedFlow

    private val blockedFlow = MutableStateFlow(joinKeywords(_filter.value.keywords))

    /** "Hide read" toggle (equals read filter = unread); kept for old callers/tests. */
    val hideRead: StateFlow<Boolean>
        get() = hideReadFlow

    private val hideReadFlow = MutableStateFlow(_filter.value.readFilter == FeedReadFilter.UNREAD)

    fun setHideRead(enabled: Boolean) = setReadFilter(
        if (enabled) FeedReadFilter.UNREAD else FeedReadFilter.ALL
    )

    /** Keywords as typed by the user: separated by comma, semicolon or whitespace. */
    fun keywordList(): List<String> = _filter.value.keywords

    /** True when any of [text] contains one of the blocked keywords (case-insensitive). */
    fun isBlocked(vararg text: String?): Boolean {
        val keywords = keywordList()
        if (keywords.isEmpty()) return false
        return text.any { candidate ->
            candidate != null && keywords.any { candidate.contains(it, ignoreCase = true) }
        }
    }

    fun setBlocked(raw: String) = setKeywords(parseKeywords(raw))

    fun setKeywords(keywords: List<String>) = update { it.copy(keywords = keywords.distinct()) }

    fun setGroups(groups: Set<FeedEventGroup>) = update {
        it.copy(groups = groups.ifEmpty { FeedEventGroup.entries.toSet() })
    }

    fun toggleGroup(group: FeedEventGroup) {
        val current = _filter.value.groups
        setGroups(if (group in current) current - group else current + group)
    }

    fun setTimeRange(range: FeedTimeRange) = update { it.copy(timeRange = range) }

    fun setSource(source: FeedSource) = update { it.copy(source = source) }

    fun setReadFilter(read: FeedReadFilter) = update { it.copy(readFilter = read) }

    fun setOrder(order: FeedOrder) = update { it.copy(order = order) }

    fun setHideBots(enabled: Boolean) = update { it.copy(hideBots = enabled) }

    /** "Clear filter": reset every condition to the default, keywords included, and persist. */
    fun clearFilter() {
        persist(FeedFilterState.Default)
        _filter.value = FeedFilterState.Default
        blockedFlow.value = ""
        hideReadFlow.value = false
    }

    private inline fun update(transform: (FeedFilterState) -> FeedFilterState) {
        val next = transform(_filter.value)
        if (next == _filter.value) return
        persist(next)
        _filter.value = next
        blockedFlow.value = joinKeywords(next.keywords)
        hideReadFlow.value = next.readFilter == FeedReadFilter.UNREAD
    }

    private fun persist(state: FeedFilterState) {
        put(KEY_GROUPS, state.groups.joinToString(",") { it.name })
        put(KEY_TIME_RANGE, state.timeRange.name)
        put(KEY_SOURCE, state.source.name)
        put(KEY_ORDER, state.order.name)
        put(KEY_HIDE_BOTS, state.hideBots)
        put(KEY_BLOCKED, joinKeywords(state.keywords))
        put(KEY_HIDE_READ, state.readFilter == FeedReadFilter.UNREAD)
    }

    private fun load(): FeedFilterState {
        val defaults = FeedFilterState.Default
        val unread = PrefHelper.isExist<Boolean>(KEY_HIDE_READ) && PrefHelper.getBoolean(KEY_HIDE_READ)
        return FeedFilterState(
            groups = parseEnumSet(PrefHelper.getString(KEY_GROUPS), FeedEventGroup.entries)
                .ifEmpty { defaults.groups },
            timeRange = parseEnum(PrefHelper.getString(KEY_TIME_RANGE), defaults.timeRange),
            source = parseEnum(PrefHelper.getString(KEY_SOURCE), defaults.source),
            order = parseEnum(PrefHelper.getString(KEY_ORDER), defaults.order),
            hideBots = PrefHelper.getBoolean(KEY_HIDE_BOTS),
            keywords = parseKeywords(PrefHelper.getString(KEY_BLOCKED).orEmpty()),
            readFilter = if (unread) FeedReadFilter.UNREAD else FeedReadFilter.ALL,
        )
    }

    private fun put(key: String, value: String) {
        if (value.isEmpty()) PrefHelper.clearKey<String>(key) else PrefHelper.putAny(key, value)
    }

    private fun put(key: String, value: Boolean) = PrefHelper.putAny(key, value)

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == raw } ?: fallback

    private inline fun <reified T : Enum<T>> parseEnumSet(raw: String?, all: List<T>): Set<T> {
        if (raw.isNullOrBlank()) return emptySet()
        val names = raw.split(',').map { it.trim() }.toSet()
        return all.filter { it.name in names }.toSet()
    }

}
