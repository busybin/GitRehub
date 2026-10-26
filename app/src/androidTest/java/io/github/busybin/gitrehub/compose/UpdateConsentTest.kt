package io.github.busybin.gitrehub.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.UpdatePromptHost
import io.github.busybin.gitrehub.compose.data.UpdateConsent
import io.github.busybin.gitrehub.compose.data.UpdatePrefs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-launch question about automatic update checks, and that an answer is remembered. */
@RunWith(AndroidJUnit4::class)
class UpdateConsentTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unaskedShowsTheQuestionOnceAndRemembersNo() {
        rule.activity.runOnUiThread { UpdatePrefs.setConsent(UpdateConsent.UNASKED) }
        rule.waitForIdle()

        rule.setContent { UpdatePromptHost(enabled = true) }
        val title = rule.activity.getString(R.string.m3_update_consent_title)
        val deny = rule.activity.getString(R.string.m3_update_consent_deny)
        rule.onNodeWithText(title).assertExists()

        rule.onNodeWithText(deny).performClick()
        rule.waitForIdle()

        assertEquals(UpdateConsent.DENIED, UpdatePrefs.consent.value)
        rule.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun answeredBuildDoesNotAskAgain() {
        rule.activity.runOnUiThread { UpdatePrefs.setConsent(UpdateConsent.DENIED) }
        rule.waitForIdle()

        rule.setContent { UpdatePromptHost(enabled = true) }
        val title = rule.activity.getString(R.string.m3_update_consent_title)
        rule.onNodeWithText(title).assertDoesNotExist()
    }
}
