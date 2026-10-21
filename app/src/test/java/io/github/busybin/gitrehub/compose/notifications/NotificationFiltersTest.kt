package io.github.busybin.gitrehub.compose.notifications

import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.model.NotificationSubject
import io.github.busybin.gitrehub.core.model.Repo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Calendar

/**
 * Notification-screen filtering/grouping (pure Kotlin, no device).
 *
 * Getting these rules wrong is exactly what users see as "nothing left after filtering" or "a missed
 * important one", so each rule is pinned individually.
 */
class NotificationFiltersTest {

    private val now = System.currentTimeMillis()

    private fun daysAgo(days: Int): Instant = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time.toInstant()

    private fun notification(
        id: Long,
        repo: String? = "busybin/gitrehub",
        subjectType: String? = "Issue",
        reason: String? = "mention",
        unread: Boolean = true,
        updatedAt: Instant? = daysAgo(0),
    ) = Notification(
        id = id.toString(),
        repository = repo?.let { Repo(id = id, fullName = it, name = it.substringAfter('/')) },
        subject = NotificationSubject(title = "thread $id", type = subjectType),
        reason = reason,
        unread = unread,
        updatedAt = updatedAt,
    )

    @Test
    fun typeChipsMatchTheirOwnKind() {
        val issue = notification(1, subjectType = "Issue")
        val pull = notification(2, subjectType = "PullRequest")
        val commit = notification(3, subjectType = "Commit")
        // Discussion / Release / CheckSuite are real GitHub subject types without a chip.
        val discussion = notification(4, subjectType = "Discussion")

        assertTrue(NotificationFilter.ISSUE.matches(issue))
        assertFalse(NotificationFilter.ISSUE.matches(pull))
        assertTrue(NotificationFilter.PULL.matches(pull))
        assertTrue(NotificationFilter.COMMIT.matches(commit))
        assertFalse(NotificationFilter.COMMIT.matches(issue))

        // "Other" = anything not issue / PR / commit, including a missing type.
        assertTrue(NotificationFilter.OTHER.matches(discussion))
        assertTrue(NotificationFilter.OTHER.matches(notification(5, subjectType = null)))
        assertFalse(NotificationFilter.OTHER.matches(issue))
        assertTrue(NotificationFilter.ALL.matches(issue))
    }

    @Test
    fun unreadChipOnlyKeepsUnreadRows() {
        assertTrue(NotificationFilter.UNREAD.matches(notification(1, unread = true)))
        assertFalse(NotificationFilter.UNREAD.matches(notification(2, unread = false)))
    }

    @Test
    fun githubReasonsFoldIntoTheGroupsWeShow() {
        assertEquals(NotificationReasonGroup.MENTIONS, NotificationReasonGroup.of("mention"))
        assertEquals(NotificationReasonGroup.MENTIONS, NotificationReasonGroup.of("team_mention"))
        assertEquals(NotificationReasonGroup.ASSIGNED, NotificationReasonGroup.of("assign"))
        assertEquals(NotificationReasonGroup.REVIEW, NotificationReasonGroup.of("review_requested"))
        assertEquals(NotificationReasonGroup.COMMENTS, NotificationReasonGroup.of("comment"))
        assertEquals(NotificationReasonGroup.COMMENTS, NotificationReasonGroup.of("author"))
        assertEquals(NotificationReasonGroup.COMMENTS, NotificationReasonGroup.of("subscribed"))
        assertEquals(NotificationReasonGroup.STATE, NotificationReasonGroup.of("state_change"))
        assertEquals(NotificationReasonGroup.CI, NotificationReasonGroup.of("ci_activity"))
        assertEquals(NotificationReasonGroup.SECURITY, NotificationReasonGroup.of("security_alert"))
        // Unrecognized reasons (including a missing one) fall into OTHER and must never disappear.
        assertEquals(NotificationReasonGroup.OTHER, NotificationReasonGroup.of("invitation"))
        assertEquals(NotificationReasonGroup.OTHER, NotificationReasonGroup.of("push"))
        assertEquals(NotificationReasonGroup.OTHER, NotificationReasonGroup.of(null))
    }

    @Test
    fun reasonFilterIsAnOrWithinAndAcrossGroups() {
        val mentioned = notification(1, reason = "mention")
        val reviewed = notification(2, reason = "review_requested")
        val ci = notification(3, reason = "ci_activity")

        val onlyMentions = NotificationFilterState(reasons = setOf(NotificationReasonGroup.MENTIONS))
        assertTrue(onlyMentions.matches(mentioned))
        assertFalse(onlyMentions.matches(reviewed))
        assertFalse(onlyMentions.matches(ci))

        val mentionsOrReview = NotificationFilterState(
            reasons = setOf(NotificationReasonGroup.MENTIONS, NotificationReasonGroup.REVIEW),
        )
        assertTrue(mentionsOrReview.matches(mentioned))
        assertTrue(mentionsOrReview.matches(reviewed))
        assertFalse(mentionsOrReview.matches(ci))

        // Selecting no reason means no reason filtering (not "filter everything out").
        assertTrue(NotificationFilterState().matches(ci))
    }

