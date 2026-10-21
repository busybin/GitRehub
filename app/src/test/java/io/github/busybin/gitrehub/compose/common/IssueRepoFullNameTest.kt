package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.PullRequestRef
import io.github.busybin.gitrehub.core.model.repoFullNameFromUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `search/issues` rows carry no `repository` object; without a fallback the "My work" rows had no
 * owner/repo and taps did nothing.
 */
class IssueRepoFullNameTest {

    @Test
    fun readsOwnerAndRepoFromTheApiUrl() {
        val issue = Issue(url = "https://api.github.com/repos/octocat/Hello-World/issues/42")
        assertEquals("octocat/Hello-World", issue.repoFullName)
    }

    @Test
    fun readsOwnerAndRepoFromTheHtmlUrl() {
        val issue = Issue(htmlUrl = "https://github.com/octocat/Hello-World/pull/7")
        assertEquals("octocat/Hello-World", issue.repoFullName)
    }

    @Test
    fun buildsAPullRequestThreadKey() {
        val issue = Issue(
            url = "https://api.github.com/repos/octocat/Hello-World/issues/7",
            number = 7,
            pullRequest = PullRequestRef(),
        )
        assertEquals("octocat/Hello-World#p7", ThreadKey.ofIssue(issue))
    }

    @Test
    fun unknownUrlsYieldNothing() {
        assertNull(repoFullNameFromUrl("https://example.com/octocat/Hello-World"))
        assertNull(repoFullNameFromUrl(null))
        assertNull(Issue().repoFullName)
    }
}
