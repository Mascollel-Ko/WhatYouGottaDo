package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesCalendarGridTest {
    @Test
    fun alignmentBuildsContinuousIsoWeekGridAndPreservesMissingWeeks() {
        val alignment = fixture()

        assertEquals(
            listOf("2025-12-22", "2025-12-29", "2026-01-05", "2026-01-12"),
            alignment.weeks.map(LocalDate::toString)
        )
        assertEquals(
            listOf(
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.STRUCTURAL_ZERO,
                TimeSeriesCellState.OBSERVED_VALUE
            ),
            alignment.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).map(TimeSeriesCell::state)
        )
        assertEquals(
            listOf(
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.MISSING,
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.OBSERVED_VALUE
            ),
            alignment.grid!!.cellsByMetric.getValue(TrendMetricId.FATIGUE_COMPOSITE).map(TimeSeriesCell::state)
        )
    }

    @Test
    fun structuralZeroIsNotTreatedAsMissingValue() {
        val alignment = fixture()

        assertEquals(0.0, alignment.valueAt(TrendMetricId.BADMINTON_TRAINING, 2)!!, 0.0)
        assertNull(alignment.valueAt(TrendMetricId.FATIGUE_COMPOSITE, 1))
        assertEquals(0.0, alignment.missingRates.getValue(TrendMetricId.BADMINTON_TRAINING), 0.0)
        assertEquals(0.25, alignment.missingRates.getValue(TrendMetricId.FATIGUE_COMPOSITE), 0.0)
    }

    @Test
    fun lagDifferenceAndHorizonRequireExactCalendarWeeks() {
        val alignment = fixture()

        assertEquals(0.0, alignment.exactLag(TrendMetricId.BADMINTON_TRAINING, 3, 1)!!, 0.0)
        assertNull(alignment.exactLag(TrendMetricId.FATIGUE_COMPOSITE, 2, 1))
        assertNull(alignment.exactDifference(TrendMetricId.FATIGUE_COMPOSITE, 2))
        assertEquals(3.0, alignment.exactHorizon(TrendMetricId.FATIGUE_COMPOSITE, 0, 2)!!, 0.0)
    }

    @Test
    fun exactRowsKeepExclusionReasons() {
        val alignment = fixture()
        val (rows, exclusions) = alignment.buildExactRows(
            sourceMetric = TrendMetricId.FATIGUE_COMPOSITE,
            targetMetric = TrendMetricId.BADMINTON_TRAINING,
            lag = 1,
            horizon = 1
        )

        assertTrue(rows.isEmpty())
        assertTrue(exclusions.any { it.reason in setOf(TimeSeriesRowExclusionReason.MISSING_SOURCE, TimeSeriesRowExclusionReason.MISSING_LAG) })
        assertTrue(exclusions.all { it.sourceWeek != null && it.cellStates.isNotEmpty() })
    }

    private fun fixture(): TimeSeriesAlignment {
        val weeks = listOf("2025-12-22", "2025-12-29", "2026-01-12").map(LocalDate::parse)
        return TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to weeks.mapIndexed { index, week -> TrendDataPoint(week, (index + 1).toDouble()) },
                TrendMetricId.FATIGUE_COMPOSITE to listOf(
                    TrendDataPoint(LocalDate.parse("2025-12-22"), 0.0),
                    TrendDataPoint(LocalDate.parse("2026-01-05"), 3.0),
                    TrendDataPoint(LocalDate.parse("2026-01-12"), 5.0)
                )
            ),
            structuralZeroMetrics = setOf(TrendMetricId.BADMINTON_TRAINING)
        )!!
    }
}
