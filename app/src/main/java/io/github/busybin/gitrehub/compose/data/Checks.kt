package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.core.net.GitHubApi

/**
 * Checks of a commit. Needed on the pull-request screen: the merge box shows whether checks pass,
 * which is half of "can I merge this?".
 */
object Checks {

    suspend fun forRef(owner: String, repo: String, ref: String): List<CheckRun> =
        GitHubApi.checkRuns(owner, repo, ref)
}
