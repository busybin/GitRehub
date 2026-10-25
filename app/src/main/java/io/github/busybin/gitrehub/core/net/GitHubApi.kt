package io.github.busybin.gitrehub.core.net

import io.github.busybin.gitrehub.core.model.Branch
import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.core.model.CheckRuns
import io.github.busybin.gitrehub.core.model.CodeFrequency
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.model.CommitFile
import io.github.busybin.gitrehub.core.model.ContributionCalendar
import io.github.busybin.gitrehub.core.model.ContributorStat
import io.github.busybin.gitrehub.core.model.CreatedPullRequest
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Label
import io.github.busybin.gitrehub.core.model.MergeResult
import io.github.busybin.gitrehub.core.model.Milestone
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.core.model.PullRequest
import io.github.busybin.gitrehub.core.model.PunchCard
import io.github.busybin.gitrehub.core.model.Reaction
import io.github.busybin.gitrehub.core.model.Release
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.RepoSubscription
import io.github.busybin.gitrehub.core.model.Review
import io.github.busybin.gitrehub.core.model.ReviewComment
import io.github.busybin.gitrehub.core.model.SearchCode
import io.github.busybin.gitrehub.core.model.SearchResult
import io.github.busybin.gitrehub.core.model.StatsOutcome
import io.github.busybin.gitrehub.core.model.Tag
import io.github.busybin.gitrehub.core.model.ThreadSubscription
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.model.WatchLevel
import io.github.busybin.gitrehub.core.model.WatchersPage
import io.github.busybin.gitrehub.core.model.WeekActivity
import io.github.busybin.gitrehub.core.model.Workflow
import io.github.busybin.gitrehub.core.model.WorkflowJob
import io.github.busybin.gitrehub.core.model.WorkflowJobs
import io.github.busybin.gitrehub.core.model.WorkflowRun
import io.github.busybin.gitrehub.core.model.WorkflowRuns
import io.github.busybin.gitrehub.core.model.Workflows
import io.github.busybin.gitrehub.core.model.parseCodeFrequency
import io.github.busybin.gitrehub.core.model.parseCommitActivity
import io.github.busybin.gitrehub.core.model.parseContributors
import io.github.busybin.gitrehub.core.model.parseInteractions
import io.github.busybin.gitrehub.core.model.parsePunchCard
import io.github.busybin.gitrehub.core.model.parseWatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Every REST/GraphQL call the rewritten data layer makes, one `suspend fun` each.
 *
 * Callers get the decoded value (and, where it matters, the scopes/pagination headers); failures
 * arrive as [ApiException]. No endpoint here knows about tokens of other accounts — a token is
 * only passed in when it has not been stored yet (login validation).
 */
object GitHubApi {

    suspend fun currentUser(token: String? = null): ApiResponse<User> =
        GitHubClient.get("/user", User.serializer(), token = token)

    /** Public profile of any account. */
    suspend fun user(login: String, token: String? = null): User =
        GitHubClient.get("/users/$login", User.serializer(), token = token).value

    suspend fun followers(login: String, page: Int): ApiResponse<List<User>> =
        people("/users/$login/followers", page)

    suspend fun following(login: String, page: Int): ApiResponse<List<User>> =
        people("/users/$login/following", page)

    /** Public members of an organization (`GET /orgs/{org}/members`). */
    suspend fun orgMembers(org: String, page: Int): ApiResponse<List<User>> =
        people("/orgs/$org/members", page)

    /** `GET /user/following/{login}`: 204 when followed, 404 when not. */
    suspend fun isFollowing(login: String): Boolean =
        GitHubClient.exists("/user/following/$login")

    suspend fun setFollowing(login: String, following: Boolean) =
        GitHubClient.sendNoContent(
            if (following) "PUT" else "DELETE",
            "/user/following/$login",
        )

    // ---------------------------------------------------------------- gists

    /** `GET /gists`: the signed-in account's gists, newest first. Includes secret gists. */
    suspend fun myGists(page: Int): ApiResponse<List<Gist>> = gists("/gists", page)

    /** `GET /users/{login}/gists`: the public gists of any account. */
    suspend fun userGists(login: String, page: Int): ApiResponse<List<Gist>> =
        gists("/users/$login/gists", page)

    /** One gist with its files; the list endpoint already has them, this is for direct links. */
    suspend fun gist(id: String): Gist =
        GitHubClient.get("/gists/$id", Gist.serializer()).value

    suspend fun deleteGist(id: String) = GitHubClient.sendNoContent("DELETE", "/gists/$id")

    private suspend fun gists(path: String, page: Int): ApiResponse<List<Gist>> =
        GitHubClient.get(
            path,
            ListSerializer(Gist.serializer()),
            query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
        )

    // ---------------------------------------------------------------- repos

    /** Same page size everywhere; full pages are how the fallback "there is more" check works. */
    const val PAGE_SIZE = 30

    /** `GET /user/repos`: owned, collaborator and organization repositories, newest push first. */
    suspend fun myRepos(page: Int, cacheOnly: Boolean = false): ApiResponse<List<Repo>> = repos(
        "/user/repos",
        page,
        cacheOnly,
        mapOf(
            "affiliation" to "owner,collaborator,organization_member",
            "sort" to "updated",
            "direction" to "desc",
        ),
    )

