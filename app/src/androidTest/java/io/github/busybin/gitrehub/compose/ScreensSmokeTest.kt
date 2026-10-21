package io.github.busybin.gitrehub.compose

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.feed.FeedFilterAction
import io.github.busybin.gitrehub.compose.feed.FeedFilterSheet
import io.github.busybin.gitrehub.compose.accounts.AccountsScreen
import io.github.busybin.gitrehub.compose.actions.ActionsScreen
import io.github.busybin.gitrehub.compose.auth.LoginMode
import io.github.busybin.gitrehub.compose.auth.LoginScreen
import io.github.busybin.gitrehub.compose.code.BranchesScreen
import io.github.busybin.gitrehub.compose.code.CodeBrowserScreen
import io.github.busybin.gitrehub.compose.code.FileViewerScreen
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.discussions.DiscussionDetailScreen
import io.github.busybin.gitrehub.compose.discussions.DiscussionsScreen
import io.github.busybin.gitrehub.compose.feed.FeedScreen
import io.github.busybin.gitrehub.compose.issues.IssueDetailScreen
import io.github.busybin.gitrehub.compose.issues.IssueKind
import io.github.busybin.gitrehub.compose.issues.IssuesScreen
import io.github.busybin.gitrehub.compose.issues.PullRequestFilesScreen
import io.github.busybin.gitrehub.compose.mywork.MyWorkScreen
import io.github.busybin.gitrehub.compose.notifications.NotificationsScreen
import io.github.busybin.gitrehub.compose.repos.ForksScreen
import io.github.busybin.gitrehub.compose.repos.InsightsTab
import io.github.busybin.gitrehub.compose.repos.RepoActivityScreen
import io.github.busybin.gitrehub.compose.repos.RepoDetailScreen
import io.github.busybin.gitrehub.compose.repos.ReposScreen
import io.github.busybin.gitrehub.compose.repos.StargazersScreen
import io.github.busybin.gitrehub.compose.repos.TrendingScreen
import io.github.busybin.gitrehub.compose.search.SearchScreen
import io.github.busybin.gitrehub.compose.settings.SettingsScreen
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.compose.theme.ThemeId
import io.github.busybin.gitrehub.compose.theme.ThemeState
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke tests for the modern UI.
 *
 * They do not assert business results (most screens need a GitHub token), they assert that every
 * screen *composes and renders* without blowing up — the cheapest possible safety net for a UI
 * rewrite that is being written without a device at hand.
 */
