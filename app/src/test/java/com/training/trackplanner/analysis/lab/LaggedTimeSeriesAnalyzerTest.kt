package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaggedTimeSeriesAnalyzerTest {
    @Test
    fun BayesianLocalProjectionKeepsMissingWeeksMissingAndReturnsHorizonZeroThroughTwo() {
        val result = LegacyTimeSeriesAnalyzer().analyze(
            TimeSeriesAnalysisRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 2),
            stationaryFixture(52)
        )

        assertEquals(BayesianTimeSeriesModel.BAYESIAN_LOCAL_PROJECTION, result.model)
        assertEquals(listOf(0, 1, 2), result.responses.single().points.map { it.horizonWeeks })
        assertTrue(result.responses.single().points.all { it.observations >= 24 })
    }

    @Test
    fun requestedHorizonIsReducedInsteadOfFillingFutureWeeks() {
        val result = LegacyTimeSeriesAnalyzer().analyze(
            TimeSeriesAnalysisRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 8),
            stationaryFixture(31)
        )

        assertTrue(result.usedHorizon in 1 until 8)
        assertTrue(result.responses.single().points.last().horizonWeeks == result.usedHorizon)
    }

    @Test
    fun zeroVarianceShockDoesNotProduceCorrelationFallback() {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 40).map { start.plusWeeks(it.toLong()) }
        val result = LegacyTimeSeriesAnalyzer().analyze(
            TimeSeriesAnalysisRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 2),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to weeks.map { TrendDataPoint(it, 1.0) },
                TrendMetricId.FATIGUE_COMPOSITE to weeks.mapIndexed { index, week -> TrendDataPoint(week, index.toDouble()) }
            )
        )

        assertEquals(BayesianTimeSeriesModel.UNAVAILABLE, result.model)
        assertTrue(result.responses.isEmpty())
    }

    @Test
    fun integrationAnalyzerSeparatesStationaryAndRandomWalkSeries() {
        val analyzer = IntegrationOrderAnalyzer()
        val stationary = (0 until 64).map { index -> if (index % 2 == 0) 1.0 else -1.0 }
        val randomWalk = (0 until 64).runningFold(0.0) { total, index -> total + if (index % 2 == 0) 1.0 else -0.35 }.dropLast(1)

        assertEquals(IntegrationOrder.I0, analyzer.diagnose(TrendMetricId.BADMINTON_TRAINING, stationary).levelOrder)
        assertTrue(analyzer.diagnose(TrendMetricId.FATIGUE_COMPOSITE, randomWalk).levelOrder in setOf(IntegrationOrder.I1, IntegrationOrder.INCONCLUSIVE))
    }

    @Test
    fun lagPosteriorIsSharedAcrossEveryResponseHorizon() {
        val result = LegacyTimeSeriesAnalyzer().analyze(
            TimeSeriesAnalysisRequest(
                TrendMetricId.BADMINTON_TRAINING,
                listOf(TrendMetricId.FATIGUE_COMPOSITE, TrendMetricId.STRENGTH_PERFORMANCE),
                emptyList(),
                2
            ),
            stationaryFixture(56, includeStrength = true)
        )

        assertTrue(result.lagPosterior!!.probabilities.values.sum() in 0.999..1.001)
        assertFalse(result.responses.any { response -> response.points.map { it.horizonWeeks } != listOf(0, 1, 2) })
    }

    @Test
    fun alignmentKeepsActualZerosAndDoesNotForwardFillMissingWeeks() {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 32).map { start.plusWeeks(it.toLong()) }
        val x = weeks.mapIndexed { index, week -> TrendDataPoint(week, if (index % 4 == 0) 0.0 else index.toDouble()) }
        val y = weeks.mapIndexedNotNull { index, week ->
            if (index in setOf(4, 10, 16, 22, 28)) null else TrendDataPoint(week, index.toDouble() / 2.0)
        }

        val alignment = TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            mapOf(TrendMetricId.BADMINTON_TRAINING to x, TrendMetricId.FATIGUE_COMPOSITE to y)
        )!!

        assertEquals(32, alignment.weeks.size)
        assertTrue(alignment.valuesByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).contains(0.0))
        assertEquals(5.0 / 32.0, alignment.qualitySummaries.getValue(TrendMetricId.FATIGUE_COMPOSITE).rawMissingRate, 1e-9)
    }

    @Test
    fun rollingOriginScoreUsesHeldOutWeeklyResponses() {
        val fixture = stationaryFixture(56, includeStrength = true)
        val alignment = TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE, TrendMetricId.STRENGTH_PERFORMANCE),
            fixture
        )!!

        val score = BayesianLocalProjectionEstimator().rollingPredictiveScore(
            alignment,
            TrendMetricId.BADMINTON_TRAINING,
            TrendMetricId.FATIGUE_COMPOSITE,
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE, TrendMetricId.STRENGTH_PERFORMANCE),
            emptyList()
        )

        assertTrue(score != null)
        assertTrue(score!!.origins >= 3)
        assertTrue(score.logPredictiveDensity.isFinite())
        assertTrue(score.rmse.isFinite())
    }

    @Test
    fun canonicalCholeskyOrderExcludesExogenousControlsAndDoesNotForceXFirst() {
        val identifier = CholeskyShockIdentifier()
        val order = identifier.canonicalOrder(
            listOf(TrendMetricId.FATIGUE_COMPOSITE, TrendMetricId.BADMINTON_TRAINING, TrendMetricId.SQUAT_E1RM)
        )

        assertEquals(TrendMetricId.BADMINTON_TRAINING, order.first())
        assertFalse(TrendMetricId.SLEEP_HOURS in order)
    }

    @Test
    fun legacyCointegrationDiagnosticDoesNotRouteToBayesianVecm() {
        val result = LegacyTimeSeriesAnalyzer().analyze(
            TimeSeriesAnalysisRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 2),
            cointegratedFixture(72)
        )

        assertFalse(result.model == BayesianTimeSeriesModel.BAYESIAN_VECM)
        assertFalse(result.cointegration!!.supportedForModelRouting)
        assertTrue(result.cointegration.diagnosticOnly)
    }

    private fun stationaryFixture(count: Int, includeStrength: Boolean = false): Map<TrendMetricId, List<TrendDataPoint>> {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until count).map { start.plusWeeks(it.toLong()) }
        val xValues = (0 until count).map { index -> ((index % 7) - 3).toDouble() + if (index % 3 == 0) 0.2 else -0.1 }
        val x = weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues[index]) }
        val fatigue = weeks.mapIndexed { index, week ->
            val lagged = xValues.getOrElse((index - 2).coerceAtLeast(0)) { 0.0 }
            TrendDataPoint(week, lagged * 0.7 + ((index % 5) - 2) * 0.08)
        }
        return buildMap {
            put(TrendMetricId.BADMINTON_TRAINING, x)
            put(TrendMetricId.FATIGUE_COMPOSITE, fatigue)
            if (includeStrength) put(
                TrendMetricId.STRENGTH_PERFORMANCE,
                weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues.getOrElse((index - 1).coerceAtLeast(0)) { 0.0 } * 0.4 + index % 4) }
            )
        }
    }

    private fun cointegratedFixture(count: Int): Map<TrendMetricId, List<TrendDataPoint>> {
        val start = LocalDate.parse("2025-01-06")
        val weeks = (0 until count).map { start.plusWeeks(it.toLong()) }
        val xValues = (0 until count).map { index -> index.toDouble() + ((index * 7 % 5) - 2).toDouble() * 0.2 }
        val yValues = xValues.mapIndexed { index, value -> value * 0.9 + ((index * 11 % 7) - 3).toDouble() * 0.12 }
        return mapOf(
            TrendMetricId.BADMINTON_TRAINING to weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues[index]) },
            TrendMetricId.FATIGUE_COMPOSITE to weeks.mapIndexed { index, week -> TrendDataPoint(week, yValues[index]) }
        )
    }
}
