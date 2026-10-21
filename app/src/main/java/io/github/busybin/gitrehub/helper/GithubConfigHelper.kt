package io.github.busybin.gitrehub.helper

import io.github.busybin.gitrehub.BuildConfig

/**
 * OAuth app settings, read from `app/secrets.properties` at build time (see app/build.gradle.kts).
 *
 * This repository ships with **no** credentials: plain access-token login needs none. Only the
 * device-code login flow requires your own GitHub OAuth App with
 * "Enable Device Flow" ticked and this redirect URL whitelisted.
 */
object GithubConfigHelper {

    /** OAuth callback URL; your own GitHub OAuth App must whitelist it. */
    @JvmStatic
    val redirectUrl = BuildConfig.OAUTH_REDIRECT_URL

    @JvmStatic
    val clientId = BuildConfig.GITHUB_CLIENT_ID

    /** No client id configured: OAuth and device-code login are unavailable. */
    @JvmStatic
    val isConfigured: Boolean get() = clientId.isNotBlank()

    @JvmStatic
    val secret = BuildConfig.GITHUB_SECRET
}