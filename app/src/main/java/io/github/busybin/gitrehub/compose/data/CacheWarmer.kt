package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.OfflinePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills the offline cache after sign-in with page 1 of "my repos" and "starred", so the list still
 * shows up when the user never opened that tab. Uses the list screen's exact endpoints (same
 * params) so the cache keys match.
 */
object CacheWarmer {

    suspend fun warm() = withContext(Dispatchers.IO) {
        if (!OfflinePrefs.isEnabled) return@withContext
        runCatching {
            GitHubApi.myRepos(1)
            val login = Session.currentAccount()?.login
            if (!login.isNullOrBlank()) GitHubApi.starredRepos(login, 1)
        }
    }
}
