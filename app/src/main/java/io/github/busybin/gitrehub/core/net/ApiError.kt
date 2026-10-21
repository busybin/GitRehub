package io.github.busybin.gitrehub.core.net

import java.io.IOException

/**
 * A failed GitHub API call, in the one shape every caller can reason about.
 *
 * [status] is 0 when the request never produced an HTTP answer (DNS, timeout, no network), so
 * "offline" and "GitHub said 404" stay distinguishable without string matching.
 */
data class ApiError(
    val status: Int,
    val message: String,
    /** `X-RateLimit-Remaining` hit 0; [retryAt] is the epoch second the window resets. */
    val rateLimited: Boolean = false,
    val retryAt: Long? = null,
) {
    val isOffline: Boolean get() = status == 0
}

/** Thrown by [GitHubClient]; [toUiText] maps the status to a localized message. */
class ApiException(val error: ApiError, cause: Throwable? = null) :
    IOException(if (error.status == 0) error.message else "HTTP ${error.status}: ${error.message}", cause) {

    constructor(status: Int, message: String) : this(ApiError(status, message))
}
