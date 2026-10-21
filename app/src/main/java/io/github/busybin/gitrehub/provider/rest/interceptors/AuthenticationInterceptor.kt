package io.github.busybin.gitrehub.provider.rest.interceptors

import io.github.busybin.gitrehub.exception.AuthNullOrBlankException
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.provider.scheme.LinkParserHelper
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the token (and a pending 2FA code) to outgoing requests. */
class AuthenticationInterceptor(
    private val token: String? = null,
    private val otp: String? = null,
    private val scraping: Boolean = false,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val enterprise = LinkParserHelper.isEnterprise(chain.request().url.host)
        val authToken = token?.takeIf { it.isNotBlank() }
            ?: if (enterprise) PrefGetter.enterpriseToken else PrefGetter.token
        val otpCode = otp?.takeIf { it.isNotBlank() }
            ?: if (enterprise) PrefGetter.enterpriseOtpCode else PrefGetter.otpCode

        val builder = chain.request().newBuilder()
        var authorised = false
        authToken?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", if (it.startsWith("Basic")) it else "token $it")
            authorised = true
        }
        otpCode?.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("X-GitHub-OTP", it.trim())
            authorised = true
        }
        val request = chain.request()
        if (!authorised && request.url.host == "api.github.com" && request.url.encodedPath == "/user") {
            throw AuthNullOrBlankException()
        }
        if (!scraping) builder.addHeader("User-Agent", "GitRehub")
        return chain.proceed(builder.build())
    }
}
