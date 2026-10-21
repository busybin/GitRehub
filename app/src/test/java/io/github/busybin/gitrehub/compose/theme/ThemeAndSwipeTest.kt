package io.github.busybin.gitrehub.compose.theme

import io.github.busybin.gitrehub.compose.common.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins two things that are easy to change only halfway:
 * 1. `ThemeId` and its spec table ([spec]) must stay in sync: adding a name to [ThemeId] without a
 *    matching `FaSpec` (or an `m3_theme_*` string) still compiles.
 * 2. `SwipeAction` is a name-based preference: renaming the enum breaks existing settings, so
 *    [ThemeId.from] / [SwipeAction.from] must fall back to the default.
 */
class ThemeAndSwipeTest {

    @Test
    fun everyThemeHasASpec() {
        ThemeId.entries.forEach { id ->
            if (id == ThemeId.WALLPAPER) {
                // Wallpaper follows the system for its whole palette, so it has no spec.
                assertNull("WALLPAPER must not have a spec", id.spec)
            } else {
                val spec = id.spec
                assertNotNull("$id is missing its FaSpec (not set in buildSpec)", spec)
            }
        }
    }

    @Test
    fun everyThemeHasBothCodePalettes() {
        ThemeId.entries.filter { it != ThemeId.WALLPAPER }.forEach { id ->
            val spec = id.spec!!
            // Light and dark must both be complete, not half-filled.
            assertTrue("$id's light code palette is incomplete", spec.lightCode.backgroundArgb != 0)
            assertTrue("$id's dark code palette is incomplete", spec.darkCode.backgroundArgb != 0)
        }
    }

    @Test
    fun themeEnumNamesRoundTrip() {
        ThemeId.entries.forEach { assertEquals(it, ThemeId.from(it.name)) }
        // Unknown value (old stored value / hand-edited MMKV) falls back to the default.
        assertEquals(ThemeId.default, ThemeId.from("NOT_A_THEME"))
        assertEquals(ThemeId.default, ThemeId.from(null))
    }

    @Test
    fun swipeActionNamesRoundTrip() {
        SwipeAction.entries.forEach { assertEquals(it, SwipeAction.from(it.name)) }
        // Never set / unknown → keep the old behaviour: swipe = mark as read.
        assertEquals(SwipeAction.MARK_READ, SwipeAction.from(null))
        assertEquals(SwipeAction.MARK_READ, SwipeAction.from("whatever"))
    }
}
