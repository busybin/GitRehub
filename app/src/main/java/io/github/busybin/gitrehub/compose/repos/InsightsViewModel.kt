package io.github.busybin.gitrehub.compose.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.model.DependabotAlert
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.TrafficPath
import io.github.busybin.gitrehub.core.model.TrafficReferrer
import io.github.busybin.gitrehub.core.model.TrafficSeries
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The maintainer dashboard: traffic, referrers, popular paths, alerts and the stale backlog. */
data class InsightsData(
    val views: TrafficSeries? = null,
    val clones: TrafficSeries? = null,
    val referrers: List<TrafficReferrer> = emptyList(),
    val paths: List<TrafficPath> = emptyList(),
    val alerts: List<DependabotAlert> = emptyList(),
    /** Dependabot needs `security_events`; a token without it must not break the whole page. */
    val alertsUnavailable: Boolean = false,
    val staleIssues: List<Issue> = emptyList(),
    val staleUnavailable: Boolean = false,
)

/**
 * All read-only and only meaningful for people with push access, so the screen is gated on
 * `permissions.push`; GitHub answers 403 for the traffic endpoints otherwise.
 */
class InsightsViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<InsightsData>>(LoadState.Loading)
    val state: StateFlow<LoadState<InsightsData>> = _state.asStateFlow()

    private var key = ""

    fun load(owner: String, repo: String, days: Int = 90) {
        val newKey = "$owner/$repo/$days"
        if (newKey == key && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) { fetch(owner, repo, days) }
    }

    private suspend fun fetch(owner: String, repo: String, days: Int): InsightsData =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val views = async {
                    runCatching { GitHubApi.trafficViews(owner, repo) }.getOrNull()
                }
                val clones = async {
                    runCatching { GitHubApi.trafficClones(owner, repo) }.getOrNull()
                }
                val referrers = async {
                    runCatching { GitHubApi.trafficReferrers(owner, repo) }.getOrDefault(emptyList())
                }
                val paths = async {
                    runCatching { GitHubApi.trafficPaths(owner, repo) }.getOrDefault(emptyList())
                }
                val alerts = async {
                    runCatching { GitHubApi.dependabotAlerts(owner, repo) }
                }
                val stale = async {
                    runCatching { GitHubApi.staleIssues(owner, repo, days) }
                }
                val alertsResult = alerts.await()
                val staleResult = stale.await()
                InsightsData(
                    views = views.await(),
                    clones = clones.await(),
                    referrers = referrers.await(),
                    paths = paths.await(),
                    alerts = alertsResult.getOrDefault(emptyList()),
                    alertsUnavailable = alertsResult.isFailure,
                    staleIssues = staleResult.getOrDefault(emptyList()),
                    staleUnavailable = staleResult.isFailure,
                )
            }
        }
}
