package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.PunchCard
import io.github.busybin.gitrehub.compose.data.RepoInsights.parseCodeFrequency
import io.github.busybin.gitrehub.compose.data.RepoInsights.parseContributors
import io.github.busybin.gitrehub.compose.data.RepoInsights.parsePunchCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for parsing the `stats/...` charts (contributors / code frequency / punch card).
 *
 * These endpoints return bare arrays, but the app's `PaginationInterceptor` wraps arrays as
 * `{"items":[…]}` (same as `RepoStatsTest` for commit activity), so both shapes must parse.
 */
class RepoInsightsTest {

    @Test
    fun `parses contributors with weekly stats`() {
        val json = """
            {"items":[
              {"total":2,"author":{"login":"bob","avatar_url":"https://x/b.png"},
               "weeks":[{"w":200,"a":10,"d":2,"c":1},{"w":100,"a":30,"d":0,"c":5}]},
              {"author":{"login":"ann"},"weeks":[{"w":100,"a":1,"d":1,"c":7}]}
            ]}
        """.trimIndent()
        val contributors = parseContributors(json)
        assertEquals(2, contributors.size)
        // Default sort is commits descending: ann 7 > bob 6
        assertEquals("ann", contributors[0].login)
        assertEquals(7, contributors[0].commits)
        val bob = contributors[1]
        assertEquals("bob", bob.login)
        assertEquals("https://x/b.png", bob.avatarUrl)
        assertEquals(6, bob.commits)
        assertEquals(40L, bob.additions)
        assertEquals(2L, bob.deletions)
        assertEquals(5, bob.peak)
        // Weeks ascending by time
        assertEquals(100L, bob.weeks[0].weekEpochSeconds)
        assertEquals(200L, bob.weeks[1].weekEpochSeconds)
    }

    @Test
    fun `contributor without login is skipped`() {
        val json = """[{"weeks":[]},{"author":{"login":"ok"},"weeks":[]}]"""
        val contributors = parseContributors(json)
        assertEquals(1, contributors.size)
        assertEquals("ok", contributors[0].login)
        assertEquals(0, contributors[0].commits)
    }

    @Test
    fun `contributor with null author is skipped instead of crashing`() {
        // GitHub emits "author": null for commits made by deleted accounts. Asking Gson for an
        // object there used to throw "JsonNull cannot be cast to JsonObject" and killed the tab.
        val json = """[{"author":null,"weeks":[{"w":100,"a":1,"d":1,"c":2}]},{"author":{"login":"ok"},"weeks":[]}]"""
        val contributors = parseContributors(json)
        assertEquals(1, contributors.size)
        assertEquals("ok", contributors[0].login)
    }

    @Test
    fun `parses plain contributor array and bad input`() {
        assertTrue(parseContributors(null).isEmpty())
        assertTrue(parseContributors("not json").isEmpty())
        assertTrue(parseContributors("[]").isEmpty())
    }

    @Test
    fun `parses code frequency rows`() {
        val json = """[[300,100,-20],[100,5,0],[200,7,-3]]"""
        val weeks = parseCodeFrequency(json)
        assertEquals(3, weeks.size)
        assertEquals(100L, weeks[0].weekEpochSeconds)
        assertEquals(5L, weeks[0].additions)
        assertEquals(0L, weeks[0].deletions)
        // GitHub reports deletions as negative; keep them as-is
        assertEquals(-20L, weeks[2].deletions)
    }

    @Test
    fun `code frequency ignores short rows`() {
        val weeks = parseCodeFrequency("""[[1,2],[3,4,-5]]""")
        assertEquals(1, weeks.size)
        assertEquals(3L, weeks[0].weekEpochSeconds)
    }

    @Test
    fun `parses punch card into a 7x24 grid`() {
        val json = """{"items":[[3,15,9],[0,0,2],[6,23,4]]}"""
        val card = parsePunchCard(json)
        assertEquals(PunchCard.CELLS, card.counts.size)
        assertEquals(9, card.cell(3, 15))
        assertEquals(2, card.cell(0, 0))
        assertEquals(4, card.cell(6, 23))
        assertEquals(0, card.cell(1, 1))
        assertEquals(15, card.total)
        val peak = card.peak
        assertEquals(3, peak?.first)
        assertEquals(15, peak?.second)
        assertEquals(9, peak?.third)
    }

    @Test
    fun `punch card with no commits has no peak`() {
        val card = parsePunchCard("[]")
        assertEquals(0, card.total)
        assertNull(card.peak)
        // Out-of-range cells must not crash either
        assertEquals(0, card.cell(9, 99))
    }
}
