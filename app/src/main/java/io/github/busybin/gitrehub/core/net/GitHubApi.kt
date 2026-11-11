package io.github.busybin.gitrehub.core.net

import io.github.busybin.gitrehub.core.model.Branch
import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.core.model.CheckRuns
import io.github.busybin.gitrehub.core.model.CodeFrequency
import io.github.busybin.gitrehub.core.model.AutoMergeRequest
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.model.CommitFile
import io.github.busybin.gitrehub.core.model.CompareResult
import io.github.busybin.gitrehub.core.model.ContributionCalendar
import io.github.busybin.gitrehub.core.model.ContributorStat
import io.github.busybin.gitrehub.core.model.CreatedPullRequest
import io.github.busybin.gitrehub.core.model.DependabotAlert
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionAnswerInfo
import io.github.busybin.gitrehub.core.model.DiscussionCategoryRef
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.core.model.DiscussionSetup
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.GeneratedNotes
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.GistComment
import io.github.busybin.gitrehub.core.model.GistRevision
import io.github.busybin.gitrehub.core.model.GitTree
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
import io.github.busybin.gitrehub.core.model.RepoOverview
import io.github.busybin.gitrehub.core.model.RepoSubscription
import io.github.busybin.gitrehub.core.model.Review
import io.github.busybin.gitrehub.core.model.ReviewComment
import io.github.busybin.gitrehub.core.model.ReviewThread
import io.github.busybin.gitrehub.core.model.SearchCode
import io.github.busybin.gitrehub.core.model.SearchResult
import io.github.busybin.gitrehub.core.model.StatsOutcome
import io.github.busybin.gitrehub.core.model.Tag
import io.github.busybin.gitrehub.core.model.ThreadSubscription
import io.github.busybin.gitrehub.core.model.TimelineEvent
import io.github.busybin.gitrehub.core.model.TrafficPath
import io.github.busybin.gitrehub.core.model.TrafficReferrer
import io.github.busybin.gitrehub.core.model.TrafficSeries
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
import io.github.busybin.gitrehub.core.model.parseRepoWatchers
import io.github.busybin.gitrehub.core.model.parseWatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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

    /** `POST /gists`. [files] maps a filename to its text; a gist needs at least one entry. */
    suspend fun createGist(
        description: String,
        public: Boolean,
        files: Map<String, String>,
    ): Gist = GitHubClient.send(
        "POST",
        "/gists",
        WriteBodies.gist(description, public, files),
        Gist.serializer(),
    ).value

    /** `PATCH /gists/{id}`: files left out keep their text, a `null` value deletes them. */
    suspend fun updateGist(
        id: String,
        description: String,
        files: Map<String, String?>,
    ): Gist = GitHubClient.send(
        "PATCH",
        "/gists/$id",
        WriteBodies.gist(description, public = false, files = files),
        Gist.serializer(),
    ).value

    private suspend fun gists(path: String, page: Int): ApiResponse<List<Gist>> =
        GitHubClient.get(
            path,
            ListSerializer(Gist.serializer()),
            query = mapOf("page" to "$page", "per_page" to "$PAGE_SIZE"),
        )

    /** `GET /gists/{id}/comments`; paginated like every list endpoint. */
    suspend fun gistComments(id: String, page: Int): ApiResponse<List<GistComment>> = GitHubClient.get(
        "/gists/$id/comments",
        ListSerializer(GistComment.serializer()),
        query = mapOf("page" to "$page", "per_page" to "100"),
    )

    suspend fun createGistComment(id: String, body: String): GistComment = GitHubClient.send(
        "POST",
        "/gists/$id/comments",
        buildJsonObject { put("body", JsonPrimitive(body)) },
        GistComment.serializer(),
    ).value

    /** `GET /gists/{id}/commits`: newest first, the sha in [GistRevision.version]. */
    suspend fun gistRevisions(id: String): List<GistRevision> = GitHubClient.get(
        "/gists/$id/commits",
        ListSerializer(GistRevision.serializer()),
        query = mapOf("per_page" to "100"),
    ).value

    /** `PUT/DELETE /gists/{id}/star`; the GET answers 204 when starred and 404 when not. */
    suspend fun gistStarred(id: String): Boolean = runCatching {
        GitHubClient.sendNoContent("GET", "/gists/$id/star")
    }.isSuccess

    suspend fun setGistStarred(id: String, starred: Boolean) =
        GitHubClient.sendNoContent(if (starred) "PUT" else "DELETE", "/gists/$id/star")

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

    /**
     * `PATCH /repos/{o}/{r}`: description and homepage. Topics live on their own endpoint, because
     * GitHub only accepts them through `PUT /topics` (and the PATCH ignores the field silently).
     */
    suspend fun editRepo(
        owner: String,
        repo: String,
        description: String,
        homepage: String,
        topics: List<String>?,
    ): Repo {
        val updated = GitHubClient.send(
            "PATCH",
            "/repos/$owner/$repo",
            WriteBodies.repoEdit(description.trim(), homepage.trim()),
            Repo.serializer(),
        ).value
        if (topics == null) return updated
        val names = GitHubClient.send(
            "PUT",
            "/repos/$owner/$repo/topics",
            WriteBodies.topics(topics.map { it.trim() }.filter { it.isNotEmpty() }),
            RepoTopicsEnvelope.serializer(),
        ).value.names
        return updated.copy(topics = names)
    }

    @Serializable
    private data class RepoTopicsEnvelope(val names: List<String> = emptyList())

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

    /**
     * State changes of a thread (`closed`, `labeled`, `referenced`, ...). Comments arrive from
     * their own endpoint; `commented`/`reviewed` entries are dropped by the caller.
     * Failures are non-fatal: the detail screen keeps working without the timeline.
     */
    suspend fun issueTimeline(
        owner: String,
        repo: String,
        number: Int,
        page: Int,
    ): ApiResponse<List<TimelineEvent>> = GitHubClient.get(
        "/repos/$owner/$repo/issues/$number/timeline",
        ListSerializer(TimelineEvent.serializer()),
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

    // ------------------------------------------------- pull request review completion

    /** `POST /pulls/{n}/requested_reviewers`. Adding someone already requested is a no-op on GitHub. */
    suspend fun requestReviewers(
        owner: String,
        repo: String,
        number: Int,
        reviewers: List<String>,
    ): PullRequest {
        if (reviewers.isEmpty()) return pullRequest(owner, repo, number)
        return GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/pulls/$number/requested_reviewers",
            WriteBodies.reviewers(reviewers),
            PullRequest.serializer(),
        ).value
    }

    /** `DELETE /pulls/{n}/requested_reviewers` — the one DELETE that carries a JSON body here. */
    suspend fun removeReviewers(
        owner: String,
        repo: String,
        number: Int,
        reviewers: List<String>,
    ): PullRequest = GitHubClient.send(
        "DELETE",
        "/repos/$owner/$repo/pulls/$number/requested_reviewers",
        WriteBodies.reviewers(reviewers),
        PullRequest.serializer(),
    ).value

    /** `PUT /pulls/{n}/update-branch`: merges the base branch into the PR branch (202 Accepted). */
    suspend fun updatePullBranch(
        owner: String,
        repo: String,
        number: Int,
        expectedHeadSha: String?,
    ) = GitHubClient.sendNoContent(
        "PUT",
        "/repos/$owner/$repo/pulls/$number/update-branch",
        WriteBodies.updateBranch(expectedHeadSha),
    )

    /** `DELETE /git/refs/heads/{branch}`: remove the source branch after a merge. */
    suspend fun deleteBranch(owner: String, repo: String, branch: String) =
        GitHubClient.sendNoContent(
            "DELETE",
            "/repos/$owner/$repo/git/refs/heads/${encodePath(branch)}",
        )

    /**
     * Convert a PR to draft / mark it ready. Both are GraphQL-only mutations keyed by the PR's node
     * id, so the REST object is read first (its `node_id` is not in the lists we already hold).
     */
    suspend fun setPullDraft(owner: String, repo: String, number: Int, draft: Boolean) {
        val nodeId = pullRequest(owner, repo, number).nodeId
            ?: throw ApiException(422, "Pull request has no node id")
        val field = if (draft) "convertPullRequestToDraft" else "markPullRequestReadyForReview"
        GitHubClient.graphQl(
            "mutation SetDraft(${'$'}id: ID!) { $field(input: {pullRequestId: ${'$'}id}) { pullRequest { isDraft } } }",
            buildJsonObject { put("id", JsonPrimitive(nodeId)) },
        )
    }

    private const val AUTO_MERGE_QUERY = """
        query AutoMerge(${'$'}id: ID!) {
          node(id: ${'$'}id) { ... on PullRequest { autoMergeRequest { enabledAt mergeMethod } } }
        }
    """

    /**
     * Read the pending auto-merge of a PR. Null means "not requested"; a REST PR object cannot tell
     * us this because `auto_merge` is GraphQL-only.
     */
    suspend fun autoMergeRequest(nodeId: String?): AutoMergeRequest? {
        if (nodeId.isNullOrBlank()) return null
        val data = GitHubClient.graphQl(
            AUTO_MERGE_QUERY,
            buildJsonObject { put("id", JsonPrimitive(nodeId)) },
        )
        val request = data["node"]?.jsonObject?.get("autoMergeRequest") ?: return null
        if (request is JsonNull) return null
        return runCatching {
            GitHubClient.json.decodeFromJsonElement(AutoMergeRequest.serializer(), request)
        }.getOrNull()
    }

    /**
     * `enablePullRequestAutoMerge` / `disablePullRequestAutoMerge`. [method] is GitHub's enum
     * (`MERGE` / `SQUASH` / `REBASE`); the server rejects enabling without one.
     */
    suspend fun setAutoMerge(nodeId: String, enabled: Boolean, method: String) {
        val query = if (enabled) {
            """
            mutation EnableAuto(${'$'}id: ID!, ${'$'}method: PullRequestMergeMethod!) {
              enablePullRequestAutoMerge(input: {pullRequestId: ${'$'}id, mergeMethod: ${'$'}method}) {
                pullRequest { autoMergeRequest { enabledAt mergeMethod } }
              }
            }
            """
        } else {
            """
            mutation DisableAuto(${'$'}id: ID!) {
              disablePullRequestAutoMerge(input: {pullRequestId: ${'$'}id}) {
                pullRequest { autoMergeRequest { enabledAt mergeMethod } }
              }
            }
            """
        }
        val variables = buildJsonObject {
            put("id", JsonPrimitive(nodeId))
            if (enabled) put("method", JsonPrimitive(method))
        }
        GitHubClient.graphQl(query, variables)
    }

    private const val REVIEW_THREADS_QUERY = """
        query ReviewThreads(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
          repository(owner: ${'$'}owner, name: ${'$'}repo) {
            pullRequest(number: ${'$'}number) {
              reviewThreads(first: 100) {
                nodes {
                  id
                  path
                  line
                  isResolved
                  isOutdated
                  comments(first: 1) { nodes { body author { login avatarUrl } } }
                }
              }
            }
          }
        }
    """

    /** Review conversations of a PR. REST has no thread ids, so resolving one needs this query. */
    suspend fun reviewThreads(owner: String, repo: String, number: Int): List<ReviewThread> {
        val data = GitHubClient.graphQl(
            REVIEW_THREADS_QUERY,
            buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
                put("number", JsonPrimitive(number))
            },
        )
        return GitHubClient.json
            .decodeFromJsonElement(ReviewThreadsEnvelope.serializer(), data)
            .repository?.pullRequest?.reviewThreads?.nodes.orEmpty()
    }

    /** `resolveReviewThread` / `unresolveReviewThread`. */
    suspend fun setReviewThreadResolved(threadId: String, resolved: Boolean) {
        val field = if (resolved) "resolveReviewThread" else "unresolveReviewThread"
        GitHubClient.graphQl(
            "mutation ResolveThread(${'$'}id: ID!) { $field(input: {threadId: ${'$'}id}) { thread { isResolved } } }",
            buildJsonObject { put("id", JsonPrimitive(threadId)) },
        )
    }

    @Serializable
    private data class ReviewThreadsEnvelope(val repository: ReviewThreadsRepo? = null)

    @Serializable
    private data class ReviewThreadsRepo(val pullRequest: ReviewThreadsPull? = null)

    @Serializable
    private data class ReviewThreadsPull(val reviewThreads: ReviewThreadsConn? = null)

    @Serializable
    private data class ReviewThreadsConn(val nodes: List<ReviewThread> = emptyList())

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

    suspend fun createComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
        token: String? = null,
    ): Comment =
        GitHubClient.send(
            "POST",
            "/repos/$owner/$repo/issues/$number/comments",
            WriteBodies.comment(body),
            Comment.serializer(),
            token = token,
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

    /** Commits pushed since [since] (ISO 8601); the digest card counts them. */
    suspend fun commitsSince(
        owner: String,
        repo: String,
        since: String,
        perPage: Int = 100,
    ): ApiResponse<List<Commit>> = GitHubClient.get(
        "/repos/$owner/$repo/commits",
        ListSerializer(Commit.serializer()),
        query = mapOf("since" to since, "per_page" to "$perPage"),
    )

    /**
     * Issue/PR activity since [since]. `per_page=1` keeps the payload tiny: the exact total comes
     * from the `Link` header's last page, which is what the digest card needs.
     */
    suspend fun issuesSince(
        owner: String,
        repo: String,
        since: String,
    ): ApiResponse<List<Issue>> = GitHubClient.get(
        "/repos/$owner/$repo/issues",
        ListSerializer(Issue.serializer()),
        query = mapOf(
            "state" to "all",
            "since" to since,
            "sort" to "updated",
            "direction" to "desc",
            "per_page" to "1",
        ),
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

    /** `POST /releases/generate-notes`: a changelog built from the PRs merged since the last tag. */
    suspend fun generateReleaseNotes(
        owner: String,
        repo: String,
        tagName: String,
        targetCommitish: String? = null,
        previousTagName: String? = null,
    ): GeneratedNotes = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/releases/generate-notes",
        WriteBodies.generateNotes(tagName, targetCommitish, previousTagName),
        GeneratedNotes.serializer(),
    ).value

    /** `POST /releases`: creates a draft or publishes immediately. */
    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        targetCommitish: String?,
        name: String,
        body: String,
        draft: Boolean,
        prerelease: Boolean,
    ): Release = GitHubClient.send(
        "POST",
        "/repos/$owner/$repo/releases",
        WriteBodies.release(
            tagName = tagName,
            targetCommitish = targetCommitish,
            name = name,
            body = body,
            draft = draft,
            prerelease = prerelease,
        ),
        Release.serializer(),
    ).value

    /**
     * `PATCH /releases/{id}`. Every editable field is sent, so clearing the name/body really clears
     * it; `tagName` is only sent when it changed (GitHub rejects a retag on a published release).
     */
    suspend fun updateRelease(
        owner: String,
        repo: String,
        id: Long,
        tagName: String?,
        targetCommitish: String?,
        name: String,
        body: String,
        draft: Boolean,
        prerelease: Boolean,
    ): Release = GitHubClient.send(
        "PATCH",
        "/repos/$owner/$repo/releases/$id",
        WriteBodies.release(
            tagName = tagName,
            targetCommitish = targetCommitish,
            name = name,
            body = body,
            draft = draft,
            prerelease = prerelease,
        ),
        Release.serializer(),
    ).value

    suspend fun deleteRelease(owner: String, repo: String, id: Long) =
        GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/releases/$id")

    suspend fun deleteReleaseAsset(owner: String, repo: String, assetId: Long) =
        GitHubClient.sendNoContent("DELETE", "/repos/$owner/$repo/releases/assets/$assetId")

    /** `GET /compare/{base}...{head}`: commits and files between two refs. */
    suspend fun compare(owner: String, repo: String, base: String, head: String): CompareResult =
        GitHubClient.get(
            "/repos/$owner/$repo/compare/${encodePath("$base...$head")}",
            CompareResult.serializer(),
        ).value

    /**
     * Directory contents (`GET .../contents/{path}`) as a raw answer: the endpoint returns an array
     * for a directory and an object for a file, and GitHub rejects a malformed path with 404.
     */
    suspend fun contents(owner: String, repo: String, path: String, ref: String?): RawResponse =
        GitHubClient.getRaw(
            "/repos/$owner/$repo/contents/${encodePath(path)}",
            query = if (ref.isNullOrBlank()) emptyMap() else mapOf("ref" to ref),
        )

    /** Whole file list of one ref in one call; used by the code browser's file finder. */
    suspend fun gitTree(owner: String, repo: String, ref: String?): GitTree =
        GitHubClient.get(
            "/repos/$owner/$repo/git/trees/${encodePath(ref.orEmpty().ifBlank { "HEAD" })}",
            GitTree.serializer(),
            query = mapOf("recursive" to "1"),
        ).value

    @Serializable
    private data class GitBlob(val content: String = "", val encoding: String = "")

    /** `GET /git/blobs/{sha}`: the pack sync reads file bodies by blob sha. */
    suspend fun blobText(owner: String, repo: String, sha: String): String {
        val blob = GitHubClient.get(
            "/repos/$owner/$repo/git/blobs/$sha",
            GitBlob.serializer(),
        ).value
        if (blob.encoding != "base64") return blob.content
        return runCatching {
            String(android.util.Base64.decode(blob.content.replace("\n", ""), android.util.Base64.DEFAULT))
        }.getOrDefault("")
    }

    /** Raw file text, fetched from the entry's own URL (works for private repositories too). */
    suspend fun fileText(url: String): String =
        GitHubClient.getTextUrl(url, accept = "application/vnd.github.VERSION.raw")

    /**
     * Whole-repository archive for [ref] (`GET /repos/{o}/{r}/zipball/{ref}`).
     *
     * GitHub answers `302` to a signed `codeload.github.com` URL that is valid without a token, so
     * the archive arrives in one request instead of one request per file. The partial file is
     * deleted on failure; callers fall back to blob downloads.
     */
    suspend fun downloadZipball(
        owner: String,
        repo: String,
        ref: String,
        target: java.io.File,
        maxBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Long = GitHubClient.download(
        path = "/repos/$owner/$repo/zipball/${encodePath(ref)}",
        target = target,
        maxBytes = maxBytes,
        onProgress = onProgress,
    )

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

    // ------------------------------------------------- maintainer dashboard

    /** `GET /traffic/views`: needs push access; GitHub keeps 14 days. */
    suspend fun trafficViews(owner: String, repo: String): TrafficSeries =
        GitHubClient.get(
            "/repos/$owner/$repo/traffic/views",
            TrafficSeries.serializer(),
        ).value

    /** `GET /traffic/clones`; same shape as views, under the `clones` key. */
    suspend fun trafficClones(owner: String, repo: String): TrafficSeries =
        GitHubClient.get(
            "/repos/$owner/$repo/traffic/clones",
            TrafficSeries.serializer(),
        ).value

    suspend fun trafficReferrers(owner: String, repo: String): List<TrafficReferrer> =
        GitHubClient.get(
            "/repos/$owner/$repo/traffic/popular/referrers",
            ListSerializer(TrafficReferrer.serializer()),
        ).value

    suspend fun trafficPaths(owner: String, repo: String): List<TrafficPath> =
        GitHubClient.get(
            "/repos/$owner/$repo/traffic/popular/paths",
            ListSerializer(TrafficPath.serializer()),
        ).value

    /**
     * `GET /dependabot/alerts`. A classic token without `security_events` (and a fine-grained token
     * without Dependabot read) gets a 403; callers show "not available" instead of failing the page.
     */
    suspend fun dependabotAlerts(owner: String, repo: String): List<DependabotAlert> =
        GitHubClient.get(
            "/repos/$owner/$repo/dependabot/alerts",
            ListSerializer(DependabotAlert.serializer()),
            query = mapOf("state" to "open", "per_page" to "100"),
        ).value

    /** Open issues untouched for [days] days, oldest first — the "stale backlog" list. */
    suspend fun staleIssues(owner: String, repo: String, days: Int): List<Issue> {
        val before = java.time.LocalDate.now().minusDays(days.toLong()).toString()
        return searchIssues(
            query = "repo:$owner/$repo is:issue is:open updated:<$before",
            page = 1,
            sort = "updated",
            order = "asc",
        ).value.items
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

    /** Re-runs only the failed jobs of a completed run (`rerun-failed-jobs`). */
    suspend fun rerunFailedJobs(owner: String, repo: String, runId: Long) =
        GitHubClient.sendNoContent(
            "POST",
            "/repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs",
            JsonObject(emptyMap()),
        )

    /**
     * `POST /actions/workflows/{id}/dispatches`: the mobile "run it now" button. GitHub answers 204
     * whether or not the workflow declared `workflow_dispatch`; a non-dispatchable workflow is a 422.
     */
    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String>,
    ) = GitHubClient.sendNoContent(
        "POST",
        "/repos/$owner/$repo/actions/workflows/$workflowId/dispatches",
        WriteBodies.dispatch(ref, inputs),
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

    private const val DISCUSSION_ANSWER_QUERY = """
        query DiscussionAnswer(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
          repository(owner: ${'$'}owner, name: ${'$'}repo) {
            discussion(number: ${'$'}number) {
              id
              answer { ... on DiscussionComment { fullDatabaseId } }
              comments(first: 100) { nodes { id fullDatabaseId } }
            }
          }
        }
    """

    /**
     * Answer state of a discussion. REST does not expose it, GraphQL does; comments are matched by
     * their database id so the ids already in the screen can be reused.
     */
    suspend fun discussionAnswerInfo(owner: String, repo: String, number: Int): DiscussionAnswerInfo? {
        val data = GitHubClient.graphQl(
            DISCUSSION_ANSWER_QUERY,
            buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
                put("number", JsonPrimitive(number))
            },
        )
        val discussion = data["repository"]?.jsonObject?.get("discussion")
            ?.takeIf { it !is JsonNull }?.jsonObject ?: return null
        val discussionId = discussion["id"]?.asStringOrNull() ?: return null
        val answerId = discussion["answer"]?.takeIf { it !is JsonNull }
            ?.jsonObject?.get("fullDatabaseId")?.asStringOrNull()?.toLongOrNull()
        val nodeIds = discussion["comments"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { node ->
                val obj = node.jsonObject
                val databaseId = obj["fullDatabaseId"]?.asStringOrNull()?.toLongOrNull()
                val nodeId = obj["id"]?.asStringOrNull()
                if (databaseId != null && nodeId != null) databaseId to nodeId else null
            }
            .toMap()
        return DiscussionAnswerInfo(discussionId, answerId, nodeIds)
    }

    /** `markDiscussionCommentAsAnswer` / `unmarkDiscussionCommentAsAnswer`. */
    suspend fun setDiscussionAnswer(commentNodeId: String, answer: Boolean) {
        val field = if (answer) "markDiscussionCommentAsAnswer" else "unmarkDiscussionCommentAsAnswer"
        GitHubClient.graphQl(
            "mutation SetAnswer(${'$'}id: ID!) { $field(input: {id: ${'$'}id}) { discussionComment { isAnswer } } }",
            buildJsonObject { put("id", JsonPrimitive(commentNodeId)) },
        )
    }

    private const val DISCUSSION_NODE_QUERY = """
        query DiscussionNode(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
          repository(owner: ${'$'}owner, name: ${'$'}repo) {
            discussion(number: ${'$'}number) { id }
          }
        }
    """

    /** GraphQL node id of one discussion; the REST object does not carry it, mutations need it. */
    suspend fun discussionNodeId(owner: String, repo: String, number: Int): String? {
        val data = GitHubClient.graphQl(
            DISCUSSION_NODE_QUERY,
            buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
                put("number", JsonPrimitive(number))
            },
        )
        return data["repository"]?.jsonObject?.get("discussion")
            ?.takeIf { it !is JsonNull }?.jsonObject?.get("id")?.asStringOrNull()
    }

    /** `addDiscussionComment`. Discussions have no REST write endpoint, so this is GraphQL only. */
    suspend fun addDiscussionComment(owner: String, repo: String, number: Int, body: String) {
        val discussionId = discussionNodeId(owner, repo, number)
            ?: throw ApiException(404, "Discussion #$number not found")
        GitHubClient.graphQl(
            "mutation AddDiscussionComment(${'$'}discussion: ID!, ${'$'}body: String!) { " +
                "addDiscussionComment(input: {discussionId: ${'$'}discussion, body: ${'$'}body}) " +
                "{ comment { id } } }",
            buildJsonObject {
                put("discussion", JsonPrimitive(discussionId))
                put("body", JsonPrimitive(body))
            },
        )
    }

    private const val DISCUSSION_SETUP_QUERY = """
        query DiscussionSetup(${'$'}owner: String!, ${'$'}repo: String!) {
          repository(owner: ${'$'}owner, name: ${'$'}repo) {
            id
            discussionCategories(first: 25) { nodes { id name } }
          }
        }
    """

    /** Repository node id plus the categories a new discussion can be filed under. */
    suspend fun discussionSetup(owner: String, repo: String): DiscussionSetup? {
        val data = GitHubClient.graphQl(
            DISCUSSION_SETUP_QUERY,
            buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
            },
        )
        val repository = data["repository"]?.takeIf { it !is JsonNull }?.jsonObject ?: return null
        val repositoryId = repository["id"]?.asStringOrNull() ?: return null
        val categories = repository["discussionCategories"]?.jsonObject
            ?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { node ->
                val obj = node.jsonObject
                val id = obj["id"]?.asStringOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.asStringOrNull() ?: return@mapNotNull null
                DiscussionCategoryRef(id, name)
            }
        return DiscussionSetup(repositoryId, categories)
    }

    /**
     * `createDiscussion`; answers the new discussion's number so the UI can open it. Throwing on a
     * disabled category or a missing title is GraphQL's own error, which [GitHubClient.graphQl]
     * already turns into an [ApiException].
     */
    suspend fun createDiscussion(
        repositoryId: String,
        categoryId: String,
        title: String,
        body: String,
    ): Int {
        val data = GitHubClient.graphQl(
            "mutation CreateDiscussion(${'$'}repository: ID!, ${'$'}category: ID!, " +
                "${'$'}title: String!, ${'$'}body: String!) { " +
                "createDiscussion(input: {repositoryId: ${'$'}repository, categoryId: ${'$'}category, " +
                "title: ${'$'}title, body: ${'$'}body}) { discussion { number } } }",
            buildJsonObject {
                put("repository", JsonPrimitive(repositoryId))
                put("category", JsonPrimitive(categoryId))
                put("title", JsonPrimitive(title))
                put("body", JsonPrimitive(body))
            },
        )
        val number = data["createDiscussion"]?.jsonObject?.get("discussion")?.jsonObject
            ?.get("number")?.asStringOrNull()?.toIntOrNull()
        return number ?: throw ApiException(422, "GraphQL: empty response")
    }

    /** GraphQL scalars arrive as strings, numbers or null; this keeps the parsing in one place. */
    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull

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
                watchers(first: 100) {
                  totalCount
                  nodes { login avatarUrl }
                }
              }
            }
          }
        }
    """

    /**
     * "Who starred / forked / opened something on my repos" plus the current watcher list, newest
     * 15 pushed repos, 10 interaction rows and up to 100 watchers each.
     *
     * Watchers are a snapshot, not an event stream: GitHub has no "started watching" event and no
     * timestamp, so the caller diffs the list against the one it saved last time. Any failure is
     * the caller's to swallow: this only augments the feed.
     */
    suspend fun repoOverview(): RepoOverview {
        val root = GitHubClient.graphQl(INTERACTIONS_QUERY)
        return RepoOverview(
            interactions = parseInteractions(root, GitHubClient.json),
            watchers = parseRepoWatchers(root, GitHubClient.json),
        )
    }

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
        token: String? = null,
    ): ApiResponse<List<Notification>> = GitHubClient.get(
        "/notifications",
        ListSerializer(Notification.serializer()),
        query = buildMap {
            put("per_page", "$NOTIFICATIONS_PAGE_SIZE")
            if (all) put("all", "true")
            if (!since.isNullOrBlank()) put("since", since)
        },
        token = token,
        cacheOnly = cacheOnly,
    )

    /** `PUT /notifications`: every notification thread becomes read. The feed reuses it. */
    suspend fun markAllNotificationsRead() =
        GitHubClient.sendNoContent("PUT", "/notifications", JsonObject(emptyMap()))

    suspend fun markThreadRead(id: String, token: String? = null) =
        GitHubClient.sendNoContent(
            "PATCH",
            "/notifications/threads/$id",
            JsonObject(emptyMap()),
            token = token,
        )

    /** `DELETE /notifications/threads/{id}`: back to unread. */
    suspend fun markThreadUnread(id: String) =
        GitHubClient.sendNoContent("DELETE", "/notifications/threads/$id")

    /** Mute the thread (`DELETE .../subscription`). */
    suspend fun unsubscribeThread(id: String, token: String? = null) =
        GitHubClient.sendNoContent(
            "DELETE",
            "/notifications/threads/$id/subscription",
            token = token,
        )

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
    suspend fun commentAt(url: String, token: String? = null): Comment =
        GitHubClient.getUrl(url, Comment.serializer(), token).value

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
