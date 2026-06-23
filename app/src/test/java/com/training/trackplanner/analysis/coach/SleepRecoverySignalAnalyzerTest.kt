package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRecoverySignalAnalyzerTest {
    private val today = LocalDate.of(2026, 6, 23)
    private val analyzer = SleepRecoverySignalAnalyzer()

    @Test
    fun recentSleepBelowBaselineCreatesWatchOrCaution() {
        val metrics = (3..16).map { daysAgo ->
            DailyMetric(today.minusDays(daysAgo.toLong()).toString(), sleepHours = 7.5)
        } + listOf(
            DailyMetric(today.minusDays(2).toString(), sleepHours = 5.4),
            DailyMetric(today.minusDays(1).toString(), sleepHours = 5.6),
            DailyMetric(today.toString(), sleepHours = 5.2)
        )

        val signal = analyzer.analyze(today, metrics)

        assertEquals(CoachingSignalSeverity.WATCH, signal.severity)
        assertTrue((signal.sleepDeficitHours ?: 0.0) > 1.0)
    }

    @Test
    fun veryLowRecentSleepCreatesCaution() {
        val signal = analyzer.analyze(
            today,
            listOf(DailyMetric(today.toString(), sleepHours = 4.5))
        )

        assertEquals(CoachingSignalSeverity.CAUTION, signal.severity)
    }

    @Test
    fun missingSleepDoesNotBecomeZero() {
        val signal = analyzer.analyze(today, dailyMetrics = emptyList())

        assertEquals(CoachingSignalSeverity.NONE, signal.severity)
        assertNull(signal.recentAverageHours)
    }

    @Test
    fun dailyMetricOverridesCheckInSleepFallback() {
        val signal = analyzer.analyze(
            today = today,
            dailyMetrics = listOf(DailyMetric(today.toString(), sleepHours = 7.0)),
            checkIns = listOf(DailyCheckIn(today.toString(), sleepHours = 4.0))
        )

        assertEquals(7.0, signal.recentAverageHours ?: 0.0, 0.001)
        assertEquals(CoachingSignalSeverity.INFO, signal.severity)
    }
}
