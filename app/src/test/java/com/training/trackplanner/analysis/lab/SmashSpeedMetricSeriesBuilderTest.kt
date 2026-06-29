package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.SmashSpeedRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmashSpeedMetricSeriesBuilderTest {
    @Test
    fun buildsWeeklySmashSpeedSeriesFromRecordedDaysOnly() {
        val series = SmashSpeedMetricSeriesBuilder.build(
            listOf(
                SmashSpeedRecord(date = "2026-06-29", speedKmh = 200.0),
                SmashSpeedRecord(date = "2026-06-30", speedKmh = 240.0),
                SmashSpeedRecord(date = "2026-07-01", speedKmh = 220.0),
                SmashSpeedRecord(date = "2026-07-01", speedKmh = 180.0)
            )
        )

        assertEquals(220.0, series.getValue(TrendMetricId.SMASH_SPEED_TOP3_AVG).single().value ?: 0.0, 0.001)
        assertEquals(240.0, series.getValue(TrendMetricId.SMASH_SPEED_BEST).single().value ?: 0.0, 0.001)
        assertEquals(210.0, series.getValue(TrendMetricId.SMASH_SPEED_AVG).single().value ?: 0.0, 0.001)
        assertEquals(4.0, series.getValue(TrendMetricId.SMASH_ATTEMPT_COUNT).single().value ?: 0.0, 0.001)
    }

    @Test
    fun invalidDatesDoNotCreateZeroPoints() {
        val series = SmashSpeedMetricSeriesBuilder.build(
            listOf(SmashSpeedRecord(date = "bad-date", speedKmh = 200.0))
        )

        assertTrue(series.getValue(TrendMetricId.SMASH_SPEED_TOP3_AVG).isEmpty())
    }
}
