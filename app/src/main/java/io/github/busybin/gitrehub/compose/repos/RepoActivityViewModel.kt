package io.github.busybin.gitrehub.compose.repos

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.CodeFrequency
import io.github.busybin.gitrehub.core.model.ContributorMetric
import io.github.busybin.gitrehub.core.model.ContributorStat
import io.github.busybin.gitrehub.core.model.PunchCard
import io.github.busybin.gitrehub.compose.data.RepoInsights
import io.github.busybin.gitrehub.core.model.StatsOutcome
import io.github.busybin.gitrehub.core.model.WeekActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The five tabs of the activity page (the first is the event timeline, the rest come from `stats/...`). */
enum class InsightsTab(@StringRes val labelRes: Int) {
    EVENTS(R.string.m3_activity_tab_events),
    COMMITS(R.string.m3_activity_tab_commits),
    CONTRIBUTORS(R.string.m3_activity_tab_contributors),
    FREQUENCY(R.string.m3_activity_tab_frequency),
    PUNCH_CARD(R.string.m3_activity_tab_punch_card),
}

/**
 * State of the four `stats/...` charts on the activity page.
 *
 * Each tab is fetched only when first shown (these endpoints are expensive and GitHub may answer 202
 * while computing). [StatsOutcome.Computing] is retried automatically every 5 seconds up to
 * [MAX_AUTO_RETRIES] times, as GitHub's own site does; only then is "computing" shown to the user,
 * with a manual retry.
 */
class RepoActivityViewModel : ViewModel() {

    private val _activity = MutableStateFlow<LoadState<StatsOutcome<List<WeekActivity>>>>(LoadState.Loading)
    val activity: StateFlow<LoadState<StatsOutcome<List<WeekActivity>>>> = _activity.asStateFlow()

    private val _contributors = MutableStateFlow<LoadState<StatsOutcome<List<ContributorStat>>>>(LoadState.Loading)
    val contributors: StateFlow<LoadState<StatsOutcome<List<ContributorStat>>>> = _contributors.asStateFlow()

    private val _frequency = MutableStateFlow<LoadState<StatsOutcome<List<CodeFrequency>>>>(LoadState.Loading)
    val frequency: StateFlow<LoadState<StatsOutcome<List<CodeFrequency>>>> = _frequency.asStateFlow()

    private val _punchCard = MutableStateFlow<LoadState<StatsOutcome<PunchCard>>>(LoadState.Loading)
    val punchCard: StateFlow<LoadState<StatsOutcome<PunchCard>>> = _punchCard.asStateFlow()

    /** Contributor chart metric (commits / additions / deletions): a UI choice, does not refetch. */
    private val _metric = MutableStateFlow(ContributorMetric.COMMITS)
    val metric: StateFlow<ContributorMetric> = _metric.asStateFlow()

    fun setMetric(value: ContributorMetric) {
        _metric.value = value
    }

    private var owner = ""
    private var repo = ""

    /** Tabs already fetched (cleared when the repo changes). */
    private val loaded = mutableSetOf<InsightsTab>()

    fun start(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        loaded.clear()
        _activity.value = LoadState.Loading
        _contributors.value = LoadState.Loading
        _frequency.value = LoadState.Loading
        _punchCard.value = LoadState.Loading
    }

    fun load(tab: InsightsTab, force: Boolean = false) {
        if (owner.isBlank() || repo.isBlank()) return
        if (!force && tab in loaded) return
        loaded += tab
        val currentOwner = owner
        val currentRepo = repo
        when (tab) {
            InsightsTab.COMMITS -> loadInto(tab, _activity) { RepoInsights.commitActivity(currentOwner, currentRepo) }
            InsightsTab.CONTRIBUTORS -> loadInto(tab, _contributors) { RepoInsights.contributors(currentOwner, currentRepo) }
            InsightsTab.FREQUENCY -> loadInto(tab, _frequency) { RepoInsights.codeFrequency(currentOwner, currentRepo) }
            InsightsTab.PUNCH_CARD -> loadInto(tab, _punchCard) { RepoInsights.punchCard(currentOwner, currentRepo) }
            InsightsTab.EVENTS -> Unit // The event timeline is paged and lives in RepoEventsViewModel.
        }
    }

    private fun <T> loadInto(
        tab: InsightsTab,
        flow: MutableStateFlow<LoadState<StatsOutcome<T>>>,
        fetch: suspend () -> StatsOutcome<T>,
    ) {
        // Show "loading" for both a manual retry and a first entry, so a failure is not left on screen.
        flow.value = LoadState.Loading
        viewModelScope.launch {
            var attempt = 0
            while (true) {
                val outcome = try {
                    withContext(Dispatchers.IO) { fetch() }
                } catch (t: Throwable) {
                    flow.value = LoadState.Failure(t.toUiText(), t)
                    return@launch
                }
                if (outcome is StatsOutcome.Computing && attempt < MAX_AUTO_RETRIES) {
                    attempt++
                    delay(RETRY_DELAY_MS)
                    continue
                }
                flow.value = LoadState.Ready(outcome)
                return@launch
            }
        }
    }

    private companion object {
        const val MAX_AUTO_RETRIES = 3
        const val RETRY_DELAY_MS = 5_000L
    }
}
