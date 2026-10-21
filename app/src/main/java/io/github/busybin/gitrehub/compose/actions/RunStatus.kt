package io.github.busybin.gitrehub.compose.actions

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Pill

/** How a workflow run / job / step ended (or that it has not ended yet). */
enum class RunStatus(@StringRes val labelRes: Int) {
    SUCCESS(R.string.m3_status_success),
    FAILURE(R.string.m3_status_failure),
    CANCELLED(R.string.m3_status_cancelled),
    SKIPPED(R.string.m3_status_skipped),
    TIMED_OUT(R.string.m3_status_timed_out),
    NEUTRAL(R.string.m3_status_neutral),
    ACTION_REQUIRED(R.string.m3_status_action_required),
    STALE(R.string.m3_status_stale),
    QUEUED(R.string.m3_status_queued),
    IN_PROGRESS(R.string.m3_status_in_progress),
    WAITING(R.string.m3_status_waiting),
    REQUESTED(R.string.m3_status_requested),
    PENDING(R.string.m3_status_pending),
    UNKNOWN(R.string.m3_status_unknown),
    ;

    companion object {
        /**
         * GitHub uses the same vocabulary for a run's conclusion and, while it is still going, its
         * status (`queued` / `in_progress` / …). Anything we have never seen is [UNKNOWN] and is
         * then shown verbatim rather than swallowed — a status GitHub just invented should be
         * visible, not silently rendered as "unknown".
         */
        fun of(word: String?): RunStatus = when (word?.lowercase()) {
            "success" -> SUCCESS
            "failure" -> FAILURE
            "cancelled", "canceled" -> CANCELLED
            "skipped" -> SKIPPED
            "timed_out" -> TIMED_OUT
            "neutral" -> NEUTRAL
            "action_required" -> ACTION_REQUIRED
            "stale" -> STALE
            "queued" -> QUEUED
            "in_progress" -> IN_PROGRESS
            "waiting" -> WAITING
            "requested" -> REQUESTED
            "pending" -> PENDING
            else -> UNKNOWN
        }
    }
}

private val GREEN = Color(0xFF1A7F37)

/**
 * Coloured status chip. GitHub's own green/red/grey idea, expressed with the current theme where it
 * can be — only "success" keeps a literal green dot, for the same reason the issue list does.
 */
@Composable
fun StatusPill(word: String?) {
    if (word.isNullOrBlank()) return
    val status = RunStatus.of(word)
    val container: Color
    val content: Color
    var dot: Color? = null
    when (status) {
        RunStatus.SUCCESS -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            dot = GREEN
        }

        RunStatus.FAILURE, RunStatus.TIMED_OUT, RunStatus.ACTION_REQUIRED -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }

        RunStatus.IN_PROGRESS, RunStatus.QUEUED, RunStatus.WAITING, RunStatus.REQUESTED,
        RunStatus.PENDING -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }

        else -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Pill(
        text = if (status == RunStatus.UNKNOWN) word else stringResource(status.labelRes),
        container = container,
        content = content,
        leadingDot = dot,
    )
}
