package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventType
import io.github.busybin.gitrehub.core.model.parseInteractions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the one-shot GraphQL answer behind "who starred / forked / opened something on my repos".
 *
 * The fixture mirrors a **real** response (captured for this account, trimmed to one repo),
 * including the parts that are easy to get wrong: the viewer's own star and own PR must be dropped,
 * and GraphQL's `issues` list contains pull requests too (so #2 must not appear twice).
 */
class InteractionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String): List<Event> =
        parseInteractions(json.parseToJsonElement(raw), json)

    private val raw = """
    {
      "viewer": {
        "login": "busybin",
        "repositories": {
          "nodes": [
            {
              "nameWithOwner": "busybin/luci-app-socat-reforged",
              "stargazers": {
                "edges": [
                  { "starredAt": "2026-10-16T23:16:34Z", "node": { "login": "busybin", "avatarUrl": "https://avatars/busybin" } },
                  { "starredAt": "2026-10-15T10:00:00Z", "node": { "login": "Decklong", "avatarUrl": "https://avatars/decklong" } }
                ]
              },
              "forks": {
                "nodes": [
                  { "nameWithOwner": "ntbowen/luci-app-socat-reforged",
                    "createdAt": "2026-10-15T00:04:17Z", "owner": { "login": "ntbowen", "avatarUrl": "https://avatars/ntbowen" } }
                ]
              },
              "pullRequests": {
                "nodes": [
                  { "number": 2, "title": "i18n: add Simplified Chinese translation", "createdAt": "2026-10-15T00:09:55Z",
                    "state": "CLOSED", "merged": false, "author": { "login": "ntbowen", "avatarUrl": "https://avatars/ntbowen" } },
                  { "number": 9, "title": "my own work", "createdAt": "2026-10-14T00:00:00Z",
                    "state": "OPEN", "merged": false, "author": { "login": "busybin", "avatarUrl": "https://avatars/busybin" } }
                ]
              },
              "issues": {
                "nodes": [
                  { "number": 2, "title": "i18n: add Simplified Chinese translation", "createdAt": "2026-10-15T00:09:55Z",
                    "state": "CLOSED", "author": { "login": "ntbowen", "avatarUrl": "https://avatars/ntbowen" } },
                  { "number": 7, "title": "luci: page spacing is off", "createdAt": "2026-10-13T00:00:00Z",
                    "state": "OPEN", "author": { "login": "someone", "avatarUrl": "https://avatars/someone" } }
                ]
              }
            }
          ]
        }
      }
    }
    """.trimIndent()

    @Test
    fun someoneElseStarringMyRepoBecomesAFeedRow() {
        val star = parse(raw).single { it.type == EventType.WatchEvent }
        assertEquals("Decklong", star.actor?.login)
        assertEquals("https://avatars/decklong", star.actor?.avatarUrl)
        assertEquals("busybin/luci-app-socat-reforged", star.repo?.fullName)
        assertEquals(EventVerb.STAR, star.verb())
    }

    @Test
    fun aForkCarriesTheForkerAndMyRepo() {
        val fork = parse(raw).single { it.type == EventType.ForkEvent }
        assertEquals("ntbowen", fork.actor?.login)
        assertEquals("busybin/luci-app-socat-reforged", fork.repo?.fullName)
        assertEquals(EventVerb.FORK, fork.verb())
    }

    /** Tapping "someone forked my repo" must open the fork, not my repo. */
    @Test
    fun aForkOpensTheForkedRepositoryNotMine() {
        val fork = parse(raw).single { it.type == EventType.ForkEvent }
        assertEquals(FeedTarget.ForkedRepo("ntbowen/luci-app-socat-reforged"), fork.feedTarget())
        // The card's second line also shows the fork's repo name.
        assertEquals("ntbowen/luci-app-socat-reforged", fork.titleLine())
    }

    /**
     * The whole point: the PR author shows up, with the number and the title.
     *
     * The fixture's PR is `CLOSED` (that is the real data — this account closed ntbowen's PRs without
     * merging), and the row still reads "opened": the item is stamped at *creation* time, and
     * "who closed it" is a separate event further down the timeline.
     */
    @Test
    fun aPullRequestShowsItsAuthorNotTheOwner() {
        val pr = parse(raw).single { it.type == EventType.PullRequestEvent }
        assertEquals("ntbowen", pr.actor?.login)
        assertEquals(EventVerb.PR_OPENED, pr.verb())
        assertEquals("i18n: add Simplified Chinese translation", pr.titleLine())
    }

    @Test
    fun graphqlIssuesContainingPullRequestsDoNotDuplicateThem() {
        val issues = parse(raw).filter { it.type == EventType.IssuesEvent }
        assertEquals(listOf("#7"), issues.map { it.numberLabel() })
        assertEquals("someone", issues.single().actor?.login)
    }

    @Test
    fun myOwnActionsAreNotInteractions() {
        val logins = parse(raw).map { it.actor?.login }
        assertEquals(listOf("Decklong", "ntbowen", "ntbowen", "someone"), logins.filterNotNull().sorted())
    }

    /** Same input, same ids — read state and list keys must survive a refresh. */
    @Test
    fun syntheticIdsAreStableAndNeverCollideWithRealOnes() {
        val first = parse(raw).map { it.id }
        val second = parse(raw).map { it.id }
        assertEquals(first, second)
        assertEquals(first.size, first.toSet().size)
        assertTrue(first.all { it < 0 })
    }

    @Test
    fun anEmptyAnswerIsJustAnEmptyList() {
        assertEquals(emptyList<Any>(), parse("{}"))
        assertEquals(emptyList<Any>(), parse("""{"viewer":null}"""))
        assertEquals(emptyList<Any>(), parse("""{"viewer":{"repositories":null}}"""))
    }
}
