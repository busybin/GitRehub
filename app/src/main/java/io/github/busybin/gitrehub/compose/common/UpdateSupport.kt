package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.busybin.gitrehub.BuildConfig
import java.security.MessageDigest

/**
 * In-app updates only work when the installed APK carries the release certificate. a third-party store and any
 * other store that re-signs produce an APK with a different key, and an APK signed by us can never be
 * installed over it. The from-source build also builds with `-Pgitrehub.store=external`, which switches
 * [BuildConfig.SELF_UPDATE] off.
 */
object UpdateSupport {

    /** SHA-256 of the release certificate (the key used for GitHub and a third-party store builds). */
    private const val RELEASE_CERT_SHA256 =
        "c42c7adc15075ed7349ef5600dd03ddb45177cf82f615534d8309bc0633f53bc"

    fun isAvailable(context: Context): Boolean =
        BuildConfig.SELF_UPDATE && isDeveloperSigned(context)

    private fun isDeveloperSigned(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        } ?: return false
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        digest.joinToString("") { "%02x".format(it.toInt() and 0xff) } == RELEASE_CERT_SHA256
    }.getOrDefault(false)
}
