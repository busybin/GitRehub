package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does this build actually load a picture over the network?
 *
 * Coil is the only image pipeline (avatars, README/issue bodies), so this needs the emulator to
 * have internet; a failure here is real evidence, a pass means the bug is elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class ImageLoadingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A tiny, always-public JPEG that GitHub serves for any account. */
    private val url = "https://avatars.githubusercontent.com/u/1?v=4"

    @Test
    fun coilLoadsAnAvatar() {
        val loader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context).data(url).build()
        val result = runBlocking { loader.execute(request) }
        assertTrue("Coil failed: $result", result is SuccessResult)
    }
}
