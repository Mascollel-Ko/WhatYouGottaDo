package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import kotlin.math.sqrt

class TrendForecastRangeCalculator {
    fun calculate(points: List<TrendDataPoint>, confidence: AnalysisConfidence): ForecastRange? {
        val values = points.mapNotNull { point -> point.value }
        if (values.size < PerformanceTrendConstants.FORECAST_MIN_POINTS) return null
        if (confidence == AnalysisConfidence.LOW) return null

        val recent = values.takeLast(8)
        val xs = recent.indices.map { index -> index.toDouble() }
        val slope = slope(xs, recent) ?: return null
        val intercept = recent.first()
        val fitted = xs.map { x -> intercept + slope * x }
        val residuals = recent.zip(fitted).map { (actual, fit) -> actual - fit }
        val residualStd = TrendMath.standardDeviation(residuals)
        val lastValue = recent.last()
        val lastWeek = points.lastOrNull()?.weekStart ?: return null
        val forecast = (1..PerformanceTrendConstants.FORECAST_WEEKS).map { h ->
            val center = lastValue + slope * h
            val range = PerformanceTrendConstants.RANGE_MULTIPLIER * residualStd * sqrt(h.toDouble())
            ForecastPoint(
                weekStart = lastWeek.plusWeeks(h.toLong()),
                lower = TrendMath.clamp(center - range, PerformanceTrendConstants.STANDARD_MIN, PerformanceTrendConstants.FATIGUE_MAX),
                center = TrendMath.clamp(center, PerformanceTrendConstants.STANDARD_MIN, PerformanceTrendConstants.FATIGUE_MAX),
                upper = TrendMath.clamp(center + range, PerformanceTrendConstants.STANDARD_MIN, PerformanceTrendConstants.FATIGUE_MAX)
            )
        }
        return ForecastRange(points = forecast, confidence = confidence)
    }

    private fun slope(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 2) return null
        val xMean = xs.average()
        val yMean = ys.average()
        val variance = xs.sumOf { x -> (x - xMean) * (x - xMean) }
        if (variance <= PerformanceTrendConstants.EPSILON) return null
        val covariance = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) }
        return covariance / variance
    }
}
