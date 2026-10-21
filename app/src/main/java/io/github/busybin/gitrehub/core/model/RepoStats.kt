package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One week of `stats/commit_activity`: [week] is Sunday 00:00 UTC (epoch seconds), [days] runs
 * Sunday to Saturday.
 */
@Serializable
data class WeekActivity(
    val week: Long = 0,
    val total: Int = 0,
    val days: List<Int> = emptyList(),
) {
    val weekEpochSeconds: Long get() = week
}

/** One contributor's week: commits plus additions/deletions (GitHub's `w/a/d/c`). */
@Serializable
data class WeekStat(
    val w: Long = 0,
    val a: Long = 0,
    val d: Long = 0,
    val c: Int = 0,
) {
    val weekEpochSeconds: Long get() = w
    val commits: Int get() = c
    val additions: Long get() = a
    val deletions: Long get() = d
}

/** A contributor with per-week detail (`stats/contributors`). */
@Serializable
data class ContributorStat(
    val author: StatsAuthor? = null,
    val weeks: List<WeekStat> = emptyList(),
) {
    val login: String get() = author?.login.orEmpty()
    val avatarUrl: String? get() = author?.avatarUrl
    val commits: Int get() = weeks.sumOf { it.commits }
    val additions: Long get() = weeks.sumOf { it.additions }
    val deletions: Long get() = weeks.sumOf { it.deletions }

    /** Busiest week of the year; scales the sparkline. */
    val peak: Int get() = weeks.maxOfOrNull { it.commits } ?: 0
}

/** `author` is null for commits made by deleted accounts; those rows are dropped. */
@Serializable
data class StatsAuthor(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** GitHub's `deletions` are negative; the chart takes the absolute value. */
@Serializable
data class CodeFrequency(
    val week: Long = 0,
    val additions: Long = 0,
    val deletions: Long = 0,
) {
    val weekEpochSeconds: Long get() = week
}

/** 7 days x 24 hours of commits; `counts[day * 24 + hour]`, day 0 = Sunday. */
data class PunchCard(val counts: List<Int>) {
    fun cell(day: Int, hour: Int): Int = counts.getOrElse(day * HOURS + hour) { 0 }

    val busiest: Int get() = counts.maxOrNull() ?: 0

    /** Busiest day/hour/count, for the caption under the card. */
    val peak: Triple<Int, Int, Int>?
        get() {
            val index = counts.indexOfFirst { it == busiest }
            return if (index < 0 || busiest == 0) null else Triple(index / HOURS, index % HOURS, busiest)
        }

    val total: Int get() = counts.sum()

    companion object {
        const val DAYS = 7
        const val HOURS = 24
        const val CELLS = DAYS * HOURS
    }
}

/** A stats call either returns data, or GitHub is still computing it (202). */
sealed interface StatsOutcome<out T> {
    data class Data<T>(val value: T) : StatsOutcome<T>

    /** GitHub answers 202 with an empty body while computing; that is not "no activity". */
    data object Computing : StatsOutcome<Nothing>
}

/** Sort metric of the contributor chart. */
enum class ContributorMetric { COMMITS, ADDITIONS, DELETIONS }

fun ContributorStat.metricOf(metric: ContributorMetric): Long = when (metric) {
    ContributorMetric.COMMITS -> commits.toLong()
    ContributorMetric.ADDITIONS -> additions
    ContributorMetric.DELETIONS -> deletions
}

fun WeekStat.metricOf(metric: ContributorMetric): Long = when (metric) {
    ContributorMetric.COMMITS -> commits.toLong()
    ContributorMetric.ADDITIONS -> additions
    ContributorMetric.DELETIONS -> deletions
}

// ------------------------------------------------------------------------------- parsing
// The stats endpoints answer a bare array; the legacy interceptor sometimes wrapped it as
// {"items":[…]}. Parsing stays lenient: unknown or broken rows are dropped, never thrown.

private val statsJson = Json { ignoreUnknownKeys = true }

private fun statsItems(json: String?): List<JsonElement> {
    if (json.isNullOrBlank()) return emptyList()
    val root = runCatching { statsJson.parseToJsonElement(json) }.getOrNull() ?: return emptyList()
    return when {
        root is JsonArray -> root.toList()
        root is JsonObject -> (root["items"] as? JsonArray)?.toList().orEmpty()
        else -> emptyList()
    }
}

fun parseCommitActivity(json: String?): List<WeekActivity> = statsItems(json).mapNotNull { element ->
    val obj = element as? JsonObject ?: return@mapNotNull null
    WeekActivity(
        week = obj["week"]?.intOrNull()?.toLong() ?: 0L,
        total = obj["total"]?.intOrNull() ?: 0,
        days = (obj["days"] as? JsonArray)?.map { it.intOrNull() ?: 0 }.orEmpty(),
    )
}.sortedBy { it.week }

fun parseContributors(json: String?): List<ContributorStat> = statsItems(json).mapNotNull { element ->
    val obj = element as? JsonObject ?: return@mapNotNull null
    // `"author": null` is how GitHub reports commits by deleted accounts.
    val author = obj["author"] as? JsonObject ?: return@mapNotNull null
    val login = author["login"]?.stringOrNull() ?: return@mapNotNull null
    val weeks = (obj["weeks"] as? JsonArray).orEmpty().mapNotNull { week ->
        val w = week as? JsonObject ?: return@mapNotNull null
        WeekStat(
            w = w["w"]?.longOrNull() ?: 0L,
            a = w["a"]?.longOrNull() ?: 0L,
            d = w["d"]?.longOrNull() ?: 0L,
            c = w["c"]?.intOrNull() ?: 0,
        )
    }.sortedBy { it.w }
    ContributorStat(
        author = StatsAuthor(login, author["avatar_url"]?.stringOrNull()),
        weeks = weeks,
    )
}.sortedByDescending { it.commits }

fun parseCodeFrequency(json: String?): List<CodeFrequency> = statsItems(json).mapNotNull { element ->
    val row = element as? JsonArray ?: return@mapNotNull null
    if (row.size < 3) return@mapNotNull null
    CodeFrequency(
        week = row[0].longOrNull() ?: 0L,
        additions = row[1].longOrNull() ?: 0L,
        deletions = row[2].longOrNull() ?: 0L,
    )
}.sortedBy { it.week }

fun parsePunchCard(json: String?): PunchCard {
    val counts = MutableList(PunchCard.CELLS) { 0 }
    statsItems(json).forEach { element ->
        val row = element as? JsonArray ?: return@forEach
        if (row.size < 3) return@forEach
        val day = row[0].intOrNull() ?: return@forEach
        val hour = row[1].intOrNull() ?: return@forEach
        if (day in 0 until PunchCard.DAYS && hour in 0 until PunchCard.HOURS) {
            counts[day * PunchCard.HOURS + hour] = row[2].intOrNull() ?: 0
        }
    }
    return PunchCard(counts)
}

private fun JsonElement.intOrNull(): Int? = (this as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonElement.longOrNull(): Long? = (this as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
