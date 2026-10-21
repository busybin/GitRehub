package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A GitHub account, as `GET /user` and `GET /users/{login}` return it.
 *
 * Deliberately a plain DTO: no token, no ObjectBox id, no UI state. The signed-in account's token
 * lives in `core/auth/TokenStore` and never travels inside a model object.
 */
@Serializable
data class User(
    val id: Long = 0,
    val login: String = "",
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val type: String? = null,
    val bio: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val email: String? = null,
    @SerialName("public_repos") val publicRepos: Long = 0,
    @SerialName("public_gists") val publicGists: Long = 0,
    val followers: Long = 0,
    val following: Long = 0,
    /** Only `/repos/{o}/{r}/contributors` sends it; elsewhere the field is absent (0). */
    val contributions: Long = 0,
)
