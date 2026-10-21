package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.helper.PrefGetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The value written by the "check frequency" chips must read back verbatim from the scheduler. The
 * old mapping translated legacy buckets and returned -1 for unknown values, silently disabling
 * notifications, and the value also fed `setPeriodic` (floored at 15 minutes).
 */
@RunWith(AndroidJUnit4::class)
class NotificationPrefsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun restore() {
        NotificationPrefs.setEnabled(context, false)
        NotificationPrefs.setIntervalMinutes(context, NotificationPrefs.DEFAULT_INTERVAL_MINUTES)
    }

    @Test
    fun everyIntervalChipSurvivesThePrefMapping() {
        NotificationPrefs.setEnabled(context, true)
        NotificationPrefs.intervals.forEach { minutes ->
            NotificationPrefs.setIntervalMinutes(context, minutes)
            assertEquals(
                "chose $minutes min but the scheduler reads something else (interval lost or treated as off)",
                minutes,
                PrefGetter.notificationIntervalMinutes,
            )
        }
    }

    @Test
    fun turningTheSwitchOffMeansNoInterval() {
        NotificationPrefs.setEnabled(context, true)
        NotificationPrefs.setIntervalMinutes(context, 30)
        assertEquals(30, PrefGetter.notificationIntervalMinutes)
        NotificationPrefs.setEnabled(context, false)
        assertEquals(-1, PrefGetter.notificationIntervalMinutes)
    }

    @Test
    fun aSillyStoredValueFallsBackToTheDefaultInsteadOfDisabling() {
        NotificationPrefs.setEnabled(context, true)
        // A legacy "20" or any stray value must not turn notifications into "off".
        NotificationPrefs.setIntervalMinutes(context, 20)
        assertEquals(20, PrefGetter.notificationIntervalMinutes)
    }
}
