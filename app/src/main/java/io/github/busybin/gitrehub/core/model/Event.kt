package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * One row of an events stream (`/users/{login}/received_events`, `/users/{login}/events`,
 * `/repos/{owner}/{repo}/events`), plus the synthetic rows built from the GraphQL "who starred /
 * forked / opened something on my repos" query.
 *
 * The event stream's `repo` mini object carries no `full_name` and no `owner`: its `name` *is*
 * `owner/repo`. Everything asking "which repo is this about" must go through [repoFullName].
 */
@Serializable
data class Event(
    @Serializable(with = EventIdSerializer::class) val id: Long = 0,
    @Serializable(with = EventTypeSerializer::class) val type: EventType = EventType.Unknown,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val actor: EventActor? = null,
    val repo: EventRepo? = null,
    val payload: EventPayload = EventPayload(),
    @SerialName("public") val isPublic: Boolean = false,
) {
    val repoFullName: String?
        get() = repo?.fullName?.takeIf { it.isNotBlank() }
            ?: repo?.name?.takeIf { it.contains('/') }

    val repoOwner: String?
        get() = repoFullName?.substringBefore('/')?.takeIf { it.isNotBlank() }
}

@Serializable
data class EventActor(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class EventRepo(
    @Serializable(with = EventIdSerializer::class) val id: Long = 0,
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

/**
 * The part of an event payload the UI reads. Most fields are absent depending on the event type;
 * GitHub trims payloads in the events API, so a missing title/number is normal, not an error.
 */
@Serializable
data class EventPayload(
    val action: String? = null,
    val ref: String? = null,
    @SerialName("ref_type") val refType: String? = null,
    val before: String? = null,
    val head: String? = null,
    /** GitHub's own id for a push; stable when the same push is served again. */
    @SerialName("push_id") @Serializable(with = EventIdSerializer::class) val pushId: Long = 0,
    val size: Int = 0,
    val commits: List<EventCommit> = emptyList(),
    val forkee: EventRepo? = null,
    val issue: EventSubject? = null,
    @SerialName("pull_request") val pullRequest: EventSubject? = null,
    val release: EventRelease? = null,
    val comment: EventComment? = null,
)

/** An issue or a pull request; [merged] only exists on the pull request side. */
@Serializable
data class EventSubject(
    val number: Int = 0,
    val title: String? = null,
    val merged: Boolean? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class EventRelease(
    @Serializable(with = EventIdSerializer::class) val id: Long = 0,
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class EventComment(
    @Serializable(with = EventIdSerializer::class) val id: Long = 0,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class EventCommit(
    val sha: String? = null,
    val message: String? = null,
)

/** The events API sends ids as JSON strings; the same model also holds our negative synthetic ids. */
object EventIdSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EventId", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val input = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = input.decodeJsonElement()
        return (element as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** ISO-8601 instants (`2026-10-15T20:38:53Z`); anything unparsable degrades to "unknown". */
@OptIn(ExperimentalSerializationApi::class)
object InstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant? {
        val input = decoder as? JsonDecoder ?: return null
        val raw = runCatching { input.decodeJsonElement().jsonPrimitive.content }.getOrNull()
        return raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }

    override fun serialize(encoder: Encoder, value: Instant?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    }
}
