package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.DailyCheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInMetricSeriesBuilderTest {
    @Test
    fun dailyCheckInsAggregateToWeeklyAverages() {
        val series = CheckInMetricSeriesBuilder.build(
            listOf(
                DailyCheckIn(date = "2026-06-22", sleepHours = 6.0, overallFatigue = 4),
                DailyCheckIn(date = "2026-06-23", sleepHours = 8.0, overallFatigue = 2)
            )
        )

        assertEquals(7.0, series.getValue(TrendMetricId.SLEEP_HOURS).single().value ?: 0.0, 0.001)
        assertEquals(3.0, series.getValue(TrendMetricId.OVERALL_FATIGUE_CHECKIN).single().value ?: 0.0, 0.001)
    }

    @Test
    fun recoveryCompositeKeepsMotivationInGoodDirection() {
        val good = CheckInMetricSeriesBuilder.build(
            listOf(DailyCheckIn(date = "2026-06-22", overallFatigue = 1, focusMotivation = 5))
        ).getValue(TrendMetricId.RECOVERY_CHECKIN_COMPOSITE).single().value!!
        val bad = CheckInMetricSeriesBuilder.build(
            listOf(DailyCheckIn(date = "2026-06-22", overallFatigue = 5, focusMotivation = 1))
        ).getValue(TrendMetricId.RECOVERY_CHECKIN_COMPOSITE).single().value!!

        assertTrue(good > bad)
    }
}
