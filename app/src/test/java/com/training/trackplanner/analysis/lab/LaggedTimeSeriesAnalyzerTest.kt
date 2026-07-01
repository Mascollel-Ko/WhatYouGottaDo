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
}
