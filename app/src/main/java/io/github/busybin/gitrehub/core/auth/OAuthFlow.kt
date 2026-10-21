package io.github.busybin.gitrehub.core.auth

import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.core.net.GitHubClient
import io.github.busybin.gitrehub.helper.GithubConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The redirect-based OAuth hand-off: `gitrehub://login?code=…` in, a token out.
 *
 * Only used by the browser flow; plain token login and the device flow do not come through here.
 * Bare OkHttp client for the same reason as [DeviceFlow]: the exchange must not be logged.
 */
object OAuthFlow {

    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"

    class Failure(message: String?) : Exception(message ?: "OAuth exchange failed")

    private val client = OkHttpClient()

    /** Exchanges the one-time [code] for an access token. */
    suspend fun exchangeCode(code: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", GithubConfigHelper.clientId)
            .add("client_secret", GithubConfigHelper.secret)
            .add("state", BuildConfig.GITHUB_APP_ID)
            .add("redirect_uri", GithubConfigHelper.redirectUrl)
            .build()
        val request = Request.Builder()
            .url(ACCESS_TOKEN_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "GitRehub")
            .post(form)
            .build()
        val text = try {
            client.newCall(request).execute().use { it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw Failure(e.message)
        }
        val parsed = runCatching {
            GitHubClient.json.decodeFromString(TokenResponse.serializer(), text)
        }.getOrElse { throw Failure("Could not read the token response") }
        parsed.accessToken?.takeIf { it.isNotBlank() }
            ?: throw Failure(parsed.errorDescription ?: parsed.error)
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )
}