    suspend fun starredRepos(
        login: String,
        page: Int,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Repo>> = repos("/users/$login/starred", page, cacheOnly)

    suspend fun userRepos(
        login: String,
        page: Int,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Repo>> = repos(
        "/users/$login/repos",
        page,
        cacheOnly,
        mapOf("sort" to "updated", "direction" to "desc"),
    )

    // ---------------------------------------------------------------- search

    /**
     * The search endpoints answer `{total_count, incomplete_results, items}` instead of a bare
     * array and take the whole query as one string: qualifiers are separated by spaces, which
     * [GitHubClient] percent-encodes. Pages are the same 30 rows.
     */
    private suspend fun <T> search(
        path: String,
        query: String,
        page: Int,
        serializer: KSerializer<T>,
        sort: String? = null,
        order: String? = null,
    ): ApiResponse<SearchResult<T>> {
        val params = buildMap {
            put("q", query)
            put("page", "$page")
            put("per_page", "$PAGE_SIZE")
            if (sort != null) put("sort", sort)
            if (order != null) put("order", order)
        }
        return GitHubClient.get(path, SearchResult.serializer(serializer), query = params)
    }

    /** Trending is not an API: search recently created repositories by stars (see `TrendingViewModel`). */
    suspend fun searchRepos(
        query: String,
        page: Int,
        sort: String? = null,
        order: String? = null,
    ): ApiResponse<SearchResult<Repo>> =
        search("/search/repositories", query, page, Repo.serializer(), sort, order)

    suspend fun searchUsers(query: String, page: Int): ApiResponse<SearchResult<User>> =
        search("/search/users", query, page, User.serializer())

    /**
     * `search/issues` returns pull requests too. My Work sorts by `updated` desc; the search
     * screen leaves the sort to GitHub's best match, which is why both are optional.
     */
    suspend fun searchIssues(
        query: String,
        page: Int,
        sort: String? = null,
        order: String? = null,
    ): ApiResponse<SearchResult<Issue>> =
        search("/search/issues", query, page, Issue.serializer(), sort, order)

    /** Code search needs a `repo:`/`user:`/`org:` qualifier, otherwise GitHub answers 422. */
    suspend fun searchCode(query: String, page: Int): ApiResponse<SearchResult<SearchCode>> =
        search("/search/code", query, page, SearchCode.serializer())

    private suspend fun repos(
        path: String,
        page: Int,
        cacheOnly: Boolean,
        query: Map<String, String> = emptyMap(),
    ): ApiResponse<List<Repo>> = GitHubClient.get(
        path,
        ListSerializer(Repo.serializer()),
        query = query + mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
        cacheOnly = cacheOnly,
    )

    suspend fun repo(owner: String, repo: String): Repo =
        GitHubClient.get("/repos/$owner/$repo", Repo.serializer()).value

    /** README rendered by GitHub itself (`Accept: application/vnd.github.html`), as the detail page shows it. */
    suspend fun readmeHtml(owner: String, repo: String): String = GitHubClient.getText(
        "/repos/$owner/$repo/readme",
        accept = "application/vnd.github.html",
    )

    /** `GET /user/starred/{o}/{r}`: 204 when starred, 404 when not. */
    suspend fun isStarred(owner: String, repo: String): Boolean =
        GitHubClient.exists("/user/starred/$owner/$repo")

    suspend fun setStarred(owner: String, repo: String, starred: Boolean) =
        GitHubClient.sendNoContent(
            if (starred) "PUT" else "DELETE",
            "/user/starred/$owner/$repo",
        )

    /** `GET /repos/{o}/{r}/subscription`: a 404 (→ exception) means "not watching". */
    suspend fun repoSubscription(owner: String, repo: String): RepoSubscription =
        GitHubClient.get(
            "/repos/$owner/$repo/subscription",
            RepoSubscription.serializer(),
        ).value

    /** `null` unwatches; otherwise PUT the level (all notifications, or ignored). */
    suspend fun setWatchLevel(owner: String, repo: String, level: WatchLevel?) {
        if (level == null) {
            GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/subscription")
            return
        }
        GitHubClient.sendNoContent(
            "PUT",
            "/repos/$owner/$repo/subscription",
            watchSubscriptionBody(level),
        )
    }

    suspend fun forkRepo(
        owner: String,
        repo: String,
        forkName: String? = null,
        defaultBranchOnly: Boolean = false,
    ): Repo = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/forks",
        buildJsonObject {
            if (!forkName.isNullOrBlank()) put("name", JsonPrimitive(forkName))
            if (defaultBranchOnly) put("default_branch_only", JsonPrimitive(true))
        },
        Repo.serializer(),
    ).value

    suspend fun contributors(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<User>> = people("/repos/$owner/$repo/contributors", page)

    suspend fun stargazers(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<User>> = people("/repos/$owner/$repo/stargazers", page)

    suspend fun forks(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<Repo>> = repos("/repos/$owner/$repo/forks", page, cacheOnly = false)

    private suspend fun people(path: String, page: Int): ApiResponse<List<User>> = GitHubClient.get(
        path,
        ListSerializer(User.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    /** Language bytes, e.g. `{"Kotlin": 12345}`. */
    suspend fun languages(owner: String, repo: String): Map<String, Long> = GitHubClient.get(
        "/repos/$owner/$repo/languages",
        MapSerializer(String.serializer(), Long.serializer()),
    ).value

    /**
     * Exact total of a list endpoint: `per_page=1` and the `rel="last"` page number is the count.
     * When the server omits the header (a single page) the row count is the answer.
     */
    suspend fun openPullsCount(owner: String, repo: String): Int =
        total("/repos/$owner/$repo/pulls", mapOf("state" to "open"))

    suspend fun branchesCount(owner: String, repo: String): Int =
        total("/repos/$owner/$repo/branches")

    suspend fun tagsCount(owner: String, repo: String): Int =
        total("/repos/$owner/$repo/tags")

    suspend fun contributorsCount(owner: String, repo: String): Int =
        total("/repos/$owner/$repo/contributors")

    private suspend fun total(path: String, query: Map<String, String> = emptyMap()): Int {
        val response = GitHubClient.get(
            path,
            JsonArray.serializer(),
            query = query + mapOf("per_page" to "1"),
        )
        return response.links.last ?: response.value.size
    }

    private const val WATCHERS_QUERY = """
        query RepoWatchers(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}cursor: String) {
          repository(owner: ${'$'}owner, name: ${'$'}repo) {
            watchers(first: 30, after: ${'$'}cursor) {
              pageInfo { hasNextPage endCursor }
              nodes { databaseId login name avatarUrl }
            }
          }
        }
    """

    /** Watchers come from GraphQL: REST `/subscribers` 404s for repos the account does not administer. */
    suspend fun repoWatchers(owner: String, repo: String, cursor: String?): WatchersPage =
        parseWatchers(
            GitHubClient.graphQl(
                WATCHERS_QUERY,
                buildJsonObject {
                    put("owner", JsonPrimitive(owner))
                    put("repo", JsonPrimitive(repo))
                    if (cursor != null) put("cursor", JsonPrimitive(cursor))
                },
            ),
            GitHubClient.json,
        )

    // ------------------------------------------------------------ issues / pulls

    /**
     * `GET /repos/{o}/{r}/issues` — pull requests included, callers filter with `isPullRequest`.
     * [sort] is `created`/`updated`/`comments`; GitHub defaults the direction per sort.
     *
     * [labels] is a comma-separated AND filter, [assignee] takes a login or `*` (nobody) and
     * [milestone] a milestone number, `*` or `none`.
     */
    suspend fun repoIssues(
        owner: String,
        repo: String,
        state: String,
        sort: String,
        page: Int,
        cacheOnly: Boolean = false,
        direction: String? = null,
        labels: List<String> = emptyList(),
        assignee: String? = null,
        milestone: Int? = null,
    ): ApiResponse<List<Issue>> = issues(
        "/repos/$owner/$repo/issues",
        page,
        buildMap {
            put("state", state)
            put("sort", sort)
            if (!direction.isNullOrBlank()) put("direction", direction)
            if (labels.isNotEmpty()) put("labels", labels.joinToString(","))
            when (assignee) {
                null -> Unit
                "*" -> put("assignee", "none")
                else -> put("assignee", assignee)
            }
            when (milestone) {
                null -> Unit
                -1 -> put("milestone", "none")
                else -> put("milestone", milestone.toString())
            }
        },
        cacheOnly,
    )

    private suspend fun issues(
        path: String,
        page: Int,
        query: Map<String, String>,
        cacheOnly: Boolean,
    ): ApiResponse<List<Issue>> = GitHubClient.get(
        path,
        ListSerializer(Issue.serializer()),
        query = query + mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
        cacheOnly = cacheOnly,
    )

    suspend fun issue(owner: String, repo: String, number: Int): Issue =
        GitHubClient.get("/repos/$owner/$repo/issues/$number", Issue.serializer()).value

    /** Full page size for comments: GitHub allows 100 and the screen shows them all. */
    const val COMMENTS_PER_PAGE = 100

    suspend fun issueComments(
        owner: String,
        repo: String,
        number: Int,
        page: Int,
    ): ApiResponse<List<Comment>> = GitHubClient.get(
        "/repos/$owner/$repo/issues/$number/comments",
        ListSerializer(Comment.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$COMMENTS_PER_PAGE"),
    )

    suspend fun pullRequest(owner: String, repo: String, number: Int): PullRequest =
        GitHubClient.get("/repos/$owner/$repo/pulls/$number", PullRequest.serializer()).value

    /** `GET /pulls/{n}/files`: same shape as a commit's files, so the same model is reused. */
    suspend fun pullRequestFiles(
        owner: String,
        repo: String,
        number: Int,
        page: Int,
    ): ApiResponse<List<CommitFile>> = GitHubClient.get(
        "/repos/$owner/$repo/pulls/$number/files",
        ListSerializer(CommitFile.serializer()),
        query = mapOf("page" to "$page", "per_page" to "100"),
    )

    /** `GET /pulls/{n}/commits`: the commits of the pull request (first page is enough for the sheet). */
    suspend fun pullRequestCommits(
        owner: String,
        repo: String,
        number: Int,
        page: Int,
    ): ApiResponse<List<Commit>> = GitHubClient.get(
        "/repos/$owner/$repo/pulls/$number/commits",
        ListSerializer(Commit.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    suspend fun pullRequestReviews(owner: String, repo: String, number: Int): List<Review> =
        GitHubClient.get(
            "/repos/$owner/$repo/pulls/$number/reviews",
            ListSerializer(Review.serializer()),
            query = mapOf("per_page" to "100"),
        ).value

    suspend fun pullRequestReviewComments(
        owner: String,
        repo: String,
        number: Int,
    ): List<ReviewComment> = GitHubClient.get(
        "/repos/$owner/$repo/pulls/$number/comments",
        ListSerializer(ReviewComment.serializer()),
        query = mapOf("per_page" to "100"),
    ).value

    /** `POST /pulls/{n}/reviews`: inline comments plus the verdict in one submission. */
    suspend fun submitReview(
        owner: String,
        repo: String,
        number: Int,
        commitId: String?,
        body: String?,
        event: String,
        comments: List<JsonObject>,
    ): Review = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/pulls/$number/reviews",
        WriteBodies.review(commitId, body, event, comments),
        Review.serializer(),
    ).value

    /** Check runs of a commit (`/commits/{ref}/check-runs` wraps its array in an object). */
    suspend fun checkRuns(owner: String, repo: String, ref: String): List<CheckRun> =
        GitHubClient.get(
            "/repos/$owner/$repo/commits/$ref/check-runs",
            CheckRuns.serializer(),
            query = mapOf("per_page" to "100"),
        ).value.checkRuns

    // ------------------------------------------------------- issues: write paths

    /** `POST /repos/{o}/{r}/issues`. `milestone` is the milestone *number*, not its database id. */
    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String?,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
        milestone: Int? = null,
    ): Issue = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/issues",
        WriteBodies.createIssue(title, body, labels, assignees, milestone),
        Issue.serializer(),
    ).value

    /** Close / reopen. Pull requests share this endpoint with plain issues. */
    suspend fun setIssueState(owner: String, repo: String, number: Int, closed: Boolean): Issue =
        GitHubClient.send(
            "PATCH",
            "/repos/$owner/$repo/issues/$number",
            WriteBodies.editIssue(state = if (closed) "closed" else "open"),
            Issue.serializer(),
        ).value

    suspend fun editIssue(
        owner: String,
        repo: String,
        number: Int,
        title: String,
        body: String?,
    ): Issue = GitHubClient.send(
        "PATCH",
        "/repos/$owner/$repo/issues/$number",
        WriteBodies.editIssue(title = title, body = body.orEmpty()),
        Issue.serializer(),
    ).value

    /** `null` clears the milestone (the body then carries an explicit `null`). */
    suspend fun setIssueMilestone(
        owner: String,
        repo: String,
        number: Int,
        milestone: Int?,
    ): Issue = GitHubClient.send(
        "PATCH",
        "/repos/$owner/$repo/issues/$number",
        WriteBodies.editIssue(milestone = milestone, milestoneChanged = true),
        Issue.serializer(),
    ).value

    suspend fun lockIssue(owner: String, repo: String, number: Int, locked: Boolean) {
        if (locked) {
            GitHubClient.sendNoContent("PUT", "/repos/$owner/$repo/issues/$number/lock")
        } else {
            GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/issues/$number/lock")
        }
    }

    /** `PUT .../labels` replaces the whole set (an empty list removes every label). */
    suspend fun setIssueLabels(
        owner: String,
        repo: String,
        number: Int,
        labels: List<String>,
    ): List<Label> =
        GitHubClient.send(
            "PUT",
            "/repos/$owner/$repo/issues/$number/labels",
            JsonArray(labels.map(::JsonPrimitive)),
            ListSerializer(Label.serializer()),
        ).value

    suspend fun addAssignees(owner: String, repo: String, number: Int, logins: List<String>): Issue =
        GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/issues/$number/assignees",
            WriteBodies.assignees(logins),
            Issue.serializer(),
        ).value

    /** Removing assignees is a `DELETE` with a body — the one endpoint where DELETE carries one. */
    suspend fun removeAssignees(
        owner: String,
        repo: String,
        number: Int,
        logins: List<String>,
    ): Issue = GitHubClient.send(
        "DELETE",
        "/repos/$owner/$repo/issues/$number/assignees",
        WriteBodies.assignees(logins),
        Issue.serializer(),
    ).value

    suspend fun createComment(owner: String, repo: String, number: Int, body: String): Comment =
        GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/issues/$number/comments",
            WriteBodies.comment(body),
            Comment.serializer(),
        ).value

    suspend fun editComment(owner: String, repo: String, commentId: Long, body: String): Comment =
        GitHubClient.send(
            "PATCH",
            "/repos/$owner/$repo/issues/comments/$commentId",
            WriteBodies.comment(body),
            Comment.serializer(),
        ).value

    suspend fun deleteComment(owner: String, repo: String, commentId: Long) =
        GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/issues/comments/$commentId")

    /**
     * `PUT /pulls/{n}/merge`. A wrong `merge_method` is a 422, "not mergeable" a 405; a 200 with
     * `merged=false` is turned into the same 405 so callers have one failure path.
     */
    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: String,
        commitMessage: String? = null,
    ): MergeResult {
        val result = GitHubClient.send(
            "PUT",
            "/repos/$owner/$repo/pulls/$number/merge",
            WriteBodies.merge(method, commitMessage),
            MergeResult.serializer(),
        ).value
        if (!result.merged) throw ApiException(405, result.message ?: "Pull request is not mergeable")
        return result
    }

    // ---------------------------------------------------------------- reactions

    /** Rows are 30 per page; `content` filters to one reaction so the caller can look for itself. */
    private suspend fun reactions(path: String, content: String, page: Int) =
        GitHubClient.get(
            path,
            ListSerializer(Reaction.serializer()),
            query = mapOf("content" to content, "page" to "$page", "per_page" to "$PAGE_SIZE"),
        )

    suspend fun issueReactions(
        owner: String,
        repo: String,
        number: Int,
        content: String,
        page: Int = 1,
    ): ApiResponse<List<Reaction>> = reactions("/repos/$owner/$repo/issues/$number/reactions", content, page)

    suspend fun issueCommentReactions(
        owner: String,
        repo: String,
        commentId: Long,
        content: String,
        page: Int = 1,
    ): ApiResponse<List<Reaction>> =
        reactions("/repos/$owner/$repo/issues/comments/$commentId/reactions", content, page)

    suspend fun addIssueReaction(owner: String, repo: String, number: Int, content: String): Reaction =
        GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/issues/$number/reactions",
            WriteBodies.reaction(content),
            Reaction.serializer(),
        ).value

    suspend fun addIssueCommentReaction(
        owner: String,
        repo: String,
        commentId: Long,
        content: String,
    ): Reaction = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/issues/comments/$commentId/reactions",
        WriteBodies.reaction(content),
        Reaction.serializer(),
    ).value

    /** `DELETE /reactions/{id}`: only the reaction's own id, no repo in the path. */
    suspend fun deleteReaction(id: Long) = GitHubClient.sendNoContent("DELETE", "/reactions/$id")

    // ------------------------------------------------- manage-drawer candidates

    /** Labels / assignable users / open milestones of a repository, for the pickers. */
    suspend fun repoLabels(owner: String, repo: String): List<Label> =
        GitHubClient.get(
            "/repos/$owner/$repo/labels",
            ListSerializer(Label.serializer()),
            query = mapOf("per_page" to "100"),
        ).value

    suspend fun assignableUsers(owner: String, repo: String): List<User> = GitHubClient.get(
        "/repos/$owner/$repo/assignees",
        ListSerializer(User.serializer()),
        query = mapOf("per_page" to "100"),
    ).value

    suspend fun repoMilestones(owner: String, repo: String): List<Milestone> =
        repoMilestones(owner, repo, state = "open")

    /** Management screens list closed milestones too. */
    suspend fun repoMilestones(owner: String, repo: String, state: String): List<Milestone> =
        GitHubClient.get(
            "/repos/$owner/$repo/milestones",
            ListSerializer(Milestone.serializer()),
            query = mapOf("state" to state, "per_page" to "100"),
        ).value

    // ------------------------------------------------- repository labels / milestones (write)

    suspend fun createLabel(owner: String, repo: String, name: String, color: String, description: String?): Label =
        GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/labels",
            WriteBodies.label(name, color, description),
            Label.serializer(),
        ).value

