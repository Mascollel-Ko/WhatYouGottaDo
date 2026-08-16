package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesAnalysisPreflightTest {
    private val policy = TimeSeriesAnalysisPreflightPolicy()
    private val request = TimeSeriesAnalysisRequest(
        TrendMetricId.BADMINTON_PRACTICE_LOAD,
        listOf(TrendMetricId.FATIGUE_COMPOSITE),
        emptyList(),
        2
    )

    @Test
    fun noUsableSeriesIsBlocked() {
        val result = policy.evaluate(request, emptyMap())

        assertEquals(TimeSeriesPreflightStatus.BLOCKED, result.status)
        assertTrue(result.blockers.any { it.code == TimeSeriesPreflightBlockerCode.REQUIRED_SERIES_UNAVAILABLE })
    }

    @Test
    fun insufficientCommonWeeksIsBlocked() {
        val result = policy.evaluate(request, stationaryFixture(20))

        assertEquals(TimeSeriesPreflightStatus.BLOCKED, result.status)
        assertTrue(result.blockers.any { it.code == TimeSeriesPreflightBlockerCode.INSUFFICIENT_USABLE_HISTORY })
    }

    @Test
    fun sufficientHistoryReportsRangeWeeksAndRows() {
        val result = policy.evaluate(request, stationaryFixture(52))

        assertEquals(TimeSeriesPreflightStatus.READY, result.status)
        assertEquals(52, result.alignedWeeks)
        assertTrue(result.transformedUsableWeeks >= 51)
        assertTrue(result.requestedEstimatorRows >= result.requiredMinimumRows)
        assertNotNull(result.availableFrom)
        assertNotNull(result.availableUntil)
    }

    @Test
    fun missingRequiredMetricNamesTheBlocker() {
        val series = stationaryFixture(52) - TrendMetricId.FATIGUE_COMPOSITE

        val result = policy.evaluate(request, series)

        assertTrue(
            result.blockers.any {
                it.code == TimeSeriesPreflightBlockerCode.REQUIRED_SERIES_UNAVAILABLE &&
                    it.metric == TrendMetricId.FATIGUE_COMPOSITE
            }
        )
    }

    @Test
    fun internalGapsRemainMissingAndReduceUsableRows() {
        val fixture = stationaryFixture(52).toMutableMap()
        fixture[TrendMetricId.FATIGUE_COMPOSITE] = fixture.getValue(TrendMetricId.FATIGUE_COMPOSITE)
            .filterIndexed { index, _ -> index !in setOf(12, 18, 27) }

        val result = policy.evaluate(request, fixture)

        assertTrue(result.transformedUsableWeeks < result.alignedWeeks)
        assertTrue(result.warnings.any { it.code == TimeSeriesPreflightWarningCode.INTERNAL_GAPS_REDUCE_ROWS })
    }

    @Test
    fun requestedHorizonCanReduceRowsWithoutBlockingShorterFeasibleHorizon() {
        val result = policy.evaluate(request.copy(requestedHorizon = 8), stationaryFixture(31))

        assertEquals(TimeSeriesPreflightStatus.READY, result.status)
        assertTrue(result.maximumFeasibleHorizon in 1..7)
        assertTrue(result.warnings.any { it.code == TimeSeriesPreflightWarningCode.REQUESTED_HORIZON_WILL_BE_REDUCED })
    }

    @Test
    fun addingSparseControlReducesEstimatorRows() {
        val baseSeries = stationaryFixture(52)
        val base = policy.evaluate(request, baseSeries)
        val start = LocalDate.parse("2026-01-05")
        val control = (0 until 52).filter { it % 2 == 0 }.map { index ->
            TrendDataPoint(start.plusWeeks(index.toLong()), 6.0 + (index % 5) * 0.2)
        }
        val controlled = policy.evaluate(
            request.copy(controls = listOf(TrendMetricId.SLEEP_HOURS)),
            baseSeries + (TrendMetricId.SLEEP_HOURS to control)
        )

        assertTrue(controlled.requestedEstimatorRows < base.requestedEstimatorRows)
    }

    @Test
    fun zeroVarianceShockIsBlockedBeforeEstimation() {
        val fixture = stationaryFixture(52).toMutableMap()
        fixture[TrendMetricId.BADMINTON_PRACTICE_LOAD] = fixture.getValue(TrendMetricId.BADMINTON_PRACTICE_LOAD)
            .map { it.copy(value = 1.0) }

        val result = policy.evaluate(request, fixture)

        assertEquals(TimeSeriesPreflightStatus.BLOCKED, result.status)
        assertTrue(
            result.blockers.any {
                it.code in setOf(
                    TimeSeriesPreflightBlockerCode.INSUFFICIENT_VARIATION,
                    TimeSeriesPreflightBlockerCode.TRANSFORMATION_UNAVAILABLE
                )
            }
        )
    }

    private fun stationaryFixture(count: Int): Map<TrendMetricId, List<TrendDataPoint>> {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until count).map { start.plusWeeks(it.toLong()) }
        val xValues = (0 until count).map { index -> ((index % 7) - 3).toDouble() + if (index % 3 == 0) 0.2 else -0.1 }
        return mapOf(
            TrendMetricId.BADMINTON_PRACTICE_LOAD to weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues[index]) },
            TrendMetricId.FATIGUE_COMPOSITE to weeks.mapIndexed { index, week ->
                val lagged = xValues.getOrElse((index - 2).coerceAtLeast(0)) { 0.0 }
                TrendDataPoint(week, lagged * 0.7 + ((index % 5) - 2) * 0.08)
            }
        )
    }
}
