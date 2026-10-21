package io.github.busybin.gitrehub.compose

import android.net.Uri
import io.github.busybin.gitrehub.provider.scheme.SchemeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which github.com links open *inside* GitRehub instead of the browser.
 *
 * `Uri` is an Android class, so this one cannot live on the JVM test set like the parsers do.
 */
class DeepLinkRoutingTest {

    private fun route(url: String) = SchemeParser.routeFor(Uri.parse(url))

    @Test
    fun repositoryLinksOpenTheRepository() {
        assertEquals(Routes.repo("busybin", "gitrehub"), route("https://github.com/busybin/gitrehub"))
        assertEquals(Routes.repo("busybin", "gitrehub"), route("http://www.github.com/busybin/gitrehub/"))
    }

    @Test
    fun issueAndPullRequestLinksOpenTheDiscussion() {
        assertEquals(Routes.issue("a", "b", 12), route("https://github.com/a/b/issues/12"))
        // GitHub itself writes both, and people paste both
        assertEquals(Routes.issue("a", "b", 12), route("https://github.com/a/b/pull/12"))
        assertEquals(Routes.issue("a", "b", 12), route("https://github.com/a/b/pulls/12"))
        assertEquals(Routes.issue("a", "b", 12), route("https://github.com/a/b/issues/12#issuecomment-99"))
    }

    @Test
    fun listLinksOpenTheLists() {
        assertEquals(Routes.issues("a", "b"), route("https://github.com/a/b/issues"))
        assertEquals(Routes.issues("a", "b"), route("https://github.com/a/b/pulls"))
        assertEquals(Routes.releases("a", "b"), route("https://github.com/a/b/releases"))
        assertEquals(Routes.releases("a", "b"), route("https://github.com/a/b/releases/latest"))
        assertEquals(Routes.releases("a", "b"), route("https://github.com/a/b/releases/tag/v1.2.3"))
        assertEquals(Routes.branches("a", "b", "tag"), route("https://github.com/a/b/tags"))
    }

    @Test
    fun codeAndFileLinksOpenTheBrowserInApp() {
        assertEquals(Routes.file("a", "b", "src/Main.kt", "main"), route("https://github.com/a/b/blob/main/src/Main.kt"))
        assertEquals(Routes.code("a", "b", "dev", "src"), route("https://github.com/a/b/tree/dev/src"))
        assertEquals(Routes.code("a", "b", "main"), route("https://github.com/a/b/tree/main"))
    }

    @Test
    fun notificationsAndFilteredIssueListsAreRecognised() {
        assertEquals(Routes.NOTIFICATIONS, route("https://github.com/notifications"))
        assertEquals(Routes.issues("a", "b"), route("https://github.com/a/b/labels/bug"))
        assertEquals(Routes.issues("a", "b"), route("https://github.com/a/b/milestone/3"))
    }

    /** Prefer the browser to opening a screen that would show the wrong thing. */
    @Test
    fun discussionsAndActionsLinksOpenTheirNativeScreens() {
        assertEquals(Routes.discussions("a", "b"), route("https://github.com/a/b/discussions"))
        assertEquals(Routes.discussion("a", "b", 12), route("https://github.com/a/b/discussions/12"))
        assertEquals(Routes.actions("a", "b"), route("https://github.com/a/b/actions"))
        // Deeper pipeline links also land on the actions list (no native run-detail screen).
        assertEquals(Routes.actions("a", "b"), route("https://github.com/a/b/actions/runs/123"))
        assertEquals(Routes.actions("a", "b"), route("https://github.com/a/b/actions/workflows/ci.yml"))
    }

    @Test
    fun linksWithoutANativeScreenAreLeftToTheBrowser() {
        assertNull(route("https://github.com/a/b/wiki"))
        assertNull(route("https://github.com/a/b/security"))
        assertNull(route("https://gist.github.com/someone/abc123"))
        assertNull(route("https://example.com/a/b"))
    }

    /** GitHub's own site pages must not parse as owner/repo (`/settings/profile` once became repo/settings/profile). */
    @Test
    fun gitHubsOwnSitePagesAreNotMistakenForRepositories() {
        assertNull(route("https://github.com/settings/profile"))
        assertNull(route("https://github.com/settings/appearance"))
        assertNull(route("https://github.com/explore"))
        assertNull(route("https://github.com/marketplace"))
        assertNull(route("https://github.com/sponsors/someone"))
        assertNull(route("https://github.com/topics/kotlin"))
        assertNull(route("https://github.com/search?q=gitrehub"))
        assertNull(route("https://github.com/orgs/someone/repositories"))
    }
}
