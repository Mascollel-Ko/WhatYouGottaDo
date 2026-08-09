package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyConditionEditorContractTest {
    @Test
    fun titleUsesTodayAndSelectedHistoricalDate() {
        val today = LocalDate.of(2026, 7, 17)

        assertEquals("오늘 컨디션 입력", dailyConditionEditorTitle(context(Locale.KOREAN), today, today))
        assertEquals(
            "2026년 7월 16일 컨디션 입력",
            dailyConditionEditorTitle(context(Locale.KOREAN), LocalDate.of(2026, 7, 16), today)
        )
        assertEquals(
            "Check-in for Jul 16, 2026",
            dailyConditionEditorTitle(context(Locale.ENGLISH), LocalDate.of(2026, 7, 16), today)
        )
    }

    @Test
    fun decimalParserAcceptsKoreanCommaAndPreservesPrecision() {
        assertEquals(80.5, parseDailyConditionNumber("80,5") ?: 0.0, 0.001)
        assertEquals(80.75, parseDailyConditionNumber("80.75") ?: 0.0, 0.001)
        assertNull(parseDailyConditionNumber(""))
    }

    @Test
    fun bodyWeightInputAllowsBlankAndRejectsInvalidPresentValues() {
        assertTrue(isValidDailyBodyWeightInput(""))
        assertTrue(isValidDailyBodyWeightInput("80,5"))
        assertFalse(isValidDailyBodyWeightInput("0"))
        assertFalse(isValidDailyBodyWeightInput("-1"))
        assertFalse(isValidDailyBodyWeightInput("NaN"))
        assertFalse(isValidDailyBodyWeightInput("Infinity"))
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
