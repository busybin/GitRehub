package io.github.busybin.gitrehub.compose.actions

import io.github.busybin.gitrehub.core.model.WorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GitHub's run/job/step vocabulary → the chip the UI shows.
 *
 * The point of [RunStatus.of] is that **nothing is ever swallowed**: a word we do not know ends up
 * as UNKNOWN and is then displayed verbatim (see `StatusPill`), so a status GitHub invents later is
 * visible instead of silently rendered as something else.
 */
class RunStatusTest {

    @Test
    fun theDocumentedConclusionsAreMapped() {
        assertEquals(RunStatus.SUCCESS, RunStatus.of("success"))
        assertEquals(RunStatus.FAILURE, RunStatus.of("failure"))
        assertEquals(RunStatus.CANCELLED, RunStatus.of("cancelled"))
        assertEquals(RunStatus.SKIPPED, RunStatus.of("skipped"))
        assertEquals(RunStatus.TIMED_OUT, RunStatus.of("timed_out"))
        assertEquals(RunStatus.NEUTRAL, RunStatus.of("neutral"))
        assertEquals(RunStatus.ACTION_REQUIRED, RunStatus.of("action_required"))
        assertEquals(RunStatus.STALE, RunStatus.of("stale"))
    }

    @Test
    fun theInFlightStatusesAreMappedToo() {
        assertEquals(RunStatus.QUEUED, RunStatus.of("queued"))
        assertEquals(RunStatus.IN_PROGRESS, RunStatus.of("in_progress"))
        assertEquals(RunStatus.WAITING, RunStatus.of("waiting"))
        assertEquals(RunStatus.REQUESTED, RunStatus.of("requested"))
        assertEquals(RunStatus.PENDING, RunStatus.of("pending"))
    }

    @Test
    fun caseDoesNotMatterAndCancellationsBothSpellingsDo() {
        assertEquals(RunStatus.SUCCESS, RunStatus.of("SUCCESS"))
        assertEquals(RunStatus.IN_PROGRESS, RunStatus.of("In_Progress"))
        assertEquals(RunStatus.CANCELLED, RunStatus.of("canceled"))
    }

    @Test
    fun unknownOrMissingWordsDoNotCrash() {
        assertEquals(RunStatus.UNKNOWN, RunStatus.of("whatever_github_does_next"))
        assertEquals(RunStatus.UNKNOWN, RunStatus.of(null))
        assertEquals(RunStatus.UNKNOWN, RunStatus.of(""))
    }

    @Test
    fun theChipWordFallsBackToWhatTheApiSaid() {
        assertEquals("in_progress", WorkflowRun(status = "in_progress").statusWord)
        assertEquals("failure", WorkflowRun(status = "completed", conclusion = "failure").statusWord)
        // the title prefers the display title GitHub wrote, then the workflow name
        assertEquals(
            "Fix the flaky test",
            WorkflowRun(name = "CI", displayTitle = "Fix the flaky test").title,
        )
        assertEquals("CI", WorkflowRun(name = "CI").title)
    }
}