@RunWith(AndroidJUnit4::class)
class ScreensSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    /** Read strings by resource for the current language, so assertions do not hardcode wording. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun loginScreenRenders() {
        rule.setContent { FaTheme { LoginScreen(onLoggedIn = {}) } }
        rule.onNodeWithText("GitRehub").assertIsDisplayed()
        // Login is two-step: the first screen picks device code / token, so it shows "login method".
        rule.onNodeWithText(str(R.string.m3_login_method_pat)).assertIsDisplayed()
    }

    @Test
    fun settingsScreenRenders() {
        rule.setContent { FaTheme { SettingsScreen() } }
        rule.onNodeWithText(str(R.string.m3_settings_appearance)).assertIsDisplayed()
        // Dark is locked, so the "theme mode / dark only" blocks were removed; appearance starts with "language".
        rule.onNodeWithText(str(R.string.m3_language)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.m3_color_theme)).assertExists()
        // density + feed filtering settings (below the fold: existence is enough here)
        rule.onNodeWithText(str(R.string.m3_interface_density)).assertExists()
        rule.onNodeWithText(str(R.string.m3_section_feed_filtering)).assertExists()
    }

    @Test
    fun compactDensityRendersFeed() {
        rule.mainClock.autoAdvance = false
        ThemeState.setCompact(true)
        rule.setContent { FaTheme { FeedScreen(onOpenRepo = {}, onOpenUser = {}) } }
        rule.waitForIdle()
        ThemeState.setCompact(false)
    }

    @Test
    fun compactDensityRendersSettingsToggle() {
        ThemeState.setCompact(true)
        rule.setContent { FaTheme { SettingsScreen() } }
        rule.onNodeWithText(str(R.string.m3_interface_density)).assertExists()
        ThemeState.setCompact(false)
    }

    @Test
    fun markdownRendersWithoutCrashing() {
        // Chinese sample: also exercises CJK line breaking and inline markup.
        rule.setContent {
            FaTheme {
                MarkdownText(
                    """
                    # 标题

                    一些 **加粗** 和 `代码`，以及一个链接 [GitHub](https://github.com)。

                    - 项目一
                    - 项目二
                    """.trimIndent()
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun feedScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { FeedScreen(onOpenRepo = {}, onOpenUser = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun reposScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { ReposScreen(onOpenRepo = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun trendingScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { TrendingScreen(onOpenRepo = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun notificationsScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { NotificationsScreen() } }
        rule.waitForIdle()
    }

    /**
     * The funnel button opens the filter sheet. The sheet holds a `ModalBottomSheet`, which cannot
     * live in the Scaffold topBar (a SubcomposeLayout) or it crashes.
     */
    @Test
    fun notificationsFilterSheetOpens() {
        rule.setContent { FaTheme { NotificationsScreen() } }
        rule.onNodeWithContentDescription(str(R.string.m3_filter_desc)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.m3_notif_filter_title)).assertIsDisplayed()
    }

    @Test
    fun myWorkScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { MyWorkScreen(onOpenIssue = { _, _, _ -> }) } }
        rule.waitForIdle()
    }

    @Test
    fun pullRequestFilesScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { PullRequestFilesScreen("busybin", "gitrehub", 1) } }
        rule.waitForIdle()
    }

    @Test
    fun searchScreenComposes() {
        rule.setContent { FaTheme { SearchScreen(onOpenRepo = {}) } }
        rule.onNodeWithText(str(R.string.m3_search_placeholder)).assertIsDisplayed()
    }

    @Test
    fun repoDetailScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { RepoDetailScreen(owner = "octocat", name = "Hello-World") } }
        rule.waitForIdle()
    }

    @Test
    fun issuesScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { IssuesScreen(owner = "octocat", repo = "Hello-World", onOpenIssue = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun pullsKindIssuesScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                IssuesScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    kind = IssueKind.PULL,
                    onOpenIssue = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun repoActivityScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { RepoActivityScreen(owner = "octocat", repo = "Hello-World") } }
        rule.waitForIdle()
    }

    /** The four `stats/...` charts each mount (no token → failure state; not crashing is enough). */
    @Test
    fun repoActivityCommitTabComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                RepoActivityScreen(owner = "octocat", repo = "Hello-World", initialTab = InsightsTab.COMMITS)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun repoActivityContributorsTabComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                RepoActivityScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    initialTab = InsightsTab.CONTRIBUTORS,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun repoActivityFrequencyTabComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                RepoActivityScreen(owner = "octocat", repo = "Hello-World", initialTab = InsightsTab.FREQUENCY)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun repoActivityPunchCardTabComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                RepoActivityScreen(owner = "octocat", repo = "Hello-World", initialTab = InsightsTab.PUNCH_CARD)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun stargazersScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { StargazersScreen(owner = "octocat", repo = "Hello-World", onOpenUser = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun forksScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { ForksScreen(owner = "octocat", repo = "Hello-World", onOpenRepo = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun discussionsScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { DiscussionsScreen(owner = "octocat", repo = "Hello-World") }
        }
        rule.waitForIdle()
    }

    @Test
    fun discussionDetailScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { DiscussionDetailScreen(owner = "octocat", repo = "Hello-World", number = 1) }
        }
        rule.waitForIdle()
    }

    @Test
    fun actionsScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { ActionsScreen(owner = "octocat", repo = "Hello-World") }
        }
        rule.waitForIdle()
    }

    @Test
    fun issueDetailScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { IssueDetailScreen(owner = "octocat", repo = "Hello-World", number = 1) }
        }
        rule.waitForIdle()
    }

    @Test
    fun branchesScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { BranchesScreen(owner = "octocat", repo = "Hello-World") }
        }
        rule.onNodeWithText(str(R.string.m3_branch)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.m3_tag)).assertIsDisplayed()
    }

    @Test
    fun codeBrowserScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme { CodeBrowserScreen(owner = "octocat", repo = "Hello-World") }
        }
        rule.waitForIdle()
    }

    @Test
    fun fileViewerScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FaTheme {
                FileViewerScreen(owner = "octocat", repo = "Hello-World", path = "README.md")
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun accountsScreenComposes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FaTheme { AccountsScreen(onAddAccount = {}, onSwitched = {}) } }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.m3_title_add_account)).assertExists()
    }

    @Test
    fun addAccountModeHasNoOAuthButton() {
        rule.setContent {
            FaTheme { LoginScreen(onLoggedIn = {}, mode = LoginMode.ADD_ACCOUNT) }
        }
        rule.onNodeWithText(str(R.string.m3_title_add_account)).assertIsDisplayed()
        // the browser round trip ends in its own activity, which nothing here would navigate away
        // from — adding an account is token-only on purpose
        rule.onNodeWithText(str(R.string.m3_login_device_add)).assertDoesNotExist()
    }

    /**
     * The filter button must ask its host to open the sheet, and the sheet must render. The sheet used
     * to live in the Scaffold topBar (SubcomposeLayout) and crashed when tapped.
     */
    @Test
    fun feedFilterButtonAsksTheHostToOpenTheSheet() {
        var opened = false
        rule.setContent { FaTheme { FeedFilterAction(onOpen = { opened = true }) } }
        rule.onNodeWithContentDescription(str(R.string.m3_filter_desc)).performClick()
        assertTrue("tapping the funnel did not open the sheet", opened)
    }

    @Test
    fun feedFilterSheetRenders() {
        rule.setContent { FaTheme { FeedFilterSheet(onDismiss = {}) } }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.m3_filter_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.m3_filter_clear)).assertExists()
    }

    @Test
    fun themeSwitchRendersSettings() {
        rule.setContent { FaTheme { SettingsScreen() } }
        rule.waitForIdle()
        // Switch color theme (dark stays locked, only the palette changes) to exercise the theme list.
        ThemeState.setTheme(ThemeId.ONE_DARK)
        rule.waitForIdle()
        ThemeState.setTheme(ThemeId.default)
        rule.waitForIdle()
    }

    @Test
    fun darkThemeRendersLogin() {
        rule.setContent { FaTheme { LoginScreen(onLoggedIn = {}) } }
        rule.onNodeWithText(str(R.string.m3_login_method_pat)).assertIsDisplayed()
    }
}

/**
 * Verifies the real entry point: the Compose activity starts, hosts the navigation graph and stays
 * in the foreground.
 */
@RunWith(AndroidJUnit4::class)
class MainComposeActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainComposeActivity>()

    @Before
    fun freezeClock() {
        // screens spin an indeterminate progress indicator while loading; keep the test clock
        // manual so waitForIdle() does not block on the looping animation
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun activityStartsAndShowsSomething() {
        rule.waitForIdle()
        // Either the login screen or the activity feed, depending on whether a token is stored.
        val loggedIn = io.github.busybin.gitrehub.core.auth.Session.isLoggedIn
        assertTrue("activity must be resumed", rule.activity.isFinishing.not())
        if (!loggedIn) {
            rule.onNodeWithText(
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.m3_login_method_pat)
            ).assertIsDisplayed()
        }
    }
}
