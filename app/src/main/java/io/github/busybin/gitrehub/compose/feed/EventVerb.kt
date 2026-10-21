package io.github.busybin.gitrehub.compose.feed

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventType

/**
 * What an event did, as a localizable verb: the UI renders `stringResource(verb.labelRes)`.
 *
 * `payload.action` matters because GitHub reports opened/closed/merged/reopened PRs as the same
 * `PullRequestEvent`, so the legacy English resources always say "opened". Closed is split into
 * merged vs closed via `payload.merged`, which the trimmed event payload often omits.
 */
enum class EventVerb(@StringRes val labelRes: Int) {
    NEW(R.string.m3_verb_new),
    STAR(R.string.m3_verb_star),
    FORK(R.string.m3_verb_fork),
    CREATED(R.string.m3_verb_created),
    DELETED(R.string.m3_verb_deleted),
    PUSHED(R.string.m3_verb_pushed),
    ISSUE_OPENED(R.string.m3_verb_issue_opened),
    ISSUE_CLOSED(R.string.m3_verb_issue_closed),
    ISSUE_REOPENED(R.string.m3_verb_issue_reopened),
    ISSUE_UPDATED(R.string.m3_verb_issue_updated),
    ISSUE_COMMENTED(R.string.m3_verb_issue_commented),
    COMMIT_COMMENTED(R.string.m3_verb_commit_commented),
    PR_OPENED(R.string.m3_verb_pr_opened),
    PR_MERGED(R.string.m3_verb_pr_merged),
    PR_CLOSED(R.string.m3_verb_pr_closed),
    PR_REOPENED(R.string.m3_verb_pr_reopened),
    PR_REVIEW_REQUESTED(R.string.m3_verb_pr_review_requested),
    PR_UPDATED(R.string.m3_verb_pr_updated),
    PR_REVIEWED(R.string.m3_verb_pr_reviewed),
    PR_COMMENTED(R.string.m3_verb_pr_commented),
    RELEASE(R.string.m3_verb_release),
    FOLLOWED(R.string.m3_verb_followed),
    MEMBER(R.string.m3_verb_member),
    PUBLIC(R.string.m3_verb_public),
    WIKI(R.string.m3_verb_wiki),
    GIST(R.string.m3_verb_gist),
    DOWNLOADED(R.string.m3_verb_downloaded),
    TEAM_ADDED(R.string.m3_verb_team_added),
    REPO(R.string.m3_verb_repo),
    ORG(R.string.m3_verb_org),
    PROJECT(R.string.m3_verb_project),
}

internal fun Event.verb(): EventVerb =
    type.verb(payload.action, payload.pullRequest?.merged == true)

/** `#12`; only PR/issue events carry a number. */
internal fun Event.numberLabel(): String? = when (type) {
    EventType.PullRequestEvent -> payload.pullRequest?.number?.takeIf { it > 0 }?.let { "#$it" }
    EventType.IssuesEvent -> payload.issue?.number?.takeIf { it > 0 }?.let { "#$it" }
    else -> null
}

/**
 * The second line of the card: PR/issue title, or the forked repo for fork events.
 * The trimmed event payload usually omits titles, so this is often null.
 */
internal fun Event.titleLine(): String? = when (type) {
    EventType.PullRequestEvent -> payload.pullRequest?.title
    EventType.IssuesEvent -> payload.issue?.title
    EventType.ForkEvent -> payload.forkee?.let { it.fullName ?: it.name }
    else -> null
}?.takeIf { it.isNotBlank() }

/** Badge icon of the card's right corner. */
@get:DrawableRes
internal val EventType.drawableRes: Int
    get() = when (this) {
        EventType.WatchEvent -> R.drawable.ic_star_filled
        EventType.CreateEvent, EventType.PublicEvent, EventType.RepositoryEvent -> R.drawable.ic_repo
        EventType.CommitCommentEvent,
        EventType.IssueCommentEvent,
        EventType.PullRequestReviewCommentEvent,
        -> R.drawable.ic_comment
        EventType.DownloadEvent, EventType.ReleaseEvent -> R.drawable.ic_download
        EventType.FollowEvent, EventType.MemberEvent -> R.drawable.ic_add
        EventType.ForkEvent, EventType.ForkApplyEvent -> R.drawable.ic_fork
        EventType.GistEvent -> R.drawable.ic_gists
        EventType.IssuesEvent -> R.drawable.ic_issues
        EventType.PullRequestEvent -> R.drawable.ic_pull_requests
        EventType.PullRequestReviewEvent -> R.drawable.ic_eye
        EventType.PushEvent -> R.drawable.ic_push
        EventType.TeamAddEvent,
        EventType.OrgBlockEvent,
        EventType.OrganizationEvent,
        -> R.drawable.ic_profile
        EventType.DeleteEvent -> R.drawable.ic_trash
        else -> R.drawable.ic_info_outline
    }

/** Default verb per event type. */
internal fun EventType.verb(action: String? = null, merged: Boolean = false): EventVerb = when (this) {
    EventType.WatchEvent -> EventVerb.STAR
    EventType.ForkEvent, EventType.ForkApplyEvent -> EventVerb.FORK
    EventType.CreateEvent -> EventVerb.CREATED
    EventType.DeleteEvent -> EventVerb.DELETED
    EventType.PushEvent -> EventVerb.PUSHED
    EventType.IssuesEvent -> when (action) {
        "opened" -> EventVerb.ISSUE_OPENED
        "closed" -> EventVerb.ISSUE_CLOSED
        "reopened" -> EventVerb.ISSUE_REOPENED
        else -> EventVerb.ISSUE_UPDATED
    }
    EventType.IssueCommentEvent -> EventVerb.ISSUE_COMMENTED
    EventType.CommitCommentEvent -> EventVerb.COMMIT_COMMENTED
    EventType.PullRequestEvent -> when (action) {
        "opened" -> EventVerb.PR_OPENED
        "closed" -> if (merged) EventVerb.PR_MERGED else EventVerb.PR_CLOSED
        "reopened" -> EventVerb.PR_REOPENED
        "review_requested" -> EventVerb.PR_REVIEW_REQUESTED
        else -> EventVerb.PR_UPDATED
    }
    EventType.PullRequestReviewEvent -> EventVerb.PR_REVIEWED
    EventType.PullRequestReviewCommentEvent -> EventVerb.PR_COMMENTED
    EventType.ReleaseEvent -> EventVerb.RELEASE
    EventType.FollowEvent -> EventVerb.FOLLOWED
    EventType.MemberEvent -> EventVerb.MEMBER
    EventType.PublicEvent -> EventVerb.PUBLIC
    EventType.GollumEvent -> EventVerb.WIKI
    EventType.GistEvent -> EventVerb.GIST
    EventType.DownloadEvent -> EventVerb.DOWNLOADED
    EventType.TeamAddEvent -> EventVerb.TEAM_ADDED
    EventType.RepositoryEvent -> EventVerb.REPO
    EventType.OrgBlockEvent, EventType.OrganizationEvent -> EventVerb.ORG
    EventType.ProjectEvent, EventType.ProjectCardEvent, EventType.ProjectColumnEvent -> EventVerb.PROJECT
    EventType.Unknown -> EventVerb.NEW
}
