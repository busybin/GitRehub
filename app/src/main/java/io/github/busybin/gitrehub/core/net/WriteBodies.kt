package io.github.busybin.gitrehub.core.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Request bodies of the write endpoints, built once here so they can be asserted in plain JVM
 * tests: GitHub only accepts the literal field names and enum values, and sending a field that
 * was not meant to change rewrites it (`milestone: null` clears the milestone).
 */
internal object WriteBodies {

    /** `POST /repos/{o}/{r}/issues`: only fields the caller actually chose are sent. */
    fun createIssue(
        title: String,
        body: String?,
        labels: List<String>,
        assignees: List<String>,
        milestone: Int?,
    ): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(title))
        if (!body.isNullOrBlank()) put("body", JsonPrimitive(body))
        if (labels.isNotEmpty()) put("labels", JsonArray(labels.map(::JsonPrimitive)))
        if (assignees.isNotEmpty()) put("assignees", JsonArray(assignees.map(::JsonPrimitive)))
        if (milestone != null) put("milestone", JsonPrimitive(milestone))
    }

    /**
     * `PATCH /repos/{o}/{r}/issues/{n}`. Issue and pull requests share it.
     * [milestoneChanged] is separate from [milestone] so `null` can mean "clear it".
     */
    fun editIssue(
        state: String? = null,
        title: String? = null,
        body: String? = null,
        milestone: Int? = null,
        milestoneChanged: Boolean = false,
    ): JsonObject = buildJsonObject {
        if (state != null) put("state", JsonPrimitive(state))
        if (title != null) put("title", JsonPrimitive(title))
        if (body != null) put("body", JsonPrimitive(body))
        if (milestoneChanged) {
            put("milestone", milestone?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    fun comment(body: String): JsonObject = buildJsonObject { put("body", JsonPrimitive(body)) }

    /** `content` is GitHub's reaction enum; a typo is a 422. */
    fun reaction(content: String): JsonObject =
        buildJsonObject { put("content", JsonPrimitive(content)) }

    /** `POST/PATCH /labels`: colour is six hex digits without `#`; an empty description clears it. */
    fun label(name: String, color: String, description: String?): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(name))
        put("color", JsonPrimitive(color.removePrefix("#")))
        put("description", JsonPrimitive(description.orEmpty()))
    }

    /** `POST/PATCH /milestones`: `due_on` is an ISO-8601 instant; an empty description clears it. */
    fun milestone(
        title: String,
        state: String,
        description: String?,
        dueOn: String?,
    ): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("state", JsonPrimitive(state))
        put("description", JsonPrimitive(description.orEmpty()))
        if (!dueOn.isNullOrBlank()) put("due_on", JsonPrimitive(dueOn))
    }

    /** `PUT /pulls/{n}/merge`: the message is optional and must not be sent empty. */
    fun merge(method: String, commitMessage: String?): JsonObject = buildJsonObject {
        put("merge_method", JsonPrimitive(method))
        if (!commitMessage.isNullOrBlank()) put("commit_message", JsonPrimitive(commitMessage))
    }

    fun assignees(logins: List<String>): JsonObject =
        buildJsonObject { put("assignees", JsonArray(logins.map(::JsonPrimitive))) }

    /** `PUT /repos/{o}/{r}/contents/{path}`: [sha] is required when replacing an existing file. */
    fun fileUpdate(
        message: String,
        base64Content: String,
        sha: String? = null,
        branch: String? = null,
    ): JsonObject = buildJsonObject {
        put("message", JsonPrimitive(message))
        put("content", JsonPrimitive(base64Content))
        if (!sha.isNullOrBlank()) put("sha", JsonPrimitive(sha))
        if (!branch.isNullOrBlank()) put("branch", JsonPrimitive(branch))
    }

    /** `POST /git/refs`: [ref] must be the full `refs/heads/...` name. */
    fun createBranch(ref: String, sha: String): JsonObject = buildJsonObject {
        put("ref", JsonPrimitive(ref))
        put("sha", JsonPrimitive(sha))
    }

    /** `POST /pulls`: [head] may be `owner:branch` for a fork; [base] is the target branch. */
    fun createPull(
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("head", JsonPrimitive(head))
        put("base", JsonPrimitive(base))
        if (!body.isNullOrBlank()) put("body", JsonPrimitive(body))
        if (draft) put("draft", JsonPrimitive(true))
    }

    /** One inline comment of a pending review. `side` is `LEFT` (deletions) or `RIGHT`. */
    fun reviewComment(path: String, line: Int, side: String, body: String): JsonObject =
        buildJsonObject {
            put("path", JsonPrimitive(path))
            put("line", JsonPrimitive(line))
            put("side", JsonPrimitive(side))
            put("body", JsonPrimitive(body))
        }

    /**
     * `POST /pulls/{n}/reviews`. [event] is `APPROVE`/`REQUEST_CHANGES`/`COMMENT`; a review made
     * only of inline comments still needs one, so the caller passes `COMMENT`.
     */
    fun review(
        commitId: String?,
        body: String?,
        event: String,
        comments: List<JsonObject>,
    ): JsonObject = buildJsonObject {
        if (!commitId.isNullOrBlank()) put("commit_id", JsonPrimitive(commitId))
        if (!body.isNullOrBlank()) put("body", JsonPrimitive(body))
        put("event", JsonPrimitive(event))
        if (comments.isNotEmpty()) put("comments", JsonArray(comments))
    }
}