    suspend fun editLabel(
        owner: String,
        repo: String,
        currentName: String,
        name: String,
        color: String,
        description: String?,
    ): Label = GitHubClient.send(
        "PATCH",
        "/repos/$owner/$repo/labels/${encodePath(currentName)}",
        WriteBodies.label(name, color, description),
        Label.serializer(),
    ).value

    suspend fun deleteLabel(owner: String, repo: String, name: String) =
        GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/labels/${encodePath(name)}")

    suspend fun createMilestone(
        owner: String,
        repo: String,
        title: String,
        description: String?,
        dueOn: String?,
    ): Milestone = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/milestones",
        WriteBodies.milestone(title, "open", description, dueOn),
        Milestone.serializer(),
    ).value

    suspend fun editMilestone(
        owner: String,
        repo: String,
        number: Int,
        title: String,
        state: String,
        description: String?,
        dueOn: String?,
    ): Milestone = GitHubClient.send(
        "PATCH",
        "/repos/$owner/$repo/milestones/$number",
        WriteBodies.milestone(title, state, description, dueOn),
        Milestone.serializer(),
    ).value

    suspend fun deleteMilestone(owner: String, repo: String, number: Int) =
        GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/milestones/$number")

    // ------------------------------------------------- commits / refs / releases / contents

    /**
     * `GET /repos/{o}/{r}/commits`. [ref] empty means the default branch (GitHub rejects an empty
     * `sha`); [path] restricts the history to one file or directory ("last modified" line).
     */
    suspend fun commits(
        owner: String,
        repo: String,
        ref: String,
        page: Int,
        path: String? = null,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Commit>> = GitHubClient.get(
        "/repos/$owner/$repo/commits",
        ListSerializer(Commit.serializer()),
        query = buildMap {
            if (ref.isNotBlank()) put("sha", ref)
            if (!path.isNullOrBlank()) put("path", path)
            put("page", "$page")
            put("per_page", "$PAGE_SIZE")
        },
        cacheOnly = cacheOnly,
    )

    /** `GET /repos/{o}/{r}/commits/{sha}`: adds `stats` and `files[].patch` (the diff). */
    suspend fun commit(owner: String, repo: String, sha: String): Commit =
        GitHubClient.get("/repos/$owner/$repo/commits/$sha", Commit.serializer()).value

    suspend fun branches(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<Branch>> = GitHubClient.get(
        "/repos/$owner/$repo/branches",
        ListSerializer(Branch.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    suspend fun tags(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<Tag>> = GitHubClient.get(
        "/repos/$owner/$repo/tags",
        ListSerializer(Tag.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    suspend fun releases(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<Release>> = GitHubClient.get(
        "/repos/$owner/$repo/releases",
        ListSerializer(Release.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    suspend fun release(owner: String, repo: String, id: Long): Release =
        GitHubClient.get("/repos/$owner/$repo/releases/$id", Release.serializer()).value

    /**
     * Directory contents (`GET .../contents/{path}`) as a raw answer: the endpoint returns an array
     * for a directory and an object for a file, and GitHub rejects a malformed path with 404.
     */
    suspend fun contents(owner: String, repo: String, path: String, ref: String?): RawResponse =
        GitHubClient.getRaw(
            "/repos/$owner/$repo/contents/${encodePath(path)}",
            query = if (ref.isNullOrBlank()) emptyMap() else mapOf("ref" to ref),
        )

    /** Raw file text, fetched from the entry's own URL (works for private repositories too). */
    suspend fun fileText(url: String): String =
        GitHubClient.getTextUrl(url, accept = "application/vnd.github.VERSION.raw")

    // ---------------------------------------------------------------- contents: write paths

    /**
     * `PUT /repos/{o}/{r}/contents/{path}`. [content] is already base64; [sha] is the blob sha of
     * the file being replaced (omitted when creating). The answer's commit is not needed by callers.
     */
    suspend fun putFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        content: String,
        sha: String? = null,
        branch: String? = null,
    ) = GitHubClient.sendNoContent(
        "PUT",
        "/repos/$owner/$repo/contents/${encodePath(path)}",
        WriteBodies.fileUpdate(message, content, sha, branch),
    )

    /** `POST /git/refs`: branches off an existing commit. */
    suspend fun createBranch(owner: String, repo: String, branch: String, sha: String) =
        GitHubClient.sendNoContent(
            "POST",
            "/repos/$owner/$repo/git/refs",
            WriteBodies.createBranch("refs/heads/$branch", sha),
        )

    /** Head commit of a branch, needed to branch off it. */
    suspend fun branchHeadSha(owner: String, repo: String, branch: String): String =
        GitHubClient.get(
            "/repos/$owner/$repo/git/ref/heads/$branch",
            GitRefEnvelope.serializer(),
        ).value.objectSha

    /** `POST /pulls`. */
    suspend fun createPull(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false,
    ): CreatedPullRequest = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/pulls",
        WriteBodies.createPull(title, head, base, body, draft),
        CreatedPullRequest.serializer(),
    ).value

    @Serializable
    private data class GitRefEnvelope(
        @SerialName("object") val objectRef: GitRefObject? = null,
    ) {
        val objectSha: String get() = objectRef?.sha.orEmpty()
    }

    @Serializable
    private data class GitRefObject(val sha: String = "")

    // ---------------------------------------------------------------- repository stats

    suspend fun commitActivity(owner: String, repo: String): StatsOutcome<List<WeekActivity>> =
        stats("/repos/$owner/$repo/stats/commit_activity", ::parseCommitActivity)

    suspend fun contributorsStats(owner: String, repo: String): StatsOutcome<List<ContributorStat>> =
        stats("/repos/$owner/$repo/stats/contributors", ::parseContributors)

    suspend fun codeFrequency(owner: String, repo: String): StatsOutcome<List<CodeFrequency>> =
        stats("/repos/$owner/$repo/stats/code_frequency", ::parseCodeFrequency)

    suspend fun punchCard(owner: String, repo: String): StatsOutcome<PunchCard> =
        stats("/repos/$owner/$repo/stats/punch_card", ::parsePunchCard)

    /** Stats endpoints answer 202 while GitHub computes them; that is not an empty result. */
    private suspend fun <T> stats(path: String, parse: (String?) -> T): StatsOutcome<T> {
        val raw = GitHubClient.getRaw(path)
        if (raw.code == 202) return StatsOutcome.Computing
        return StatsOutcome.Data(parse(raw.body))
    }

    /** Percent-encodes each path segment; `+` is not valid in a path, so spaces become `%20`. */
    private fun encodePath(path: String): String = path.trim('/').split('/')
        .filter { it.isNotEmpty() }
        .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    // ---------------------------------------------------------------- actions / discussions

    suspend fun workflows(owner: String, repo: String): List<Workflow> = GitHubClient.get(
        "/repos/$owner/$repo/actions/workflows",
        Workflows.serializer(),
        query = mapOf("per_page" to "100"),
    ).value.workflows

    /**
     * Runs of a repository, or of one workflow. `/actions/runs` ignores a `workflow_id` query, so
     * filtering has to go through the workflow-scoped endpoint.
     */
    suspend fun workflowRuns(
        owner: String,
        repo: String,
        page: Int,
        workflowId: Long? = null,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<WorkflowRun>> {
        val path = if (workflowId != null) {
            "/repos/$owner/$repo/actions/workflows/$workflowId/runs"
        } else {
            "/repos/$owner/$repo/actions/runs"
        }
        val response = GitHubClient.get(
            path,
            WorkflowRuns.serializer(),
            query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
            cacheOnly = cacheOnly,
        )
        return ApiResponse(response.value.workflowRuns, response.links, response.scopes)
    }

    suspend fun runJobs(owner: String, repo: String, runId: Long): List<WorkflowJob> =
        GitHubClient.get(
            "/repos/$owner/$repo/actions/runs/$runId/jobs",
            WorkflowJobs.serializer(),
            query = mapOf("per_page" to "100"),
        ).value.jobs

    /** Re-runs every job of a completed run. */
    suspend fun rerunRun(owner: String, repo: String, runId: Long) =
        GitHubClient.sendNoContent(
            "POST",
            "/repos/$owner/$repo/actions/runs/$runId/rerun",
            JsonObject(emptyMap()),
        )

    /** Cancels a queued/running run. */
    suspend fun cancelRun(owner: String, repo: String, runId: Long) =
        GitHubClient.sendNoContent(
            "POST",
            "/repos/$owner/$repo/actions/runs/$runId/cancel",
            JsonObject(emptyMap()),
        )

    suspend fun discussions(
        owner: String,
        repo: String,
        page: Int,
    ): ApiResponse<List<Discussion>> = GitHubClient.get(
        "/repos/$owner/$repo/discussions",
        ListSerializer(Discussion.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    /** `null` on 404: either the discussion is gone or the repository never had Discussions on. */
    suspend fun discussion(owner: String, repo: String, number: Int): Discussion? = try {
        GitHubClient.get("/repos/$owner/$repo/discussions/$number", Discussion.serializer()).value
    } catch (e: ApiException) {
        if (e.error.status == 404) null else throw e
    }

    suspend fun discussionComments(
        owner: String,
        repo: String,
        number: Int,
        page: Int,
    ): ApiResponse<List<DiscussionComment>> = GitHubClient.get(
        "/repos/$owner/$repo/discussions/$number/comments",
        ListSerializer(DiscussionComment.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
    )

    // ---------------------------------------------------------------- events

    /** Same page size the rest of the app uses; recorded rows already come 30 per page. */
    const val EVENTS_PER_PAGE = 30

    suspend fun receivedEvents(
        login: String,
        page: Int,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Event>> = events("/users/$login/received_events", page, cacheOnly)

    suspend fun userEvents(
        login: String,
        page: Int,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Event>> = events("/users/$login/events", page, cacheOnly)

    suspend fun repoEvents(
        owner: String,
        repo: String,
        page: Int,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Event>> = events("/repos/$owner/$repo/events", page, cacheOnly)

    private suspend fun events(
        path: String,
        page: Int,
        cacheOnly: Boolean,
    ): ApiResponse<List<Event>> = GitHubClient.get(
        path,
        ListSerializer(Event.serializer()),
        query = mapOf("page" to "$page", "per_page" to "$EVENTS_PER_PAGE"),
        cacheOnly = cacheOnly,
    )

    private const val INTERACTIONS_QUERY = """
        query GitRehubInteractions {
          viewer {
            login
            repositories(first: 15, orderBy: {field: PUSHED_AT, direction: DESC}, ownerAffiliations: OWNER) {
              nodes {
                nameWithOwner
                stargazers(first: 10, orderBy: {field: STARRED_AT, direction: DESC}) {
                  edges { starredAt node { login avatarUrl } }
                }
                forks(first: 10, orderBy: {field: CREATED_AT, direction: DESC}) {
                  nodes { nameWithOwner createdAt owner { login avatarUrl } }
                }
                pullRequests(first: 10, orderBy: {field: CREATED_AT, direction: DESC}) {
                  nodes { number title createdAt author { login avatarUrl } }
                }
                issues(first: 10, orderBy: {field: CREATED_AT, direction: DESC}, states: [OPEN, CLOSED]) {
                  nodes { number title createdAt author { login avatarUrl } }
                }
              }
            }
          }
        }
    """

    /**
     * "Who starred / forked / opened something on my repos", newest 15 pushed repos, 10 rows each.
     * Any failure is the caller's to swallow: this only augments the feed.
     */
    suspend fun repoInteractions(): List<Event> =
        parseInteractions(GitHubClient.graphQl(INTERACTIONS_QUERY), GitHubClient.json)

    // ---------------------------------------------------------- notifications

    /** GitHub caps this endpoint at 200 rows; there is no usable pagination beyond that. */
    const val NOTIFICATIONS_PAGE_SIZE = 200

    /**
     * `GET /notifications`. `all=true` also returns threads already read here or on the web — that
     * is what keeps the shared read state in sync with the activity feed. [since] trims the payload
     * for the background worker.
     */
    suspend fun notifications(
        all: Boolean = true,
        since: String? = null,
        cacheOnly: Boolean = false,
    ): ApiResponse<List<Notification>> = GitHubClient.get(
        "/notifications",
        ListSerializer(Notification.serializer()),
        query = buildMap {
            put("per_page", "$NOTIFICATIONS_PAGE_SIZE")
            if (all) put("all", "true")
            if (!since.isNullOrBlank()) put("since", since)
        },
        cacheOnly = cacheOnly,
    )

    /** `PUT /notifications`: every notification thread becomes read. The feed reuses it. */
    suspend fun markAllNotificationsRead() =
        GitHubClient.sendNoContent("PUT", "/notifications", JsonObject(emptyMap()))

    suspend fun markThreadRead(id: String) =
        GitHubClient.sendNoContent("PATCH", "/notifications/threads/$id", JsonObject(emptyMap()))

    /** `DELETE /notifications/threads/{id}`: back to unread. */
    suspend fun markThreadUnread(id: String) =
        GitHubClient.sendNoContent("DELETE", "/notifications/threads/$id")

    /** Mute the thread (`DELETE .../subscription`). */
    suspend fun unsubscribeThread(id: String) =
        GitHubClient.sendNoContent("DELETE", "/notifications/threads/$id/subscription")

    /** Per-thread subscription state; `ignored` is GitHub's "muted". */
    suspend fun threadSubscription(id: String): ThreadSubscription =
        GitHubClient.get(
            "/notifications/threads/$id/subscription",
            ThreadSubscription.serializer(),
        ).value

    /** `PUT .../subscription`: only the `ignored` flag is settable per thread. */
    suspend fun setThreadIgnored(id: String, ignored: Boolean) =
        GitHubClient.sendNoContent(
            "PUT",
            "/notifications/threads/$id/subscription",
            buildJsonObject { put("ignored", JsonPrimitive(ignored)) },
        )

    /** `latest_comment_url` is absolute; a mock build must still hit the mock host (see [GitHubClient.getUrl]). */
    suspend fun commentAt(url: String): Comment =
        GitHubClient.getUrl(url, Comment.serializer()).value

    /**
     * `PATCH /user`. Every editable field is sent, so clearing one really clears it.
     * Fine-grained tokens usually lack the `user` scope and answer 403.
     */
    suspend fun updateProfile(fields: Map<String, String>): ApiResponse<User> =
        GitHubClient.send("PATCH", "/user", buildJsonObject {
            fields.forEach { (name, value) -> put(name, JsonPrimitive(value.trim())) }
        }, User.serializer())

    private const val CONTRIBUTIONS_QUERY = """
        query Contributions(${'$'}login: String!, ${'$'}from: DateTime!, ${'$'}to: DateTime!) {
          user(login: ${'$'}login) {
            contributionsCollection(from: ${'$'}from, to: ${'$'}to) {
              contributionCalendar {
                totalContributions
                weeks {
                  contributionDays { date contributionCount contributionLevel }
                }
              }
            }
          }
        }
    """

    /** GraphQL's own scope is `read:user` for a token; the REST token scope is `user`. */
    suspend fun contributions(login: String, from: String, to: String): ContributionCalendar {
        val data = GitHubClient.graphQl(
            CONTRIBUTIONS_QUERY,
            buildJsonObject {
                put("login", JsonPrimitive(login))
                put("from", JsonPrimitive(from))
                put("to", JsonPrimitive(to))
            },
        )
        return GitHubClient.json.decodeFromJsonElement(ContributionEnvelope.serializer(), data).user
            ?.contributionsCollection?.contributionCalendar
            ?: ContributionCalendar()
    }

    @Serializable
    private data class ContributionEnvelope(val user: ContributionUser? = null)

    @Serializable
    private data class ContributionUser(
        @SerialName("contributionsCollection")
        val contributionsCollection: ContributionCollection? = null,
    )

    @Serializable
    private data class ContributionCollection(
        @SerialName("contributionCalendar")
        val contributionCalendar: ContributionCalendar? = null,
    )
}

/** `Link: rel="next"` is the real signal; a full page is the fallback for servers that omit it. */
fun ApiResponse<List<Event>>.hasMoreEvents(): Boolean =
    links.next != null || value.size >= GitHubApi.EVENTS_PER_PAGE

/** Same rule for every other paginated list. */
fun <T> ApiResponse<List<T>>.hasMore(pageSize: Int = GitHubApi.PAGE_SIZE): Boolean =
    links.next != null || value.size >= pageSize

/** Request body for `PUT /repos/{o}/{r}/subscription`; the API exposes only these two booleans. */
internal fun watchSubscriptionBody(level: WatchLevel): JsonObject = buildJsonObject {
    put("subscribed", JsonPrimitive(level == WatchLevel.ALL))
    put("ignored", JsonPrimitive(level == WatchLevel.IGNORED))
}

/** Search asks for `per_page=PAGE_SIZE` too, so the same full-page guess works. */
fun <T> ApiResponse<SearchResult<T>>.hasMoreSearch(pageSize: Int = GitHubApi.PAGE_SIZE): Boolean =
    links.next != null || value.items.size >= pageSize
