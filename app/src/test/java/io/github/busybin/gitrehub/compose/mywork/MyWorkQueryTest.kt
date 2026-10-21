package io.github.busybin.gitrehub.compose.mywork

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Search qualifiers are sent verbatim to `search/issues`; a wrong one silently returns the wrong
 * list (or nothing), so they are pinned here. Spaces are the separators: `GitHubClient` encodes
 * them as `%20`, while a literal `+` would arrive percent-encoded and stop separating qualifiers.
 */
class MyWorkQueryTest {

    @Test
    fun everySectionBuildsItsQualifier() {
        assertEquals("type:issue assignee:octocat is:open", MyWorkSection.ASSIGNED.query("octocat"))
        assertEquals("type:pr review-requested:octocat is:open", MyWorkSection.REVIEW.query("octocat"))
        assertEquals("mentions:octocat is:open", MyWorkSection.MENTIONS.query("octocat"))
        assertEquals("author:octocat is:open", MyWorkSection.CREATED.query("octocat"))
    }
}
