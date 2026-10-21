package io.github.busybin.gitrehub.core.auth

import io.github.busybin.gitrehub.core.net.GitHubClient
import io.github.busybin.gitrehub.helper.GithubConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * GitHub's OAuth **device flow** — web login without a client secret, a redirect URI or a browser
 * that has to come back to the app.
 *
 * The phone asks GitHub for a short user code, the user types it on <https://github.com/login/device>
 * while the app polls for the token. Because the round trip happens in-app it also works for "add
 * another account", which the redirect-based flow cannot do.
 *
 * The OAuth App must have "Enable Device Flow" ticked, otherwise GitHub answers
 * `device_flow_disabled` and [requestChallenge] throws [Failure].
 *
 * Requests use a bare [OkHttpClient] on purpose: the app's debug client logs whole bodies, and a
 * token exchange must never end up in logcat.
 */
object DeviceFlow {

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    const val VERIFICATION_URL = "https://github.com/login/device"

    /** Why the flow failed, so the UI can pick a sentence without parsing messages. */
    enum class Reason {
        DEVICE_FLOW_DISABLED,
        CLIENT_ID_INVALID,
        GRANT_REJECTED,
        NO_CODE,
        NO_TOKEN,
        NETWORK,
        OTHER,
    }

    class Failure(
        val reason: Reason,
        val detail: String? = null,
    ) : Exception(detail ?: reason.name)

    data class Challenge(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
    )

    sealed interface Poll {
        data object Pending : Poll
        data object SlowDown : Poll
        data object Expired : Poll
        data object Denied : Poll
        data class Token(val token: String) : Poll
        data class Error(val reason: Reason, val detail: String? = null) : Poll
    }

    val clientId: String get() = GithubConfigHelper.clientId

