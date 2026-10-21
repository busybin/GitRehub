package io.github.busybin.gitrehub.core.net

import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.provider.rest.HttpProvider
import io.github.busybin.gitrehub.provider.rest.interceptors.AuthenticationInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** One decoded answer plus the headers the callers care about (pagination, scopes). */
data class ApiResponse<T>(
    val value: T,
    val links: PageLinks = PageLinks(),
    /** `X-OAuth-Scopes`, only sent by classic tokens. `null` means "unknown", not "empty". */
    val scopes: Set<String>? = null,
)

/**
 * Status plus body of a call whose answer is not always JSON: the stats endpoints answer `202` with
 * an empty body while GitHub computes them, and the contents endpoint answers an array or an object.
 */
data class RawResponse(val code: Int, val body: String)

/**
 * The single HTTP entry point of the rewritten data layer.
 *
 * No Retrofit: requests are built by hand and decoded with kotlinx.serialization, so there is no
 * runtime generic type to lose to R8 (the 2026-10-20 release crash was exactly that), and every API
 * is an ordinary `suspend fun` on [GitHubApi] with an explicit serializer.
 *
 * Clients come from [HttpProvider], which keeps the shared offline cache and the auth header in
 * place, so new calls behave like the legacy ones until the rest of the app is migrated.
 */
object GitHubClient {

    /** Same base as the legacy stack, so a smoke build can point the whole app at a mock. */
    private val apiRoot = BuildConfig.REST_URL.trimEnd('/')

    /** GraphQL lives next to REST; on github.com that is `api.github.com/graphql`. */
    private val graphQlUrl = "$apiRoot/graphql"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Authenticated with whatever token the session currently holds (see [HttpProvider]). */
    private val sharedClient: OkHttpClient by lazy { HttpProvider.provideOkHttpClient() }

    /** Same authentication, but never touches the network: reads the offline HTTP cache only. */
    private val cacheOnlyClient: OkHttpClient by lazy {
        HttpProvider.provideCacheOnlyClient(AuthenticationInterceptor())
    }

    /** Explicit token: used to validate a token before it is stored, never for normal calls. */
    private fun clientFor(token: String?, cacheOnly: Boolean): OkHttpClient = when {
        !token.isNullOrBlank() -> HttpProvider.provideOkHttpClient(AuthenticationInterceptor(token))
        cacheOnly -> cacheOnlyClient
        else -> sharedClient
    }

    suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        token: String? = null,
        cacheOnly: Boolean = false,
    ): ApiResponse<T> = withContext(Dispatchers.IO) {
        val url = (apiRoot + path).toHttpUrl().newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val (body, headers) = execute(Request.Builder().url(url).get().build(), token, cacheOnly)
        ApiResponse(decode(body, serializer), PageLinks.parse(headers["Link"]), scopesOf(headers))
    }

    /**
     * GET an absolute URL taken from a payload (`latest_comment_url` and friends). The path is
     * re-rooted on [apiRoot] so a mock / smoke build stays on the mock host instead of github.com.
     */
    suspend fun <T> getUrl(url: String, serializer: KSerializer<T>): ApiResponse<T> =
        withContext(Dispatchers.IO) {
            val path = url.trim().substringAfter("://", url).substringAfter('/', "")
            val target = (apiRoot + "/" + path).toHttpUrl()
            val (body, headers) = execute(
                Request.Builder().url(target).get().build(),
                token = null,
                cacheOnly = false,
            )
            ApiResponse(decode(body, serializer), PageLinks.parse(headers["Link"]), scopesOf(headers))
        }

    /** For endpoints whose answer body is empty or irrelevant (`204`/`205`): only the status matters. */
    suspend fun sendNoContent(method: String, path: String, body: JsonElement? = null) =
        withContext(Dispatchers.IO) {
            // OkHttp rejects a null body for PUT/POST/PATCH; an empty one is what GitHub expects
            // for the star/watch endpoints.
            val payload = when {
                method == "DELETE" -> null
                body == null -> ByteArray(0).toRequestBody(null, 0, 0)
                else -> body.toString().toRequestBody(jsonMediaType)
            }
            execute(
                Request.Builder().url(apiRoot + path).method(method, payload).build(),
                token = null,
                cacheOnly = false,
            )
            Unit
        }

    /** Raw text answer (`README` HTML, file contents), for endpoints that do not answer JSON. */
    suspend fun getText(
        path: String,
        accept: String? = null,
        query: Map<String, String> = emptyMap(),
        cacheOnly: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val url = (apiRoot + path).toHttpUrl().newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val builder = Request.Builder().url(url).get()
        if (accept != null) builder.header("Accept", accept)
        execute(builder.build(), token = null, cacheOnly = cacheOnly).first
    }

    /**
     * Status plus body, for answers that are not always the same shape: `202`/empty for stats,
     * array-or-object for the contents endpoint.
     */
    suspend fun getRaw(
        path: String,
        accept: String? = null,
        query: Map<String, String> = emptyMap(),
        cacheOnly: Boolean = false,
    ): RawResponse = withContext(Dispatchers.IO) {
        val url = (apiRoot + path).toHttpUrl().newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val builder = Request.Builder().url(url).get()
        if (accept != null) builder.header("Accept", accept)
        val request = builder.build()
        val response = try {
            clientFor(null, cacheOnly).newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(ApiError(0, e.message ?: e.javaClass.simpleName), e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFor(r.code, text, r.headers)
            RawResponse(r.code, text)
        }
    }

    /** Raw text at an absolute API URL taken from a payload (file contents, `blob_url`). */
    suspend fun getTextUrl(url: String, accept: String? = null): String =
        withContext(Dispatchers.IO) {
            val path = url.trim().substringAfter("://", url).substringAfter('/', "")
            val builder = Request.Builder().url(apiRoot + "/" + path).get()
            if (accept != null) builder.header("Accept", accept)
            execute(builder.build(), token = null, cacheOnly = false).first
        }

    /** `true` on 2xx, `false` on 404; anything else still throws (used for 204/404 probes). */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            execute(Request.Builder().url(apiRoot + path).get().build(), token = null, cacheOnly = false)
            true
        } catch (e: ApiException) {
            if (e.error.status == 404) false else throw e
        }
    }

    suspend fun <T> send(
        method: String,
        path: String,
        body: JsonElement?,
        serializer: KSerializer<T>,
        token: String? = null,
    ): ApiResponse<T> = withContext(Dispatchers.IO) {
        // `DELETE .../assignees` is the one DELETE that carries a body; everything else passes null.
        val payload = if (body == null) null else body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(apiRoot + path)
            .method(method, payload)
            .build()
        val (text, headers) = execute(request, token, cacheOnly = false)
        ApiResponse(decode(text, serializer), PageLinks.parse(headers["Link"]), scopesOf(headers))
    }

    /**
     * Runs a GraphQL query and returns its `data` object.
     * GraphQL answers `200` even when it failed, so `errors` is checked here, once.
     */
    suspend fun graphQl(query: String, variables: JsonObject = JsonObject(emptyMap())): JsonObject =
        withContext(Dispatchers.IO) {
            val payload = JsonObject(
                mapOf("query" to JsonPrimitive(query), "variables" to variables)
            ).toString().toRequestBody(jsonMediaType)
            val (body, _) = execute(Request.Builder().url(graphQlUrl).post(payload).build(), null, cacheOnly = false)
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw ApiException(0, "Could not read the response: ${it.message}") }
            val errors = root["errors"] as? JsonArray
            if (errors != null && errors.isNotEmpty()) {
                val message = errors.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "GraphQL error"
                throw ApiException(422, message)
            }
            root["data"]?.jsonObject ?: throw ApiException(422, "GraphQL: empty response")
        }

    private fun <T> decode(body: String, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, body)
    } catch (t: Throwable) {
        throw ApiException(0, "Could not read the response: ${t.message}")
    }

    /** Performs the call; throws [ApiException] for anything that is not a 2xx answer. */
    private fun execute(request: Request, token: String?, cacheOnly: Boolean): Pair<String, Headers> {
        val response = try {
            clientFor(token, cacheOnly).newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(ApiError(0, e.message ?: e.javaClass.simpleName), e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFor(r.code, text, r.headers)
            return text to r.headers
        }
    }

    internal fun scopesOf(headers: Headers): Set<String>? = headers["X-OAuth-Scopes"]?.let { raw ->
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun errorFor(code: Int, body: String, headers: Headers): ApiException {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
        val remaining = headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()
        val rateLimited = (code == 403 || code == 429) && remaining == 0
        return ApiException(ApiError(code, message, rateLimited, reset))
    }
}
