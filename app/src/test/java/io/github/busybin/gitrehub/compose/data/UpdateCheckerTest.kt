package io.github.busybin.gitrehub.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsing part of the update check (pure logic, JVM).
 *
 * Rule: a parse failure is a failure, never "an update is available" — a weird tag must not make the
 * app invite the user to download something.
 */
class UpdateCheckerTest {

    private fun release(tag: String, url: String = "https://github.com/busybin/gitrehub/releases/tag/$tag") =
        """{"tag_name":"$tag","html_url":"$url","name":"Release $tag"}"""

    @Test
    fun versionTagsAreParsedEitherCase() {
        assertEquals(12, UpdateChecker.versionFromTag("V12"))
        assertEquals(12, UpdateChecker.versionFromTag("v12"))
        assertEquals(12, UpdateChecker.versionFromTag(" 12 "))
        assertEquals(null, UpdateChecker.versionFromTag("4.7.7"))
        assertEquals(null, UpdateChecker.versionFromTag("nightly"))
    }

    @Test
    fun newerTagIsReportedAsAvailable() {
        val result = UpdateChecker.classify(release("V2"), currentVersion = 1)
        assertTrue(result is UpdateChecker.Result.Available)
        val available = result as UpdateChecker.Result.Available
        assertEquals(2, available.version)
        assertEquals("V2", available.tag)
        assertEquals("https://github.com/busybin/gitrehub/releases/tag/V2", available.url)
    }

    @Test
    fun sameOrOlderTagIsUpToDate() {
        assertTrue(UpdateChecker.classify(release("V1"), currentVersion = 1) is UpdateChecker.Result.UpToDate)
        assertTrue(UpdateChecker.classify(release("V0"), currentVersion = 1) is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun garbageIsAFailureNotAnUpdate() {
        assertTrue(UpdateChecker.classify(null, 1) is UpdateChecker.Result.Failed)
        assertTrue(UpdateChecker.classify("not json", 1) is UpdateChecker.Result.Failed)
        assertTrue(UpdateChecker.classify("""{"name":"no tag here"}""", 1) is UpdateChecker.Result.Failed)
        assertTrue(UpdateChecker.classify(release("release-2024"), 1) is UpdateChecker.Result.Failed)
    }

    @Test
    fun missingUrlFallsBackToReleasesPage() {
        val result = UpdateChecker.classify("""{"tag_name":"V9"}""", 1)
        assertEquals(
            UpdateChecker.RELEASES_URL,
            (result as UpdateChecker.Result.Available).url,
        )
    }
}
