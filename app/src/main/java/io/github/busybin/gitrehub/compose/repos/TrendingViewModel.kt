package io.github.busybin.gitrehub.compose.repos

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.toPage
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Trending time window: repos created within the last N days. */
enum class TrendingPeriod(@StringRes val labelRes: Int, val days: Int) {
    TODAY(R.string.m3_trending_today, 1),
    WEEK(R.string.m3_trending_week, 7),
    MONTH(R.string.m3_trending_month, 30),
}

/**
 * Trending repos.
 *
 * GitHub has no public trending API, so this approximates it with search:
 * `q=created:>date stars:>N` + `sort=stars&order=desc`, i.e. "recently created, most starred".
 * Changing the period via [setPeriod] refetches page 1.
 */
class TrendingViewModel : PagedViewModel<Repo>() {

    /** `null` = every language. Language names are proper nouns, so they stay untranslated. */
    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    private val _period = MutableStateFlow(TrendingPeriod.WEEK)
    val period: StateFlow<TrendingPeriod> = _period.asStateFlow()

    fun setPeriod(period: TrendingPeriod) {
        if (_period.value == period) return
        _period.value = period
        refresh()
    }

    fun setLanguage(language: String?) {
        if (_language.value == language) return
        _language.value = language
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Repo> {
        // stars:>3 just filters out freshly created repos nobody cares about, less noise.
        val language = _language.value?.let { " language:\"$it\"" }.orEmpty()
        val query = "created:>${sinceDate(_period.value)} stars:>3$language"
        return GitHubApi.searchRepos(query, page = page, sort = "stars", order = "desc").toPage()
    }

    companion object {
        /** Languages offered in the filter; the search API matches them case-insensitively. */
        val LANGUAGES = listOf(
            "Kotlin", "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
            "C++", "C#", "Swift", "Dart", "Ruby", "PHP", "Shell",
        )

        /** GitHub's search syntax only accepts `YYYY-MM-DD` (UTC). */
        fun sinceDate(period: TrendingPeriod, now: Long = System.currentTimeMillis()): String {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
            calendar.timeInMillis = now
            calendar.add(Calendar.DAY_OF_YEAR, -period.days)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(calendar.time)
        }
    }
}
