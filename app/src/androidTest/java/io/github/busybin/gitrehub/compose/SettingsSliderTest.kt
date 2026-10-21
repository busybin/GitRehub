package io.github.busybin.gitrehub.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.settings.SettingsScreen
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.compose.theme.ThemePrefs
import io.github.busybin.gitrehub.compose.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The font-size slider must travel across its range in one drag. Applying every value live used to
 * rescale the whole UI mid-gesture, so the drag broke after 1–3 %.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSliderTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun restore() {
        ThemeState.setFontScale(ThemePrefs.defaultFontScale)
    }

    @Test
    fun oneDragMovesTheFontSliderAcrossTheRange() {
        ThemeState.setFontScale(ThemePrefs.fontScaleMin)
        rule.setContent { FaTheme { SettingsScreen() } }
        rule.waitForIdle()

        rule.onNodeWithTag("font_scale_slider").performTouchInput {
            swipeRight(startX = width * 0.02f, endX = width * 0.98f, durationMillis = 600)
        }
        rule.waitForIdle()

        val scale = ThemeState.fontScale.value
        assertTrue("one drag reached only ${(scale * 100).toInt()}%", scale > 1.25f)
    }
}
