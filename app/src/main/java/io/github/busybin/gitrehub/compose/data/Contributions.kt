package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.ContributionCalendar
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One square of the contribution graph. */
data class ContributionDay(val date: LocalDate, val level: Int)

/** A calendar year's worth of contribution squares, GitHub style (level 0 = none, 4 = busiest). */
data class Contributions(val total: Int, val days: List<ContributionDay>)

/** The copy on disk plus when it was fetched (used to decide whether it is still fresh). */
data class CachedContributions(val at: Long, val data: Contributions)

/**
 * The contributions calendar, now read from GraphQL's `contributionsCollection`.
 *
 * The legacy implementation scraped `github.com/users/{login}/contributions` with Jsoup: no REST
 * endpoint exists, and the HTML page was both slow to serve and easy to break. The GraphQL field
 * needs a token (which the signed-in app always has) and returns the very numbers the website
 * draws, including the `contributionsCollection` levels.
 *
 * The fetched result is written to disk per login and drawn before the next refresh, so opening "my
 * profile" never starts with a spinner; within [FRESH_MILLIS] the fetch is skipped entirely.
 * The stored text format ([encode]/[decode]) is unchanged, so existing caches still load.
 */
object ContributionsRepository {

    private const val KEY_PREFIX = "m3_contributions_"

    const val FRESH_MILLIS = 10 * 60 * 1000L

    fun cacheKey(login: String) = KEY_PREFIX + login.lowercase()

    /** The copy on disk (possibly a year old). Synchronous: the first frame can already draw it. */
    fun cached(login: String?): CachedContributions? {
        if (login.isNullOrBlank()) return null
        return decode(runCatching { PrefHelper.getString(cacheKey(login)) }.getOrNull())
    }

    suspend fun load(login: String): Contributions? = withContext(Dispatchers.IO) {
        if (login.isBlank()) return@withContext null
        val today = LocalDate.now()
        val from = "${today.minusYears(1)}T00:00:00Z"
        val to = "${today}T23:59:59Z"
        val calendar = runCatching { GitHubApi.contributions(login, from, to) }.getOrNull()
            ?: return@withContext null
        toContributions(calendar)?.also { persist(login, it) }
    }

    /** Pure mapping, unit-tested: GraphQL days → the compact squares the UI paints. */
    fun toContributions(calendar: ContributionCalendar): Contributions? {
        val days = calendar.weeks
            .flatMap { it.contributionDays }
            .mapNotNull { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@mapNotNull null
                ContributionDay(date, levelOf(day.contributionLevel))
            }
            .sortedBy { it.date }
        if (days.isEmpty()) return null
        return Contributions(calendar.totalContributions, days)
    }

    private fun levelOf(level: String): Int = when (level) {
        "FIRST_QUARTILE" -> 1
        "SECOND_QUARTILE" -> 2
        "THIRD_QUARTILE" -> 3
        "FOURTH_QUARTILE" -> 4
        else -> 0
    }

    private fun persist(login: String, data: Contributions) {
        runCatching { PrefHelper.putAny(cacheKey(login), encode(System.currentTimeMillis(), data)) }
    }

    /** `at\n total \n date:level,date:level,…` — compact, and a pure function so JVM tests cover it. */
    fun encode(at: Long, data: Contributions): String = buildString {
        append(at).append('\n')
        append(data.total).append('\n')
        data.days.forEachIndexed { index, day ->
            if (index > 0) append(',')
            append(day.date).append(':').append(day.level)
        }
    }

    fun decode(raw: String?): CachedContributions? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.split('\n')
        if (lines.size < 3) return null
        val at = lines[0].trim().toLongOrNull() ?: return null
        val total = lines[1].trim().toIntOrNull() ?: return null
        val days = lines[2].split(',')
            .mapNotNull { entry ->
                val date = entry.substringBefore(':').trim()
                val level = entry.substringAfter(':', "").trim().toIntOrNull()
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsed == null || level == null) null
                else ContributionDay(parsed, level.coerceIn(0, 4))
            }
        if (days.isEmpty()) return null
        return CachedContributions(at, Contributions(total, days.sortedBy { it.date }))
    }
}
