package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.ContributionCalendar
import io.github.busybin.gitrehub.core.model.ContributionDay
import io.github.busybin.gitrehub.core.model.ContributionWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The GraphQL calendar → UI squares mapping, plus the on-disk format that must keep working. */
class ContributionsMappingTest {

    @Test
    fun `levels map to the four shades and days stay sorted`() {
        val calendar = ContributionCalendar(
            totalContributions = 101,
            weeks = listOf(
                ContributionWeek(
                    listOf(
                        ContributionDay("2026-10-18", 0, "NONE"),
                        ContributionDay("2026-10-19", 2, "FIRST_QUARTILE"),
                        ContributionDay("2026-10-17", 9, "FOURTH_QUARTILE"),
                    )
                )
            ),
        )
        val data = ContributionsRepository.toContributions(calendar)
        assertEquals(101, data?.total)
        assertEquals(listOf(4, 0, 1), data?.days?.map { it.level })
        assertTrue(data!!.days.zipWithNext().all { (a, b) -> a.date < b.date })
    }

    @Test
    fun `an empty calendar is not cached`() {
        assertNull(ContributionsRepository.toContributions(ContributionCalendar()))
    }
}
