package com.training.trackplanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestTimerPersistenceCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    @After
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun semanticTimerIdentityRestoresAfterProcessRecreation() {
        preferences.edit()
            .putLong("rest_end_at", 1L)
            .putInt("rest_total_seconds", 90)
            .putBoolean("rest_finished", true)
            .putString("rest_exercise_stable_key", "ez_bar_spider_curl")
            .putString("rest_stored_exercise_name", "EZ바 스파이더 컬")
            .putInt("rest_next_set_number", 3)
            .putBoolean("rest_has_next_target", true)
            .commit()

        val restored = RestTimerSessionController(context).state.value

        assertEquals("ez_bar_spider_curl", restored.exerciseStableKey)
        assertEquals("EZ바 스파이더 컬", restored.storedExerciseName)
        assertEquals(3, restored.nextSetNumber)
        assertEquals(true, restored.isFinished)
    }

    @Test
    fun oldPersistedHintRestoresWithoutSemanticKeys() {
        preferences.edit()
            .putLong("rest_end_at", 1L)
            .putInt("rest_total_seconds", 60)
            .putBoolean("rest_finished", true)
            .putString("rest_next", "EZ바 스파이더 컬 2세트 준비")
            .putBoolean("rest_has_next_target", true)
            .commit()

        val restored = RestTimerSessionController(context).state.value

        assertEquals("EZ바 스파이더 컬 2세트 준비", restored.nextHint)
        assertEquals("", restored.exerciseStableKey)
        assertNull(restored.nextSetNumber)
    }
}
