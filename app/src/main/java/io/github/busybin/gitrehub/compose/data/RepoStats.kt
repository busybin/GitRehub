package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One language of a repository with its share of the code. */
data class LanguageSlice(val name: String, val bytes: Long, val percent: Double)

/**
 * Repository statistics that belong to the detail screen: the language breakdown and "the last
 * commit that touched this path" (the line the website puts next to every file).
 *
 * Commit activity, contributors, code frequency and punch card live in [RepoInsights].
 */
object RepoStats {

    /** Languages, largest first, percentages adding up to 100. */
    suspend fun languages(owner: String, repo: String): List<LanguageSlice> {
        val map = runCatching { GitHubApi.languages(owner, repo) }.getOrNull().orEmpty()
        val total = map.values.sum()
        if (total <= 0L) return emptyList()
        return map.entries
            .sortedByDescending { it.value }
            .map { (name, bytes) ->
                LanguageSlice(name, bytes, bytes * 100.0 / total)
            }
    }

    /** The newest commit that touched [path] (used for "last modified"). */
    suspend fun lastCommit(owner: String, repo: String, path: String, ref: String?): Commit? =
        runCatching {
            GitHubApi.commits(
                owner = owner,
                repo = repo,
                ref = ref.orEmpty(),
                page = 1,
                path = path.trimStart('/'),
            ).value.firstOrNull()
        }.getOrNull()

    /** Exact counters GitHub's repo JSON does not carry separately. */
    data class RepoCounts(
        val issues: Int = 0,
        val pulls: Int = 0,
        val branches: Int = 0,
        val tags: Int = 0,
        val contributors: Int = 0,
    )

    /**
     * Best-effort exact counts: each one is a `per_page=1` request whose last page number is the
     * total. GitHub's repo JSON only has `open_issues_count`, which mixes issues and pull requests,
     * so the issue count is derived by subtracting the (exact) open-PR count.
     */
    suspend fun counts(owner: String, repo: String, openIssuesCount: Long): RepoCounts = coroutineScope {
        val pullsDeferred = async {
            runCatching { GitHubApi.openPullsCount(owner, repo) }.getOrDefault(0)
        }
        val branchesDeferred = async {
            runCatching { GitHubApi.branchesCount(owner, repo) }.getOrDefault(0)
        }
        val tagsDeferred = async {
            runCatching { GitHubApi.tagsCount(owner, repo) }.getOrDefault(0)
        }
        val contributorsDeferred = async {
            runCatching { GitHubApi.contributorsCount(owner, repo) }.getOrDefault(0)
        }
        val pulls = pullsDeferred.await()
        RepoCounts(
            issues = (openIssuesCount.toInt() - pulls).coerceAtLeast(0),
            pulls = pulls,
            branches = branchesDeferred.await(),
            tags = tagsDeferred.await(),
            contributors = contributorsDeferred.await(),
        )
    }
}
