package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LaggedTimeSeriesAnalyzerTest {
    private val analyzer = LaggedTimeSeriesAnalyzer()

    @Test
    fun detectsPositiveTwoWeekPatternWithoutAutoFillingMissingValues() {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 24).map { start.plusWeeks(it.toLong()) }
        val x = weeks.mapIndexed { index, week -> TrendDataPoint(week, if (index % 2 == 0) 1.0 else -1.0) }
        val y = weeks.mapIndexed { index, week ->
            val source = index - 2
            TrendDataPoint(week, if (source >= 0) x[source].value!! * 0.8 + index * 0.01 else index * 0.01)
        }

        val result = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = mapOf(
                TrendMetricId.BADMINTON_TRAINING to x,
                TrendMetricId.FATIGUE_COMPOSITE to y
            )
        )

        val twoWeek = result.points.first { it.horizonWeeks == 2 }
        assertTrue(twoWeek.estimate > 0.0)
        assertTrue(result.points.none { it.estimate.isNaN() || it.estimate.isInfinite() })
    }

    @Test
    fun returnsUnavailableWhenSampleIsTooSmall() {
        val start = LocalDate.parse("2026-01-05")
        val series = (0 until 5).map { TrendDataPoint(start.plusWeeks(it.toLong()), it.toDouble()) }

        val result = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = mapOf(
                TrendMetricId.BADMINTON_TRAINING to series,
                TrendMetricId.FATIGUE_COMPOSITE to series
            )
        )

        assertTrue(result.points.isEmpty())
        assertTrue(result.summary.contains("부족"))
    }

    @Test
    fun returnsNoPointsForZeroVarianceX() {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 16).map { start.plusWeeks(it.toLong()) }
        val x = weeks.map { TrendDataPoint(it, 1.0) }
        val y = weeks.mapIndexed { index, week -> TrendDataPoint(week, index.toDouble()) }

        val result = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = mapOf(
                TrendMetricId.BADMINTON_TRAINING to x,
                TrendMetricId.FATIGUE_COMPOSITE to y
            )
        )

        assertTrue(result.points.isEmpty())
        assertTrue(result.warnings.any { it.contains("변동") })
    }

    @Test
    fun priorPrecisionKeepsMainXLessShrunkThanLagAndControls() {
        assertTrue(laggedPriorPrecision(LaggedCoefficientRole.INTERCEPT) < laggedPriorPrecision(LaggedCoefficientRole.MAIN_X))
        assertTrue(laggedPriorPrecision(LaggedCoefficientRole.MAIN_X) < laggedPriorPrecision(LaggedCoefficientRole.LAG_Y))
        assertTrue(laggedPriorPrecision(LaggedCoefficientRole.MAIN_X) < laggedPriorPrecision(LaggedCoefficientRole.LAG_X))
        assertTrue(laggedPriorPrecision(LaggedCoefficientRole.LAG_X) < laggedPriorPrecision(LaggedCoefficientRole.CONTROL))
    }

    @Test
    fun residualVarianceWidensTheExploratoryInterval() {
        val lowNoise = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = laggedFixture(noise = 0.01)
        ).points.first { it.horizonWeeks == 2 }
        val highNoise = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = laggedFixture(noise = 0.65)
        ).points.first { it.horizonWeeks == 2 }

        assertTrue(highNoise.residualVariance > lowNoise.residualVariance)
        assertTrue(highNoise.intervalWidth80 > lowNoise.intervalWidth80)
    }

    @Test
    fun resultReportsAutomaticLagAdjustments() {
        val result = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = emptyList(),
            metricSeries = laggedFixture(noise = 0.1)
        )

        assertTrue("Y 전주값" in result.automaticAdjustments)
        assertTrue("X 전주값" in result.automaticAdjustments)
    }

    @Test
    fun warnsWhenControlsAreTooManyForTheObservationCount() {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 18).map { start.plusWeeks(it.toLong()) }
        val series = weeks.mapIndexed { index, week -> TrendDataPoint(week, ((index % 5) - 2).toDouble()) }
        val controls = listOf(
            TrendMetricId.SLEEP_HOURS,
            TrendMetricId.STRENGTH_VOLUME,
            TrendMetricId.COURT_VOLUME,
            TrendMetricId.SYSTEMIC_FATIGUE,
            TrendMetricId.BADMINTON_SUPPORT
        )
        val metricSeries = mutableMapOf(
            TrendMetricId.BADMINTON_TRAINING to series,
            TrendMetricId.FATIGUE_COMPOSITE to series.mapIndexed { index, point -> point.copy(value = (index % 7).toDouble()) }
        )
        controls.forEachIndexed { offset, metric ->
            metricSeries[metric] = weeks.mapIndexed { index, week ->
                TrendDataPoint(week, ((index + offset) % 6).toDouble())
            }
        }

        val result = analyzer.analyze(
            xMetric = TrendMetricId.BADMINTON_TRAINING,
            yMetric = TrendMetricId.FATIGUE_COMPOSITE,
            controls = controls,
            metricSeries = metricSeries
        )

        assertTrue(result.warnings.any { it.contains("통제변수") })
    }

    private fun laggedFixture(noise: Double): Map<TrendMetricId, List<TrendDataPoint>> {
        val start = LocalDate.parse("2026-01-05")
        val weeks = (0 until 30).map { start.plusWeeks(it.toLong()) }
        val xValues = (0 until 30).map { ((it % 5) - 2).toDouble() }
        val x = weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues[index]) }
        val y = weeks.mapIndexed { index, week ->
            val source = index - 2
            val deterministicNoise = ((index % 3) - 1) * noise
            TrendDataPoint(week, if (source >= 0) xValues[source] * 0.65 + deterministicNoise else deterministicNoise)
        }
        return mapOf(
            TrendMetricId.BADMINTON_TRAINING to x,
            TrendMetricId.FATIGUE_COMPOSITE to y
        )
    }
}
