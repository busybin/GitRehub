package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.model.Event
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Where does tapping a feed item go?"
 *
 * The JSON below is copied from the real event stream: the `repo` mini object has no `full_name`,
 * only `name` whose value is `owner/repo`. Reading only `repo.fullName` gives null and the card
 * stops navigating. These tests pin the whole deserialize to target chain.
 */
class FeedTargetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun event(raw: String): Event = json.decodeFromString(Event.serializer(), raw)

    private val push = """
        {"id":"24220601523","type":"PushEvent",
         "actor":{"login":"busybin","avatar_url":"https://avatars.githubusercontent.com/u/65460344?"},
         "repo":{"id":1413324684,"name":"busybin/gitrehub"},
         "payload":{"ref":"refs/heads/main"},
         "public":true,"created_at":"2026-10-15T20:38:53Z"}
    """.trimIndent()

    private val issueComment = """
        {"id":"1","type":"IssueCommentEvent","actor":{"login":"busybin"},
         "repo":{"id":1,"name":"busybin/gitrehub"},
         "payload":{"action":"created","issue":{"number":2,"title":"热度图太慢"}},
         "public":true,"created_at":"2026-10-18T02:00:00Z"}
    """.trimIndent()

    private val pullRequest = """
        {"id":"2","type":"PullRequestEvent","actor":{"login":"busybin"},
         "repo":{"id":1,"name":"busybin/gitrehub"},
         "payload":{"action":"closed","number":2,"pull_request":{"number":2}},
         "public":true,"created_at":"2026-10-18T03:00:00Z"}
    """.trimIndent()

    private val release = """
        {"id":"3","type":"ReleaseEvent","actor":{"login":"busybin"},
         "repo":{"id":1,"name":"busybin/gitrehub"},
         "payload":{"action":"published","release":{"id":99,"tag_name":"1"}},
         "public":true,"created_at":"2026-10-18T04:00:00Z"}
    """.trimIndent()

    private val fork = """
        {"id":"4","type":"ForkEvent","actor":{"login":"someone"},
         "repo":{"id":1,"name":"busybin/gitrehub"},
         "payload":{"forkee":{"id":7,"name":"gitrehub","full_name":"someone/gitrehub"}},
         "public":true,"created_at":"2026-10-18T05:00:00Z"}
    """.trimIndent()

    private val follow = """
        {"id":"5","type":"FollowEvent","actor":{"login":"someone"},
         "payload":{},"public":true,"created_at":"2026-10-18T06:00:00Z"}
    """.trimIndent()

    private val starBySomeoneElse = """
        {"id":"6","type":"WatchEvent","actor":{"login":"someone"},
         "repo":{"id":1,"name":"busybin/gitrehub"},
         "payload":{"action":"started"},"public":true,"created_at":"2026-10-18T07:00:00Z"}
    """.trimIndent()

    @Test
    fun eventStreamRepoHasNoFullNameSoNameIsTheFullName() {
        val e = event(push)
        assertNull("the event-stream repo has no full_name", e.repo?.fullName)
        assertEquals("busybin/gitrehub", e.repoFullName)
        assertEquals("busybin", e.repoOwner)
    }

    @Test
    fun pushOpensTheRepository() {
        assertEquals(FeedTarget.Repo("busybin/gitrehub"), event(push).feedTarget())
    }

    @Test
    fun issueAndPullRequestGoToTheirDetailPage() {
        assertEquals(FeedTarget.Issue("busybin", "gitrehub", 2), event(issueComment).feedTarget())
        assertEquals(FeedTarget.Issue("busybin", "gitrehub", 2), event(pullRequest).feedTarget())
    }

    @Test
    fun releaseGoesToReleasesAndForkGoesToTheForkedRepo() {
        assertEquals(FeedTarget.Releases("busybin", "gitrehub"), event(release).feedTarget())
        assertEquals(FeedTarget.ForkedRepo("someone/gitrehub"), event(fork).feedTarget())
        assertEquals("someone/gitrehub", event(fork).titleLine())
    }

    @Test
    fun anEventWithoutARepoStillOpensTheActor() {
        assertEquals(FeedTarget.User("someone"), event(follow).feedTarget())
    }

    @Test
    fun threadKeysWorkSoReadingHereAlsoReadsThere() {
        assertEquals("busybin/gitrehub#i2", ThreadKey.ofEvent(event(issueComment)))
        assertEquals("busybin/gitrehub#p2", ThreadKey.ofEvent(event(pullRequest)))
        // A push has no thread, so it falls back to the event id.
        assertNull(ThreadKey.ofEvent(event(push)))
    }

    @Test
    fun interactionsSourceFilterStillRecognisesMyOwnRepos() {
        val filter = FeedFilterState(source = FeedSource.INTERACTIONS)
        // "Someone acted on my repo": the owner is me and the actor is someone else. `repo` has no
        // owner, so the owner is split out of `name` (= owner/repo).
        assertTrue(event(starBySomeoneElse).matchesFilter(filter, me = "busybin", isRead = false))
        assertEquals(false, event(starBySomeoneElse).matchesFilter(filter, me = "someone-else", isRead = false))
        // My own push is not another person's interaction.
        assertEquals(false, event(push).matchesFilter(filter, me = "busybin", isRead = false))
    }
}
