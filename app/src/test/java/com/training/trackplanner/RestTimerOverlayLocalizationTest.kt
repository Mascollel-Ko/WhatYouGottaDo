package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestTimerOverlayLocalizationTest {
    @Test
    fun `overlay time hint and finished state follow app locale`() {
        val koreanState = RestTimerState(
            isRunning = true,
            remainingSeconds = 13,
            exerciseStableKey = "ez_bar_spider_curl",
            storedExerciseName = "EZ바 스파이더 컬",
            nextSetNumber = 2,
            hasNextTarget = true
        )
        val englishState = koreanState

        assertEquals("13초", restTimerOverlayTimeText(context(Locale.KOREAN), koreanState))
        assertEquals("EZ바 스파이더 컬 2세트 준비", restTimerOverlayHintText(context(Locale.KOREAN), koreanState))
        assertEquals("닫기", context(Locale.KOREAN).getString(R.string.close))
        assertEquals("13 sec", restTimerOverlayTimeText(context(Locale.ENGLISH), englishState))
        assertEquals(
            "EZ-Bar Spider Curl · Set 2 ready",
            restTimerOverlayHintText(context(Locale.ENGLISH), englishState)
        )
        assertEquals("Close", context(Locale.ENGLISH).getString(R.string.close))
        assertEquals(
            "Rest finished",
            restTimerOverlayTimeText(
                context(Locale.ENGLISH),
                englishState.copy(isRunning = false, isFinished = true)
            )
        )
        assertEquals("Rest in progress", restTimerNotificationTitle(context(Locale.ENGLISH), englishState))
        assertEquals(
            "00:13 remaining · EZ-Bar Spider Curl · Set 2 ready",
            restTimerNotificationText(context(Locale.ENGLISH), englishState)
        )
    }

    @Test
    fun `semantic exercise identity relocalizes while custom and legacy names stay safe`() {
        val builtIn = RestTimerState(
            exerciseStableKey = "ez_bar_spider_curl",
            storedExerciseName = "EZ바 스파이더 컬",
            nextSetNumber = 3,
            hasNextTarget = true
        )
        assertEquals("EZ바 스파이더 컬 3세트 준비", RestTimerPresentation.nextSetHint(context(Locale.KOREAN), builtIn))
        assertEquals("EZ-Bar Spider Curl · Set 3 ready", RestTimerPresentation.nextSetHint(context(Locale.ENGLISH), builtIn))

        val custom = builtIn.copy(exerciseStableKey = "custom.user.lift", storedExerciseName = "내 운동", nextSetNumber = 2)
        assertEquals("내 운동 · Set 2 ready", RestTimerPresentation.nextSetHint(context(Locale.ENGLISH), custom))

        val legacy = RestTimerState(nextHint = "EZ바 스파이더 컬 2세트 준비", hasNextTarget = true)
        assertEquals("EZ바 스파이더 컬 · Set 2 ready", RestTimerPresentation.nextSetHint(context(Locale.ENGLISH), legacy))
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