    /**
     * Some phone resolvers fail to answer for github.com (broken IPv6 DNS is a common cause) while
     * the browser still works. When the system resolver throws, ask well-known public resolvers over
     * plain UDP so logging in does not depend on the phone's DNS setup.
     */
    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            for (server in listOf("8.8.8.8", "1.1.1.1", "9.9.9.9", "208.67.222.222")) {
                val ips = runCatching { udpDnsLookup(hostname, server) }.getOrNull()
                if (!ips.isNullOrEmpty()) return ips.map { InetAddress.getByName(it) }
            }
            throw UnknownHostException("unable to resolve $hostname")
        }

        private fun udpDnsLookup(hostname: String, server: String): List<String> {
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 3000
            try {
                val query = buildDnsQuery(hostname)
                socket.send(java.net.DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                val buf = ByteArray(512)
                val response = java.net.DatagramPacket(buf, buf.size)
                socket.receive(response)
                return parseDnsResponse(buf, response.length)
            } finally {
                socket.close()
            }
        }

        private fun buildDnsQuery(hostname: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf(0x12, 0x34)) // id
            out.write(byteArrayOf(0x01, 0x00)) // standard query, recursion desired
            out.write(byteArrayOf(0x00, 0x01)) // qdcount
            out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            hostname.split(".").forEach { label ->
                out.write(label.length)
                out.write(label.toByteArray(Charsets.US_ASCII))
            }
            out.write(0)
            out.write(byteArrayOf(0x00, 0x01)) // qtype A
            out.write(byteArrayOf(0x00, 0x01)) // qclass IN
            return out.toByteArray()
        }

        private fun parseDnsResponse(buf: ByteArray, len: Int): List<String> {
            if (len < 12) return emptyList()
            val ancount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
            var pos = 12
            while (pos < len && (buf[pos].toInt() and 0xFF) != 0) {
                val length = buf[pos].toInt() and 0xFF
                if (length and 0xC0 == 0xC0) {
                    pos += 2
                    break
                }
                pos += length + 1
            }
            if (pos < len && (buf[pos].toInt() and 0xFF) == 0) pos++
            pos += 4 // qtype + qclass
            val ips = mutableListOf<String>()
            repeat(ancount) {
                if (pos >= len) return@repeat
                if ((buf[pos].toInt() and 0xC0) == 0xC0) {
                    pos += 2
                } else {
                    while (pos < len && (buf[pos].toInt() and 0xFF) != 0) {
                        pos += (buf[pos].toInt() and 0xFF) + 1
                    }
                    if (pos < len) pos++
                }
                if (pos + 10 > len) return@repeat
                val type = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
                val cls = ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)
                pos += 4
                pos += 4 // ttl
                val rdlen = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
                pos += 2
                if (type == 1 && cls == 1 && rdlen == 4 && pos + 4 <= len) {
                    ips += listOf(buf[pos], buf[pos + 1], buf[pos + 2], buf[pos + 3])
                        .joinToString(".") { (it.toInt() and 0xFF).toString() }
                }
                pos += rdlen
            }
            return ips
        }
    }

    private val client = OkHttpClient.Builder().dns(dns).build()

    suspend fun requestChallenge(scopes: String): Challenge = withContext(Dispatchers.IO) {
        val body = post(DEVICE_CODE_URL, "client_id" to clientId, "scope" to scopes)
        challengeFrom(body)
    }

    /** Polls once; the caller decides how often and when to stop. */
    suspend fun poll(deviceCode: String): Poll = withContext(Dispatchers.IO) {
        try {
            pollFrom(
                post(
                    ACCESS_TOKEN_URL,
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                )
            )
        } catch (f: Failure) {
            Poll.Error(f.reason, f.detail)
        }
    }

    /** Pure mapping of the `/login/device/code` answer; unit-tested without any network. */
    internal fun challengeFrom(body: Map<String, String>): Challenge {
        body["error"]?.let { throw Failure(reasonFor(it), body["error_description"]) }
        val deviceCode = body["device_code"]
        val userCode = body["user_code"]
        if (deviceCode.isNullOrBlank() || userCode.isNullOrBlank()) throw Failure(Reason.NO_CODE)
        return Challenge(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = body["verification_uri"].orEmpty().ifBlank { VERIFICATION_URL },
            expiresInSeconds = body["expires_in"]?.toIntOrNull() ?: 900,
            intervalSeconds = body["interval"]?.toIntOrNull() ?: 5,
        )
    }

    /** Pure mapping of one token-poll answer; unit-tested. */
    internal fun pollFrom(body: Map<String, String>): Poll = when (val error = body["error"]) {
        null -> body["access_token"]?.takeIf { it.isNotBlank() }
            ?.let { Poll.Token(it) }
            ?: Poll.Error(Reason.NO_TOKEN)

        "authorization_pending" -> Poll.Pending
        "slow_down" -> Poll.SlowDown
        "expired_token" -> Poll.Expired
        "access_denied" -> Poll.Denied
        else -> Poll.Error(reasonFor(error), body["error_description"])
    }

    private fun reasonFor(error: String): Reason = when (error) {
        "device_flow_disabled" -> Reason.DEVICE_FLOW_DISABLED
        "incorrect_client_credentials", "unauthorized_client" -> Reason.CLIENT_ID_INVALID
        "unsupported_grant_type" -> Reason.GRANT_REJECTED
        else -> Reason.OTHER
    }

    private fun post(url: String, vararg fields: Pair<String, String>): Map<String, String> {
        val form = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "GitRehub")
            .post(form)
            .build()
        val text = try {
            client.newCall(request).execute().use { it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw Failure(Reason.NETWORK, e.message)
        }
        return try {
            GitHubClient.json.decodeFromString(DeviceResponse.serializer(), text).toMap()
        } catch (t: Throwable) {
            throw Failure(Reason.NETWORK, t.message)
        }
    }

    @Serializable
    private data class DeviceResponse(
        @SerialName("device_code") val deviceCode: String? = null,
        @SerialName("user_code") val userCode: String? = null,
        @SerialName("verification_uri") val verificationUri: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        val interval: Int? = null,
        @SerialName("access_token") val accessToken: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    ) {
        fun toMap(): Map<String, String> = buildMap {
            deviceCode?.let { put("device_code", it) }
            userCode?.let { put("user_code", it) }
            verificationUri?.let { put("verification_uri", it) }
            expiresIn?.let { put("expires_in", it.toString()) }
            interval?.let { put("interval", it.toString()) }
            accessToken?.let { put("access_token", it) }
            error?.let { put("error", it) }
            errorDescription?.let { put("error_description", it) }
        }
    }
}
