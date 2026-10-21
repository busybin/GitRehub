package io.github.busybin.gitrehub.helper

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** GitHub API timestamps (`yyyy-MM-dd'T'HH:mm:ss'Z'`). */
object ParseDateFormat {
    private val GITHUB_DATE = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        .withZone(ZoneOffset.UTC)

    /** `since` cursor for the notifications endpoint. */
    val lastWeekDate: String
        get() = GITHUB_DATE.format(Instant.now().minus(7, ChronoUnit.DAYS))
}
