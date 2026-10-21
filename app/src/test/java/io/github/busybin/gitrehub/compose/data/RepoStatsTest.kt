package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.compose.data.RepoInsights.parseActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure-function tests for parsing commit activity / the contribution calendar (strings only, no network). */
class RepoStatsTest {

    @Test
    fun `parses plain array`() {
        val json = """[{"week":100,"total":3,"days":[0,1,1,0,1,0,0]},
                        {"week":200,"total":0,"days":[0,0,0,0,0,0,0]}]"""
        val weeks = parseActivity(json)
        assertEquals(2, weeks.size)
        assertEquals(100L, weeks[0].weekEpochSeconds)
        assertEquals(3, weeks[0].total)
        assertEquals(listOf(0, 1, 1, 0, 1, 0, 0), weeks[0].days)
        assertEquals(0, weeks[1].total)
    }

    @Test
    fun `parses interceptor-wrapped object and sorts by week`() {
        val json = """{"next":"2","items":[{"week":200,"total":1,"days":[]},
                                             {"week":100,"total":5,"days":[]}]}"""
        val weeks = parseActivity(json)
        assertEquals(2, weeks.size)
        assertEquals(100L, weeks[0].weekEpochSeconds)
        assertEquals(5, weeks[0].total)
        assertEquals(200L, weeks[1].weekEpochSeconds)
    }

    @Test
    fun `bad input yields nothing instead of throwing`() {
        assertTrue(parseActivity(null).isEmpty())
        assertTrue(parseActivity("").isEmpty())
        assertTrue(parseActivity("{}").isEmpty())
        assertTrue(parseActivity("not json").isEmpty())
    }

    /** Contribution disk cache: encode/decode must round-trip exactly (the UI draws cache first, then refreshes). */
    @Test
    fun `contribution cache round trips`() {
        val original = Contributions(
            total = 42,
            days = listOf(
                ContributionDay(LocalDate.parse("2026-01-01"), 0),
                ContributionDay(LocalDate.parse("2026-01-02"), 4),
                ContributionDay(LocalDate.parse("2026-12-31"), 2),
            ),
        )
        val decoded = ContributionsRepository.decode(ContributionsRepository.encode(1234L, original))
        assertEquals(1234L, decoded?.at)
        assertEquals(42, decoded?.data?.total)
        assertEquals(original.days, decoded?.data?.days)
        // Decode sorts by date ascending (the input is already ascending; this guards against shuffling)
        assertEquals("2026-01-02", ContributionsRepository.decode(
            ContributionsRepository.encode(1L, original.copy(days = original.days.reversed()))
        )?.data?.days?.get(1)?.date?.toString())
    }

    @Test
    fun `bad or missing contribution cache yields null`() {
        assertEquals(null, ContributionsRepository.decode(null))
        assertEquals(null, ContributionsRepository.decode(""))
        assertEquals(null, ContributionsRepository.decode("garbage"))
        assertEquals(null, ContributionsRepository.decode("1\n2\n"))            // no cells at all
        assertEquals(null, ContributionsRepository.decode("1\n2\nnotadate:3"))  // bad date → no cells
        // An out-of-range level keeps the data, clamped to 0..4
        assertEquals(4, ContributionsRepository.decode("1\n2\n2026-01-01:9")?.data?.days?.first()?.level)
    }
}
