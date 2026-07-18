package com.training.trackplanner

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStrengthChartSpecTest {
    @Test
    fun e1rmDomainUsesAllExerciseWeeksWithoutFillingMissingValues() {
        val first = LocalDate.of(2026, 1, 5)
        val squat = (1L..9L).map { offset -> TrendDataPoint(first.plusWeeks(offset), 120.0 + offset) }
        val deadlift = (0L..7L).map { offset -> TrendDataPoint(first.plusWeeks(offset), 150.0 + offset) }
        val bench = listOf(TrendDataPoint(first.plusWeeks(4), 90.0))

        val spec = mainLiftE1rmSpec(
            mapOf(
                TrendMetricId.SQUAT_E1RM to squat,
                TrendMetricId.DEADLIFT_E1RM to deadlift,
                TrendMetricId.BENCH_PRESS_E1RM to bench
            )
        )

        assertEquals(first, spec.xDomain.first())
        assertEquals(first.plusWeeks(9), spec.xDomain.last())
        assertEquals(10, spec.xDomain.size)
        assertEquals(1, spec.lineSeries.first { it.label.contains("벤치") }.points.size)
        assertFalse(spec.lineSeries.flatMap { it.points }.any { it.value == 0.0 })
        assertTrue(spec.yMin!! < 90.0)
        assertTrue(spec.yMax!! > 157.0)
    }

    @Test
    fun e1rmSingleObservationRemainsOnePoint() {
        val week = LocalDate.of(2026, 7, 6)
        val spec = mainLiftE1rmSpec(
            mapOf(TrendMetricId.BENCH_PRESS_E1RM to listOf(TrendDataPoint(week, 100.0)))
        )

        assertEquals(listOf(week), spec.xDomain)
        assertEquals(1, spec.lineSeries.single().points.size)
        assertEquals(100.0, spec.lineSeries.single().points.single().value!!, 0.001)
    }
}
