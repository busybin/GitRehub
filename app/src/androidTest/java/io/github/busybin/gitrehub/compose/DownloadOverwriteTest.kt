package io.github.busybin.gitrehub.compose

import android.app.DownloadManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.busybin.gitrehub.compose.common.Downloader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end "downloading a file with an existing name asks to overwrite". Two things that cannot be
 * checked by reading code, hence a real device and a real DownloadManager:
 *  1. after a download, [Downloader.conflict] sees the name in the Downloads dir (the DownloadManager
 *     record, MediaStore, or a bare path — any one is enough);
 *  2. [Downloader.deleteExisting] can remove it, otherwise "overwrite" is a lie (DownloadManager
 *     would just save `foo-1.zip`).
 *
 * Hits the network (a small file on GitHub) and cleans up after itself.
 */
@RunWith(AndroidJUnit4::class)
class DownloadOverwriteTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val fileName = "gitrehub-overwrite-probe.txt"
    private val url = "https://raw.githubusercontent.com/busybin/DelugeLauncher/HEAD/README.md"

    @Before
    fun cleanBefore() {
        Downloader.deleteExisting(context, fileName)
    }

    @After
    fun cleanAfter() {
        Downloader.deleteExisting(context, fileName)
    }

    @Test
    fun aFinishedDownloadIsSeenAsAConflictAndCanBeOverwritten() {
        val id = Downloader.enqueue(context, url, fileName)
        assertNotNull("DownloadManager did not accept the task", id)
        waitUntilFinished(id!!)

        assertEquals(
            "conflict() did not see the file after download — Downloads dir / download records / MediaStore all missed it",
            fileName,
            Downloader.conflict(context, fileName),
        )
        assertTrue(
            "cannot delete the old file (so this is not a real overwrite; DownloadManager would save as $fileName-1)",
            Downloader.deleteExisting(context, fileName),
        )
        assertNull("no conflict should be reported after deletion", Downloader.conflict(context, fileName))
    }

    private fun waitUntilFinished(id: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline) {
            val query = DownloadManager.Query().setFilterById(id)
            manager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            throw AssertionError("download failed, status=FAILED reason=$reason")
                        }
                    }
                }
            }
            Thread.sleep(500)
        }
        throw AssertionError("download did not finish within 90 seconds")
    }
}
