package io.github.busybin.gitrehub.compose

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.busybin.gitrehub.compose.common.Downloader
import io.github.busybin.gitrehub.core.notify.DownloadNotifications
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A finished download must leave a notification of our own with a tap target — the thing that was
 * missing while the manifest receiver was `exported="false"` (DownloadProvider broadcasts from its own
 * uid, so a non-exported receiver never hears about it).
 *
 * Needs a real device and the real DownloadProvider; a mock would prove nothing here.
 */
@RunWith(AndroidJUnit4::class)
class DownloadNotificationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val fileName = "gitrehub-notify-probe.txt"
    private val url = "https://raw.githubusercontent.com/busybin/DelugeLauncher/HEAD/README.md"

    @Before
    fun setUp() {
        Downloader.deleteExisting(context, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        Downloader.deleteExisting(context, fileName)
    }

    @Test
    fun finishedDownloadPostsNotification() {
        val id = Downloader.enqueue(context, url, fileName)
        assertNotNull(id)
        val downloadId = id!!
        assertTrue(
            "download did not finish",
            waitFor(60_000) { statusOf(downloadId) == DownloadManager.STATUS_SUCCESSFUL },
        )
        val notifications = context.getSystemService(NotificationManager::class.java)
        val posted = waitFor(10_000) {
            notifications.activeNotifications.any { it.id == DownloadNotifications.id(downloadId) }
        }
        assertTrue("completion notification missing", posted)
    }

    private fun statusOf(id: Long): Int? =
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(300)
        }
        return condition()
    }
}
