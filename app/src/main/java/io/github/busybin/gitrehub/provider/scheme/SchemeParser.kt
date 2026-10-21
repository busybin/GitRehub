package io.github.busybin.gitrehub.provider.scheme

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.compose.Routes
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.helper.InputHelper.isEmpty

/**
 * Turns a github.com URL into a route in the Compose app.
 *
 * This used to be a 630-line dispatcher that built an `Intent` for each of the classic
 * activities (RepoPagerActivity, IssuePagerActivity, CommitPagerActivity, …). Those are gone, so
 * all that is left to decide is *which screen* a link means — the Compose router already knows
 * how to draw it.
 *
 * Anything we do not have a native screen for yet (gists, wikis, settings URLs, …) is handed to
 * the browser rather than opened somewhere that cannot show it.
 */
object SchemeParser {

    /** Route to open, read by [MainComposeActivity]. */
    const val EXTRA_NAVIGATE = "io.github.busybin.gitrehub.NAVIGATE"

    /** GitHub's own site paths; without this, `github.com/settings/profile` parses as owner=settings. */
    private val RESERVED_OWNERS = setOf(
        "settings", "notifications", "explore", "marketplace", "topics", "sponsors", "orgs",
        "organizations", "apps", "features", "pricing", "enterprise", "login", "logout", "join",
        "signup", "dashboard", "search", "pulls", "issues", "new", "codespaces", "trending",
        "collections", "events", "stars", "watching", "account", "security", "contact", "site",
        "about", "blog", "models", "copilot", "users", "teams", "discussions",
    )

    @JvmStatic
    fun launchUri(context: Context, url: String) = launchUri(context, Uri.parse(url))

    @JvmStatic
    @JvmOverloads
    fun launchUri(
        context: Context,
        uri: Uri,
        @Suppress("UNUSED_PARAMETER") showRepoBtn: Boolean = false,
        @Suppress("UNUSED_PARAMETER") newDocument: Boolean = false,
    ) {
        val data = LinkParserHelper.parseReferenceSymbols(uri)
        val route = routeFor(data)
        if (route != null) {
            val intent = Intent(context, MainComposeActivity::class.java)
                .putExtra(EXTRA_NAVIGATE, route)
            if (context is Application) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        // No native screen for it: the browser is the honest answer. A plain VIEW — NOT a Custom
        // Tab pinned to Chrome, which ignores the user's default browser (see BrowserLauncher).
        BrowserLauncher.open(context, data.toString())
    }

    /**
     * `github.com/owner/repo[/…]` → a [Routes] route, or null when we have no screen for it.
     *
     * Only the shapes people actually paste are handled; everything else falls through to the
     * browser rather than guessing at a screen that would show the wrong thing.
     */
    fun routeFor(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host != "github.com" && host != "www.github.com") return null

        val s = uri.pathSegments.filter { it.isNotEmpty() }

        if (s.size == 1 && s[0] == "notifications") return Routes.NOTIFICATIONS

        if (s.size < 2) return null
        val owner = s[0]
        val repo = s[1]
        if (owner.lowercase() in RESERVED_OWNERS) return null

        // /owner/repo
        if (s.size == 2) return Routes.repo(owner, repo)

        val third = s[2]
        // /owner/repo/{issues,pulls,releases,branches,tags,discussions,actions}
        if (s.size == 3) {
            return when (third) {
                "issues", "pulls" -> Routes.issues(owner, repo)
                "releases" -> Routes.releases(owner, repo)
                "branches" -> Routes.branches(owner, repo, "branch")
                "tags" -> Routes.branches(owner, repo, "tag")
                "discussions" -> Routes.discussions(owner, repo)
                "actions" -> Routes.actions(owner, repo)
                else -> null
            }
        }

        // /owner/repo/discussions/123
        if (s.size == 4 && third == "discussions") {
            val number = s[3].toIntOrNull() ?: return null
            return Routes.discussion(owner, repo, number)
        }

        // /owner/repo/actions/runs/123/job/456 and /workflows/ci.yml all land on the actions list.
        if (third == "actions") return Routes.actions(owner, repo)

        // GitHub emits both /issues/123 and /pull(s)/123.
        if (s.size == 4 && (third == "issues" || third == "pull" || third == "pulls")) {
            val number = s[3].toIntOrNull() ?: return null
            return Routes.issue(owner, repo, number)
        }

        // /owner/repo/releases/latest and releases/tag/v1.2.3 → release list.
        if (third == "releases" && (s.size == 4 || s.size == 5)) return Routes.releases(owner, repo)

        // labels/bug and milestone/3 are filtered issue lists on GitHub.
        if (s.size == 4 && (third == "labels" || third == "milestone")) {
            return Routes.issues(owner, repo)
        }

        // /owner/repo/tree/{ref}[/path…]  ·  /owner/repo/blob/{ref}[/path…]
        if (third == "tree" || third == "blob") {
            val ref = s.getOrNull(3).orEmpty()
            if (isEmpty(ref)) return null
            val path = s.drop(4).joinToString("/")
            return if (third == "tree" && path.isEmpty()) {
                Routes.code(owner, repo, ref)
            } else if (third == "tree") {
                Routes.code(owner, repo, ref, path)
            } else {
                Routes.file(owner, repo, path, ref)
            }
        }

        return null
    }
}
