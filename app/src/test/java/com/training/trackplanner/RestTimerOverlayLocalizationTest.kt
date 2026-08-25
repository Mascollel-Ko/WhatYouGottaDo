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
            nextHint = "EZ바 스파이더 컬 2세트 준비"
        )
        val englishState = koreanState.copy(nextHint = "EZ-Bar Spider Curl 2세트 준비")

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
            "break ends",
            restTimerOverlayTimeText(
                context(Locale.ENGLISH),
                englishState.copy(isRunning = false, isFinished = true)
            )
        )
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
