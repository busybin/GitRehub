package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.Serializable

/**
 * One row of `GET .../reactions?content=heart`: the reaction list is the only way to know whether
 * the signed-in account already reacted (the embedded [Reactions] summary has counts only).
 */
@Serializable
data class Reaction(
    val id: Long = 0,
    /** GitHub's `content` value (`+1`, `-1`, `laugh`, `hooray`, `confused`, `heart`, `rocket`, `eyes`). */
    val content: String? = null,
    val user: User? = null,
)
