package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmashSpeedSummaryTest {
    @Test
    fun summaryCalculatesBestAverageTop3AndCount() {
        val records = listOf(
            SmashSpeedRecord(date = "2026-06-29", speedKmh = 210.0),
            SmashSpeedRecord(date = "2026-06-29", speedKmh = 230.0),
            SmashSpeedRecord(date = "2026-06-29", speedKmh = 220.0),
            SmashSpeedRecord(date = "2026-06-29", speedKmh = 190.0)
        )

        val summary = SmashSpeedSummary.from("2026-06-29", records)

        assertEquals(230.0, summary.bestSpeedKmh ?: 0.0, 0.001)
        assertEquals(212.5, summary.averageSpeedKmh ?: 0.0, 0.001)
        assertEquals(220.0, summary.top3AverageSpeedKmh ?: 0.0, 0.001)
        assertEquals(4, summary.attemptCount)
    }

    @Test
    fun emptySummaryKeepsMissingValuesNull() {
        val summary = SmashSpeedSummary.from("2026-06-29", emptyList())

        assertNull(summary.bestSpeedKmh)
        assertNull(summary.averageSpeedKmh)
        assertNull(summary.top3AverageSpeedKmh)
        assertEquals(0, summary.attemptCount)
    }
}
