package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * GitHub event types, spelled exactly like the API's `type` field.
 *
 * [Unknown] keeps the data layer forward compatible: GitHub adds event types now and then, and an
 * unknown one must still be rendered (and pass the default filter) instead of failing the decode.
 * Labels, icons and filter groups are UI concerns and live in `compose/feed`.
 */
enum class EventType {
    WatchEvent,
    CreateEvent,
    CommitCommentEvent,
    DownloadEvent,
    FollowEvent,
    ForkEvent,
    GistEvent,
    GollumEvent,
    IssueCommentEvent,
    IssuesEvent,
    MemberEvent,
    PublicEvent,
    PullRequestEvent,
    PullRequestReviewCommentEvent,
    PullRequestReviewEvent,
    RepositoryEvent,
    PushEvent,
    TeamAddEvent,
    DeleteEvent,
    ReleaseEvent,
    ForkApplyEvent,
    OrgBlockEvent,
    ProjectCardEvent,
    ProjectColumnEvent,
    OrganizationEvent,
    ProjectEvent,
    Unknown,
    ;

    companion object {
        fun from(raw: String?): EventType =
            entries.firstOrNull { it.name == raw } ?: Unknown
    }
}

object EventTypeSerializer : KSerializer<EventType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EventType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): EventType = EventType.from(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: EventType) = encoder.encodeString(value.name)
}
