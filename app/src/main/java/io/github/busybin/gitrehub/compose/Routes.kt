package io.github.busybin.gitrehub.compose

import android.net.Uri

/** Every navigation destination of the modern UI, in one place. */
object Routes {
    const val LOGIN = "login"
    const val ADD_ACCOUNT = "add-account"
    const val DEVICE_LOGIN = "device-login"
    const val DEVICE_LOGIN_ADD = "device-login-add"
    const val ACCOUNTS = "accounts"
    const val FEED = "feed"
    const val REPOS = "repos"
    const val MY_WORK = "my-work"
    const val TRENDING = "trending"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val USER = "user/{login}"
    const val SEARCH = "search"
    const val GISTS = "gists?login={login}"
    const val GIST = "gist/{id}"
    const val GIST_EDIT = "gist-edit?id={id}"
    const val SHARE = "share"
    const val DRAFTS = "drafts"
    const val SAVED = "saved"
    const val SAVED_THREAD = "saved-thread/{owner}/{repo}/{number}"
    const val SETTINGS = "settings"
    const val REPO = "repo/{owner}/{name}"
    const val ISSUES = "issues/{owner}/{repo}?kind={kind}"
    const val ISSUE = "issue/{owner}/{repo}/{number}"
    const val PR_FILES = "pr-files/{owner}/{repo}/{number}"
    const val NEW_ISSUE = "new-issue/{owner}/{repo}"
    const val NEW_PULL = "new-pull/{owner}/{repo}?base={base}&head={head}"
    const val COMMIT = "commit/{owner}/{repo}?sha={sha}"
    const val COMMITS = "commits/{owner}/{repo}?ref={ref}"
    const val RELEASES = "releases/{owner}/{repo}"
    const val RELEASE_EDIT = "release-edit/{owner}/{repo}?id={id}"
    const val COMPARE = "compare/{owner}/{repo}?base={base}&head={head}"
    const val DISCUSSIONS = "discussions/{owner}/{repo}"
    const val DISCUSSION = "discussion/{owner}/{repo}/{number}"
    const val NEW_DISCUSSION = "new-discussion/{owner}/{repo}"
    const val LABELS = "labels/{owner}/{repo}"
    const val MILESTONES = "milestones/{owner}/{repo}"
    const val ACTIONS = "actions/{owner}/{repo}"
    const val RUN_LOGS = "run-logs/{owner}/{repo}/{runId}"
    const val BRANCHES = "branches/{owner}/{repo}?kind={kind}"
    const val STARGAZERS = "stargazers/{owner}/{repo}"
    const val WATCHERS = "watchers/{owner}/{repo}"
    const val FORKS = "forks/{owner}/{repo}"
    const val CONTRIBUTORS = "contributors/{owner}/{repo}"
    const val REPO_ACTIVITY = "repo-activity/{owner}/{repo}"
    const val INSIGHTS = "insights/{owner}/{repo}"
    const val CODE = "code/{owner}/{repo}?ref={ref}&path={path}"
    const val FILE = "file/{owner}/{repo}?path={path}&ref={ref}"
    const val FILE_HISTORY = "file-history/{owner}/{repo}?path={path}&ref={ref}"
    const val EDIT_FILE = "edit-file/{owner}/{repo}?path={path}&ref={ref}"
    const val CODE_SEARCH = "code-search/{owner}/{repo}?ref={ref}"

    fun repo(owner: String, name: String) = "repo/$owner/$name"

    fun user(login: String) = "user/${Uri.encode(login)}"

    /** `null` opens the signed-in account's own gists (secret ones included). */
    fun gists(login: String? = null) = "gists?login=${Uri.encode(login.orEmpty())}"

    fun gist(id: String) = "gist/${Uri.encode(id)}"

    fun gistEdit(id: String? = null) = "gist-edit?id=${Uri.encode(id.orEmpty())}"

    fun releases(owner: String, repo: String) = "releases/$owner/$repo"

    fun releaseEdit(owner: String, repo: String, id: Long? = null) =
        "release-edit/$owner/$repo?id=${id ?: 0}"

    fun compare(owner: String, repo: String, base: String, head: String) =
        "compare/$owner/$repo?base=${Uri.encode(base)}&head=${Uri.encode(head)}"

    fun discussions(owner: String, repo: String) = "discussions/$owner/$repo"

    fun discussion(owner: String, repo: String, number: Int) =
        "discussion/$owner/$repo/$number"

    fun newDiscussion(owner: String, repo: String) = "new-discussion/$owner/$repo"

    fun actions(owner: String, repo: String) = "actions/$owner/$repo"

    fun labels(owner: String, repo: String) = "labels/$owner/$repo"

    fun milestones(owner: String, repo: String) = "milestones/$owner/$repo"

    fun runLogs(owner: String, repo: String, runId: Long) = "run-logs/$owner/$repo/$runId"

    fun issues(owner: String, repo: String, kind: String = "issue") =
        "issues/$owner/$repo?kind=$kind"

    fun issue(owner: String, repo: String, number: Int) = "issue/$owner/$repo/$number"

    fun savedThread(owner: String, repo: String, number: Int) =
        "saved-thread/$owner/$repo/$number"

    fun prFiles(owner: String, repo: String, number: Int) = "pr-files/$owner/$repo/$number"

    fun newIssue(owner: String, repo: String) = "new-issue/$owner/$repo"

    fun newPull(owner: String, repo: String, base: String = "", head: String = "") =
        "new-pull/$owner/$repo?base=${Uri.encode(base)}&head=${Uri.encode(head)}"

    fun commit(owner: String, repo: String, sha: String) =
        "commit/$owner/$repo?sha=${Uri.encode(sha)}"

    fun commits(owner: String, repo: String, ref: String) =
        "commits/$owner/$repo?ref=${Uri.encode(ref)}"

    fun branches(owner: String, repo: String, kind: String = "branch") =
        "branches/$owner/$repo?kind=$kind"

    fun stargazers(owner: String, repo: String) = "stargazers/$owner/$repo"
    fun watchers(owner: String, repo: String) = "watchers/$owner/$repo"

    fun forks(owner: String, repo: String) = "forks/$owner/$repo"

    fun contributors(owner: String, repo: String) = "contributors/$owner/$repo"

    fun repoActivity(owner: String, repo: String) = "repo-activity/$owner/$repo"

    fun insights(owner: String, repo: String) = "insights/$owner/$repo"

    fun code(owner: String, repo: String, ref: String? = null, path: String = "") =
        "code/$owner/$repo?ref=${Uri.encode(ref.orEmpty())}&path=${Uri.encode(path)}"

    fun file(owner: String, repo: String, path: String, ref: String? = null) =
        "file/$owner/$repo?path=${Uri.encode(path)}&ref=${Uri.encode(ref.orEmpty())}"

    fun fileHistory(owner: String, repo: String, path: String, ref: String? = null) =
        "file-history/$owner/$repo?path=${Uri.encode(path)}&ref=${Uri.encode(ref.orEmpty())}"

    fun editFile(owner: String, repo: String, path: String, ref: String? = null) =
        "edit-file/$owner/$repo?path=${Uri.encode(path)}&ref=${Uri.encode(ref.orEmpty())}"

    fun codeSearch(owner: String, repo: String, ref: String? = null) =
        "code-search/$owner/$repo?ref=${Uri.encode(ref.orEmpty())}"
}
