package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.CodeFrequency
import io.github.busybin.gitrehub.core.model.ContributorStat
import io.github.busybin.gitrehub.core.model.PunchCard
import io.github.busybin.gitrehub.core.model.StatsOutcome
import io.github.busybin.gitrehub.core.model.WeekActivity
import io.github.busybin.gitrehub.core.net.GitHubApi

/**
 * Repository Insights charts (GitHub's `stats/...` endpoints).
 *
 * A first request for a repository is answered with 202 and an empty body while GitHub computes,
 * hence [StatsOutcome]. GitHub keeps one year of stats; `stats/contributors` stops at the top 100.
 * The pure parsers live in `core/model/RepoStats.kt` so tests can exercise them without network.
 */
object RepoInsights {

    suspend fun commitActivity(owner: String, repo: String): StatsOutcome<List<WeekActivity>> =
        GitHubApi.commitActivity(owner, repo)

    suspend fun contributors(owner: String, repo: String): StatsOutcome<List<ContributorStat>> =
        GitHubApi.contributorsStats(owner, repo)

    suspend fun codeFrequency(owner: String, repo: String): StatsOutcome<List<CodeFrequency>> =
        GitHubApi.codeFrequency(owner, repo)

    suspend fun punchCard(owner: String, repo: String): StatsOutcome<PunchCard> =
        GitHubApi.punchCard(owner, repo)

    /** Pure: `[{week,total,days:[…]}]` or `{"items":[…]}` to weeks, oldest first. */
    fun parseActivity(json: String?): List<WeekActivity> =
        io.github.busybin.gitrehub.core.model.parseCommitActivity(json)

    /** Pure: `[{author:{login,avatar_url}, weeks:[{w,a,d,c}]}]` or `{"items":[…]}`. */
    fun parseContributors(json: String?): List<ContributorStat> =
        io.github.busybin.gitrehub.core.model.parseContributors(json)

    /** Pure: `[[week, additions, deletions], …]` or `{"items":[[…]]}`. */
    fun parseCodeFrequency(json: String?): List<CodeFrequency> =
        io.github.busybin.gitrehub.core.model.parseCodeFrequency(json)

    /** Pure: `[[day, hour, count], …]` or `{"items":[[…]]}` to a 7x24 grid. */
    fun parsePunchCard(json: String?): PunchCard =
        io.github.busybin.gitrehub.core.model.parsePunchCard(json)
}
