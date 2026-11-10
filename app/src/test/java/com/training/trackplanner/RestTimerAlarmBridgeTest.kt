package com.training.trackplanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestTimerAlarmBridgeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private val bridge = RestTimerAlarmBridge(context)

    @After
    fun clear() {
        bridge.cancelScheduled()
        RestTimerNotifier(context).cancel()
        preferences.edit().clear().commit()
    }

    @Test
    fun warningIsOnlyDueForTimersLongerThanTenSecondsAndBeforeFinish() {
        val end = 100_000L
        assertTrue(RestTimerAlarmPolicy.warningAt(end, 90) == 90_000L)
        assertTrue(RestTimerAlarmPolicy.warningAt(end, 10) == null)
        assertFalse(RestTimerAlarmPolicy.warningDue(89_999L, end, 90))
        assertTrue(RestTimerAlarmPolicy.warningDue(90_000L, end, 90))
        assertTrue(RestTimerAlarmPolicy.warningDue(99_999L, end, 90))
        assertFalse(RestTimerAlarmPolicy.warningDue(end, end, 90))
        assertFalse(RestTimerAlarmPolicy.finishDue(99_999L, end))
        assertTrue(RestTimerAlarmPolicy.finishDue(end, end))
    }

    @Test
    fun obsoleteWarningCannotClaimNewTimerAndWarningCanOnlyBeClaimedOnce() {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putLong(RestTimerAlarmBridge.KEY_RUN_ID, 900L)
            .putLong("rest_end_at", now + 8_000L)
            .putInt("rest_total_seconds", 60)
            .putBoolean("rest_finished", false)
            .commit()

        bridge.deliverWarning(899L)
        assertFalse(preferences.getBoolean("rest_warning_delivered", false))

        bridge.deliverWarning(900L)
        assertTrue(preferences.getBoolean("rest_warning_delivered", false))
        bridge.deliverWarning(900L)
        assertTrue(preferences.getBoolean("rest_warning_delivered", false))
        assertFalse(preferences.getBoolean("rest_finished", false))
    }

    @Test
    fun onlyCurrentDueRunCanFinishAndPersistExactlyOnce() {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putLong(RestTimerAlarmBridge.KEY_RUN_ID, 901L)
            .putLong("rest_end_at", now - 1_000L)
            .putInt("rest_total_seconds", 90)
            .putBoolean("rest_finished", false)
            .commit()

        bridge.deliverFinish(900L)
        assertFalse(preferences.getBoolean("rest_finish_delivered", false))

        bridge.deliverFinish(901L)
        assertTrue(preferences.getBoolean("rest_finish_delivered", false))
        assertTrue(preferences.getBoolean("rest_finished", false))
        bridge.deliverFinish(901L)
        assertTrue(preferences.getBoolean("rest_finish_delivered", false))
    }
}
