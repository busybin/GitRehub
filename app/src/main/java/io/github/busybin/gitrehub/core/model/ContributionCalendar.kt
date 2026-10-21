package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.Serializable

/**
 * The contribution calendar of one account, as GraphQL's `contributionsCollection` returns it.
 *
 * There is no REST endpoint for this, and scraping `github.com/users/{login}/contributions` (what
 * the legacy code did) meant Jsoup + an unstable HTML page for a number GitHub is happy to serve.
 */
@Serializable
data class ContributionCalendar(
    val totalContributions: Int = 0,
    val weeks: List<ContributionWeek> = emptyList(),
)

@Serializable
data class ContributionWeek(val contributionDays: List<ContributionDay> = emptyList())

@Serializable
data class ContributionDay(
    val date: String,
    val contributionCount: Int = 0,
    /** `NONE`, `FIRST_QUARTILE`, `SECOND_QUARTILE`, `THIRD_QUARTILE`, `FOURTH_QUARTILE`. */
    val contributionLevel: String = "NONE",
)
