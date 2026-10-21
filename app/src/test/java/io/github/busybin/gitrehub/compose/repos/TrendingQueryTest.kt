package io.github.busybin.gitrehub.compose.repos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The trending query is plain string building, so its time window (`created:>YYYY-MM-DD`, UTC) is JVM-testable. */
class TrendingQueryTest {

    private fun expected(days: Int, now: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.timeInMillis = now
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(calendar.time)
    }

    @Test
    fun eachPeriodWindowsBackTheRightNumberOfDays() {
        val now = System.currentTimeMillis()
        assertEquals(expected(1, now), TrendingViewModel.sinceDate(TrendingPeriod.TODAY, now))
        assertEquals(expected(7, now), TrendingViewModel.sinceDate(TrendingPeriod.WEEK, now))
        assertEquals(expected(30, now), TrendingViewModel.sinceDate(TrendingPeriod.MONTH, now))
    }

    @Test
    fun theDateIsIsoFormattedForGithubSearch() {
        val date = TrendingViewModel.sinceDate(TrendingPeriod.WEEK, System.currentTimeMillis())
        assertEquals(true, Regex("""\d{4}-\d{2}-\d{2}""").matches(date))
    }
}
