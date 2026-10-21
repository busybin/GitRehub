package io.github.busybin.gitrehub.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.provider.timeline.handler.drawable.UrlDrawable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * README images must never draw outside the box the text layout reserved for them.
 *
 * Table cells cap an `ImageSpan` (`CappedImageSpan`) by shrinking the span drawable's bounds; the
 * Glide-loaded bitmap inside used to keep its larger bounds and spilled over the caption text below.
 */
@RunWith(AndroidJUnit4::class)
class ImageSpanBoundsTest {

    @Test
    fun imageIsDrawnInsideTheLayoutBounds() {
        val outer = UrlDrawable()
        val inner = ColorDrawable(Color.RED)
        outer.setImage(inner)
        outer.bounds = Rect(0, 0, 100, 50)
        inner.bounds = Rect(0, 0, 300, 150)

        outer.draw(Canvas(Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)))

        assertEquals(Rect(0, 0, 100, 50), inner.bounds)
    }
}
