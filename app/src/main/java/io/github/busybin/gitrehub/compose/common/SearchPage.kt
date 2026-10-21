package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.core.model.SearchResult
import io.github.busybin.gitrehub.core.net.ApiResponse
import io.github.busybin.gitrehub.core.net.hasMoreSearch

/** [PageResult] for a search answer (the envelope is not a bare list, see [SearchResult]). */
fun <T> ApiResponse<SearchResult<T>>.toPage(): PageResult<T> =
    PageResult(value.items, hasMoreSearch())
