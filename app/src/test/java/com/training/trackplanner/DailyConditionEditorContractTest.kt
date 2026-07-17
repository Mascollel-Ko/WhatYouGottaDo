package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyConditionEditorContractTest {
    @Test
    fun titleUsesTodayAndSelectedHistoricalDate() {
        val today = LocalDate.of(2026, 7, 17)

        assertEquals("오늘 컨디션 입력", dailyConditionEditorTitle(today, today))
        assertEquals(
            "7월 16일 컨디션 입력",
            dailyConditionEditorTitle(LocalDate.of(2026, 7, 16), today)
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
}
