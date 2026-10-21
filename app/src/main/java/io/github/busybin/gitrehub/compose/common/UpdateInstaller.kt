package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * In-app update: download the release APK into app-private storage and hand it to the system
 * installer (`ACTION_VIEW` + `application/vnd.android.package-archive`).
 *
 * Unlike the old "detect only" behavior this really downloads and triggers the install; the install
 * itself is still done by the system UI, so nothing beyond the one-time "install unknown apps" grant
 * is needed.
 */
object UpdateInstaller {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** Download the APK into `…/files/updates/` and return it; throws on failure. Clears the previous one first. */
    suspend fun download(context: Context, apkUrl: String, tag: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create ${dir.path}")
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val target = File(dir, "gitrehub-${tag.ifBlank { "update" }}.apk")
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "GitRehub")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty body")
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            target
        }

    /** Whether the system allows this app to install unknown-source packages (Android 8+). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Open the "install unknown apps" settings page. */
    fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Open the system installer. Returns false when nothing was launched (no installer / URI not granted). */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