    @Test
    fun repoAndTypeAndReasonAllHaveToMatch() {
        val wanted = notification(1, repo = "a/one", reason = "assign")
        val otherRepo = notification(2, repo = "b/two", reason = "assign")
        val otherReason = notification(3, repo = "a/one", reason = "comment")

        val filter = NotificationFilterState(
            type = NotificationFilter.ISSUE,
            repo = "a/one",
            reasons = setOf(NotificationReasonGroup.ASSIGNED),
        )
        assertTrue(filter.matches(wanted))
        assertFalse(filter.matches(otherRepo))
        assertFalse(filter.matches(otherReason))
    }

    @Test
    fun activeCountOnlyCountsRealFilters() {
        assertEquals(0, NotificationFilterState().activeCount)
        assertEquals(1, NotificationFilterState(repo = "a/b").activeCount)
        assertEquals(1, NotificationFilterState(type = NotificationFilter.UNREAD).activeCount)
        assertEquals(
            2,
            NotificationFilterState(
                repo = "a/b",
                reasons = setOf(NotificationReasonGroup.CI),
            ).activeCount,
        )
        // Grouping is display-only and must not light up the badge.
        assertEquals(0, NotificationFilterState(group = NotificationGroup.TIME).activeCount)
    }

    @Test
    fun narrowsIgnoresTypeSoChipCountsAreHonest() {
        val issue = notification(1, subjectType = "Issue")
        val filter = NotificationFilterState(repo = "busybin/gitrehub", type = NotificationFilter.PULL)
        assertTrue(filter.narrows(issue))
        assertFalse(filter.matches(issue))
        assertFalse(NotificationFilterState(repo = "other/repo").narrows(issue))
    }

    @Test
    fun timeBucketsFollowTheCalendar() {
        assertEquals(NotificationTimeBucket.TODAY, NotificationTimeBucket.of(daysAgo(0), now))
        assertEquals(NotificationTimeBucket.YESTERDAY, NotificationTimeBucket.of(daysAgo(1), now))
        assertEquals(NotificationTimeBucket.RECENT, NotificationTimeBucket.of(daysAgo(3), now))
        assertEquals(NotificationTimeBucket.OLDER, NotificationTimeBucket.of(daysAgo(30), now))
        // A missing date counts as OLDER; do not drop it.
        assertEquals(NotificationTimeBucket.OLDER, NotificationTimeBucket.of(null, now))
    }

    @Test
    fun noGroupingKeepsOneFlatList() {
        val items = listOf(notification(1), notification(2))
        val sections = sectionNotifications(items, NotificationGroup.NONE, now)
        assertEquals(1, sections.size)
        assertEquals(items, sections.single().items)
        assertNull(sections.single().repoName)
    }

    @Test
    fun repoGroupingSortsByNameAndKeepsOrderInsideSections() {
        val items = listOf(
            notification(1, repo = "zeta/repo", updatedAt = daysAgo(0)),
            notification(2, repo = "alpha/repo", updatedAt = daysAgo(1)),
            notification(3, repo = "zeta/repo", updatedAt = daysAgo(2)),
        )
        val sections = sectionNotifications(items, NotificationGroup.REPO, now)

        assertEquals(listOf("alpha/repo", "zeta/repo"), sections.map { it.repoName })
        // Keep the incoming order inside a section (the API already returns descending).
        assertEquals(listOf("1", "3"), sections[1].items.map { it.id })
        assertEquals("repo:zeta/repo", sections[1].key)
    }

    @Test
    fun repoGroupingPutsRowsWithoutARepositoryLast() {
        val items = listOf(notification(1, repo = null), notification(2, repo = "a/b"))
        val sections = sectionNotifications(items, NotificationGroup.REPO, now)
        assertEquals(listOf("a/b", null), sections.map { it.repoName })
    }

    @Test
    fun timeGroupingDropsEmptyBucketsAndKeepsBucketOrder() {
        val items = listOf(
            notification(1, updatedAt = daysAgo(0)),
            notification(2, updatedAt = daysAgo(30)),
            notification(3, updatedAt = daysAgo(1)),
        )
        val sections = sectionNotifications(items, NotificationGroup.TIME, now)

        assertEquals(
            listOf(
                NotificationTimeBucket.TODAY,
                NotificationTimeBucket.YESTERDAY,
                NotificationTimeBucket.OLDER,
            ),
            sections.map { it.bucket },
        )
        assertEquals(listOf("1"), sections[0].items.map { it.id })
        assertEquals(listOf("3"), sections[1].items.map { it.id })
        assertEquals(listOf("2"), sections[2].items.map { it.id })
    }
}
