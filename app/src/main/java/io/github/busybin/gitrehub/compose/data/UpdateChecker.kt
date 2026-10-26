package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Update check against our GitHub releases.
 *
 * Calls `GET releases/latest`, compares `tag_name` against `versionCode`, and hands the release page
 * or the APK asset to the UI. Nothing is sent unless the user asks for it or answered the
 * first-launch question with "allow" (see [UpdateConsent]), and no device or user data goes with
 * the request.
 *
 * The APK can only be installed by builds signed with the release key; see
 * [io.github.busybin.gitrehub.compose.common.UpdateSupport].
 *
 * Release tags and `versionName` are plain numbers matching `versionCode` (`V<number>` also parses).
 */
object UpdateChecker {

    const val OWNER = "busybin"
    const val REPO = "gitrehub"
    const val RELEASES_URL = "https://github.com/$OWNER/$REPO/releases/latest"

    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val TIMEOUT_SECONDS = 15L

    sealed interface Result {
        /** Up to date; also covers a repo with no releases yet (GitHub answers 404). */
        data object UpToDate : Result

        /** Newer build: version (= versionCode), release page, notes, direct APK URL if any. */
        data class Available(
            val version: Int,
            val tag: String,
            val url: String,
            val notes: String? = null,
            val apkUrl: String? = null,
        ) : Result

        /** Network or rate-limit failure; shown as one line and never blocks the UI. */
        data class Failed(val message: UiText) : Result
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Runs one check; `currentVersion` is `BuildConfig.VERSION_CODE`. */
    suspend fun check(currentVersion: Int): Result = withContext(Dispatchers.IO) {
        fetchOnce(currentVersion) ?: run {
            // Retry transient failures only; never an HTTP status the server already answered.
            delay(1200)
            fetchOnce(currentVersion)
        } ?: Result.Failed(UiText.res(R.string.m3_update_check_failed))
    }

    /** Null means the failure is worth retrying; otherwise this is the final result. */
    private fun fetchOnce(currentVersion: Int): Result? {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            // GitHub rejects requests without a User-Agent.
            .header("User-Agent", "GitRehub/${BuildConfig.VERSION_NAME}")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    // 404 = no release (or no repo) yet, which for the user means "nothing to update".
                    response.code == 404 -> Result.UpToDate
                    response.code == 403 -> Result.Failed(UiText.res(R.string.m3_update_rate_limited))
                    !response.isSuccessful ->
                        Result.Failed(UiText.res(R.string.m3_update_http_error, response.code))
                    else -> classify(response.body?.string(), currentVersion)
                }
            }
        } catch (e: UnknownHostException) {
            null
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Pure: release JSON to result. Unparsable counts as a failure, never as "update available". */
    fun classify(json: String?, currentVersion: Int): Result {
        val root = runCatching { Json.parseToJsonElement(json!!).jsonObject }.getOrNull()
            ?: return Result.Failed(UiText.res(R.string.m3_update_parse_failed))
        val tag = root["tag_name"]?.runCatching { jsonPrimitive.content }?.getOrNull()
            ?: return Result.Failed(UiText.res(R.string.m3_update_parse_failed))
        val version = versionFromTag(tag)
            ?: return Result.Failed(UiText.res(R.string.m3_update_tag_unknown, tag))
        val url = root["html_url"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: RELEASES_URL
        return if (version > currentVersion) {
            Result.Available(
                version = version,
                tag = tag,
                url = url,
                notes = root["body"]?.runCatching { jsonPrimitive.content }?.getOrNull()?.trim()
                    ?.takeIf { it.isNotEmpty() },
                apkUrl = apkAssetUrl(root),
            )
        } else {
            Result.UpToDate
        }
    }

    /** First `.apk` asset, if any; the UI falls back to opening the release page. */
    private fun apkAssetUrl(root: JsonObject): String? {
        val assets = root["assets"]?.runCatching { jsonArray }?.getOrNull() ?: return null
        return assets.asSequence()
            .mapNotNull { it.runCatching { jsonObject }.getOrNull() }
            .firstOrNull { obj ->
                obj["name"]?.runCatching { jsonPrimitive.content }?.getOrNull()
                    ?.endsWith(".apk", true) == true
            }
            ?.get("browser_download_url")
            ?.runCatching { jsonPrimitive.content }
            ?.getOrNull()
    }

    /** `V12` / `v12` / `12` to 12; null when unparsable (= failure, not "update available"). */
    fun versionFromTag(tag: String): Int? =
        tag.trim().removePrefix("v").removePrefix("V").toIntOrNull()
}

/**
 * The answer to "may this app check for updates on its own?", asked once on first launch.
 * [UNASKED] is the only state that shows the question, and nothing is requested from the release
 * page until it is answered.
 */
enum class UpdateConsent { UNASKED, ALLOWED, DENIED }

/**
 * What the user answered, plus the newest version a check has seen. The update dialog shows on
 * every launch while it is newer than the running build; the consent question itself is asked once.
 */
object UpdatePrefs {

    private const val KEY_CONSENT = "m3_update_consent"
    private const val KEY_KNOWN = "m3_update_known_version"

    /** The old opt-in switch; kept only to migrate builds from before the first-launch question. */
    private const val KEY_AUTO_LEGACY = "m3_update_auto_check"

    private fun loadConsent(): UpdateConsent = when {
        PrefHelper.isExist<Int>(KEY_CONSENT) ->
            UpdateConsent.entries.getOrElse(PrefHelper.getInt(KEY_CONSENT)) { UpdateConsent.UNASKED }
        // Upgraded from the switch: it was only ever turned on by the user, so that answer stands.
        PrefHelper.getBoolean(KEY_AUTO_LEGACY) -> UpdateConsent.ALLOWED
        else -> UpdateConsent.UNASKED
    }

    private val _consent = MutableStateFlow(loadConsent())
    val consent: StateFlow<UpdateConsent> = _consent.asStateFlow()

    fun setConsent(value: UpdateConsent) {
        PrefHelper.putAny(KEY_CONSENT, value.ordinal)
        _consent.value = value
    }

    /** The settings switch: on means "allowed", off means "asked and declined". */
    fun setAuto(enabled: Boolean) {
        setConsent(if (enabled) UpdateConsent.ALLOWED else UpdateConsent.DENIED)
    }

    private val _known = MutableStateFlow(
        if (PrefHelper.isExist<Int>(KEY_KNOWN)) PrefHelper.getInt(KEY_KNOWN) else 0
    )
    val known: StateFlow<Int> = _known.asStateFlow()

    /** Stores the version if it is newer than the recorded one. */
    fun recordKnown(version: Int) {
        if (version <= _known.value) return
        PrefHelper.putAny(KEY_KNOWN, version)
        _known.value = version
    }
}
