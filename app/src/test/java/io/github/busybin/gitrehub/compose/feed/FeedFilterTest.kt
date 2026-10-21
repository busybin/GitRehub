package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventActor
import io.github.busybin.gitrehub.core.model.EventPayload
import io.github.busybin.gitrehub.core.model.EventRepo
import io.github.busybin.gitrehub.core.model.EventSubject
import io.github.busybin.gitrehub.core.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Feed filtering: each dimension on its own, then combinations and the "no filter" default.
 *
 * These are pure functions ([matchesFilter] / [EventType.group]), so they run on the JVM.
 */
class FeedFilterTest {

    private fun event(
        type: EventType = EventType.PushEvent,
        actor: String? = "octocat",
        repo: String? = "octocat/Hello-World",
        ageMillis: Long = 0,
        title: String? = null,
    ) = Event(
        id = 1,
        type = type,
        actor = actor?.let { EventActor(login = it) },
        repo = repo?.let { EventRepo(name = it) },
        createdAt = Instant.now().minusMillis(ageMillis),
        payload = EventPayload(issue = title?.let { EventSubject(title = it) }),
    )

    private val me = "busybin"

    @Test
    fun defaultFilterKeepsEverything() {
        val filter = FeedFilterState.Default
        assertTrue(filter.isDefault)
        assertEquals(0, filter.activeCount)
        assertTrue(event().matchesFilter(filter, me, isRead = false))
        assertTrue(event(type = EventType.ForkEvent, actor = "bot[bot]").matchesFilter(filter, me, true))
    }

    @Test
    fun eventTypesAreGrouped() {
        assertEquals(FeedEventGroup.STAR, EventType.WatchEvent.group())
        assertEquals(FeedEventGroup.STAR, EventType.ForkEvent.group())
        assertEquals(FeedEventGroup.PULL_REQUEST, EventType.PullRequestEvent.group())
        assertEquals(FeedEventGroup.ISSUE, EventType.IssuesEvent.group())
        assertEquals(FeedEventGroup.COMMENT, EventType.PullRequestReviewCommentEvent.group())
        assertEquals(FeedEventGroup.RELEASE, EventType.ReleaseEvent.group())
        assertEquals(FeedEventGroup.FOLLOW, EventType.FollowEvent.group())
        assertEquals(FeedEventGroup.REPO, EventType.DeleteEvent.group())
        // Unknown types fall into OTHER and must not be removed by the default filter.
        assertEquals(FeedEventGroup.OTHER, EventType.Unknown.group())
        assertTrue(event(type = EventType.Unknown).matchesFilter(FeedFilterState.Default, me, false))
    }

    @Test
    fun onlySelectedEventTypesSurvive() {
        val onlyStars = FeedFilterState.Default.copy(groups = setOf(FeedEventGroup.STAR))
        assertTrue(event(type = EventType.WatchEvent).matchesFilter(onlyStars, me, false))
        assertFalse(event(type = EventType.PushEvent).matchesFilter(onlyStars, me, false))
        // Nothing selected = "all", otherwise the list would be empty.
        assertTrue(event().matchesFilter(onlyStars.copy(groups = emptySet()), me, false))
    }

    @Test
    fun timeRangeDropsOldEvents() {
        val week = FeedFilterState.Default.copy(timeRange = FeedTimeRange.WEEK)
        assertTrue(event(ageMillis = 2L * 24 * 3600_000).matchesFilter(week, me, false))
        assertFalse(event(ageMillis = 10L * 24 * 3600_000).matchesFilter(week, me, false))
        // An event with no timestamp is not treated as expired; keep it.
        assertTrue(Event(type = EventType.WatchEvent).matchesFilter(week, me, false))
    }

    @Test
    fun sourceSeparatesMyActivityFromInteractionsOnMyRepos() {
        val mine = FeedFilterState.Default.copy(source = FeedSource.MINE)
        assertTrue(event(actor = me).matchesFilter(mine, me, false))
        assertFalse(event(actor = "someone").matchesFilter(mine, me, false))

        val interactions = FeedFilterState.Default.copy(source = FeedSource.INTERACTIONS)
        assertTrue(event(actor = "someone", repo = "$me/tool").matchesFilter(interactions, me, false))
        // My own actions on my own repos are not "interactions on my repos".
        assertFalse(event(actor = me, repo = "$me/tool").matchesFilter(interactions, me, false))
        // Nor are other people's actions in their own repos.
        assertFalse(event(actor = "someone", repo = "other/tool").matchesFilter(interactions, me, false))
    }

    @Test
    fun readFilterOnlyAffectsReadEvents() {
        val unreadOnly = FeedFilterState.Default.copy(readFilter = FeedReadFilter.UNREAD)
        assertTrue(event().matchesFilter(unreadOnly, me, isRead = false))
        assertFalse(event().matchesFilter(unreadOnly, me, isRead = true))
    }

    @Test
    fun botsCanBeHidden() {
        val filter = FeedFilterState.Default.copy(hideBots = true)
        assertFalse(event(actor = "dependabot[bot]").matchesFilter(filter, me, false))
        assertTrue(event(actor = "octocat").matchesFilter(filter, me, false))
    }

    @Test
    fun keywordsMatchActorRepoAndTitle() {
        val filter = FeedFilterState.Default.copy(keywords = listOf("spammer", "Some/Repo", "i18n"))
        assertFalse(event(actor = "spammer").matchesFilter(filter, me, false))
        assertFalse(event(repo = "some/repo").matchesFilter(filter, me, false)) // case-insensitive
        assertFalse(
            event(type = EventType.IssuesEvent, title = "i18n: add Chinese")
                .matchesFilter(filter, me, false),
        )
        assertTrue(event(actor = "octocat", repo = "octocat/Hello-World").matchesFilter(filter, me, false))
    }

    @Test
    fun activeCountCountsEachEngagedDimension() {
        var filter = FeedFilterState.Default
        assertEquals(0, filter.activeCount)
        filter = filter.copy(groups = setOf(FeedEventGroup.PUSH))
        assertEquals(1, filter.activeCount)
        filter = filter.copy(
            timeRange = FeedTimeRange.WEEK,
            source = FeedSource.INTERACTIONS,
            readFilter = FeedReadFilter.UNREAD,
            order = FeedOrder.OLDEST,
            hideBots = true,
            keywords = listOf("x"),
        )
        assertEquals(7, filter.activeCount)
        assertFalse(filter.isDefault)
    }

    @Test
    fun keywordParsingRoundTrips() {
        assertEquals(listOf("a", "b", "c"), parseKeywords(" a, b;c "))
        assertEquals("a, b", joinKeywords(listOf("a", "b")))
        assertTrue(parseKeywords("   ").isEmpty())
    }
}
