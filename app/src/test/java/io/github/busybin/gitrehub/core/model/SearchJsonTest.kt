package io.github.busybin.gitrehub.core.model

import io.github.busybin.gitrehub.core.net.GitHubClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search endpoints answer an envelope, not a bare array. Stage 3 decoded `/search/repositories`
 * as a list and silently broke Trending; these tests pin the envelope shape.
 */
class SearchJsonTest {

    @Test
    fun decodesTheSearchEnvelope() {
        val json = """
            {"total_count": 2, "incomplete_results": false, "items": [
              {"id": 1, "name": "halcyon", "full_name": "northwind/halcyon", "stargazers_count": 4800},
              {"id": 2, "name": "atlas-cli", "full_name": "northwind/atlas-cli"}
            ]}
        """
        val result = GitHubClient.json.decodeFromString(
            SearchResult.serializer(Repo.serializer()),
            json,
        )
        assertEquals(2, result.totalCount)
        assertFalse(result.incomplete)
        assertEquals(listOf("northwind/halcyon", "northwind/atlas-cli"), result.items.map { it.nameWithOwner })
        assertEquals(4800, result.items.first().stargazersCount)
    }

    @Test
    fun decodesACodeHitWithItsRepository() {
        val json = """
            {"total_count": 1, "incomplete_results": false, "items": [
              {"name": "lib.rs", "path": "crates/watch/src/lib.rs", "sha": "abc",
               "html_url": "https://github.com/northwind/halcyon/blob/main/crates/watch/src/lib.rs",
               "repository": {"name": "halcyon", "full_name": "northwind/halcyon", "default_branch": "main"}}
            ]}
        """
        val result = GitHubClient.json.decodeFromString(
            SearchResult.serializer(SearchCode.serializer()),
            json,
        )
        assertEquals("northwind/halcyon", result.items.single().repoFullName)
        assertEquals("main", result.items.single().repository?.defaultBranch)
    }

    @Test
    fun decodesIssueSearchRowsWithoutARepositoryObject() {
        val json = """
            {"total_count": 1, "items": [
              {"id": 9, "number": 482, "title": "Sync stalls", "state": "open",
               "repository_url": "https://api.github.com/repos/northwind/halcyon"}
            ]}
        """
        val issue = GitHubClient.json.decodeFromString(
            SearchResult.serializer(Issue.serializer()),
            json,
        ).items.single()
        assertEquals("northwind/halcyon", issue.repoFullName)
        assertTrue(issue.pullRequest == null)
    }
}
