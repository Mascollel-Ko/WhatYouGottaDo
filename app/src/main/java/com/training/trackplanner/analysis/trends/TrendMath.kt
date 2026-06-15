package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import kotlin.math.sqrt

internal object TrendMath {
    fun safeDivide(
        numerator: Double?,
        denominator: Double?,
        fallback: Double = 1.0
    ): Double {
        if (numerator == null || denominator == null) return fallback
        if (denominator <= PerformanceTrendConstants.EPSILON) return fallback
        return numerator / denominator
    }

    fun clamp(value: Double, min: Double, max: Double): Double =
        value.coerceIn(min, max)

    fun higherIsBetterScore(currentValue: Double?, baselineValue: Double?): Double =
        clamp(
            value = 100.0 * safeDivide(currentValue, baselineValue, fallback = 1.0),
            min = PerformanceTrendConstants.STANDARD_MIN,
            max = PerformanceTrendConstants.STANDARD_MAX
        )

    fun lowerIsBetterScore(currentValue: Double?, baselineValue: Double?): Double =
        clamp(
            value = 100.0 * safeDivide(baselineValue, currentValue, fallback = 1.0),
            min = PerformanceTrendConstants.STANDARD_MIN,
            max = PerformanceTrendConstants.STANDARD_MAX
        )

    fun percentileScore(percentile: Double?): Double =
        clamp(
            value = 50.0 + (percentile ?: 50.0),
            min = PerformanceTrendConstants.STANDARD_MIN,
            max = PerformanceTrendConstants.PERCENTILE_MAX
        )

    fun zScoreBasedScore(zScore: Double?): Double =
        if (zScore == null || zScore.isNaN() || zScore.isInfinite()) {
            100.0
        } else {
            clamp(
                value = 100.0 + PerformanceTrendConstants.Z_SCORE_WEIGHT * zScore,
                min = PerformanceTrendConstants.STANDARD_MIN,
                max = PerformanceTrendConstants.STANDARD_MAX
            )
        }

    fun weightedMean(
        values: List<Double?>,
        weights: List<Double>,
        fallback: Double = 100.0
    ): Double {
        val paired = values.zip(weights)
            .filter { (value, weight) -> value != null && weight > 0.0 }
        val weightSum = paired.sumOf { (_, weight) -> weight }
        if (paired.isEmpty() || weightSum <= PerformanceTrendConstants.EPSILON) return fallback
        return paired.sumOf { (value, weight) -> value!! * weight } / weightSum
    }

    fun mean(values: List<Double>): Double =
        if (values.isEmpty()) 100.0 else values.average()

    fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
        return sqrt(variance)
    }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    fun baselineFor(
        values: List<Double?>,
        index: Int,
        fallback: Double? = null
    ): Pair<Double?, AnalysisConfidence> {
        val previous = values
            .take(index)
            .mapNotNull { value -> value?.takeIf { it > PerformanceTrendConstants.EPSILON } }
        val candidateWindow = when {
            previous.size >= 12 -> previous.takeLast(12)
            previous.size >= 8 -> previous.takeLast(8)
            previous.size >= 6 -> previous.takeLast(6)
            previous.size >= 2 -> previous
            else -> emptyList()
        }
        val baseline = median(winsorize(candidateWindow))
            ?: fallback
            ?: median(winsorize(values.mapNotNull { it?.takeIf { value -> value > PerformanceTrendConstants.EPSILON } }))
        val confidence = confidenceForDays(previous.size * 7)
        return baseline to confidence
    }

    fun confidenceForDays(days: Int): AnalysisConfidence =
        when {
            days >= 84 -> AnalysisConfidence.HIGH
            days >= 42 -> AnalysisConfidence.MEDIUM
            days >= 14 -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }

    fun combineConfidence(values: List<AnalysisConfidence>): AnalysisConfidence =
        values.minByOrNull { confidence -> confidence.ordinal } ?: AnalysisConfidence.LOW

    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < PerformanceTrendConstants.SCATTER_MIN_POINTS) return null
        val xMean = xs.average()
        val yMean = ys.average()
        val xStd = standardDeviation(xs)
        val yStd = standardDeviation(ys)
        if (xStd <= PerformanceTrendConstants.EPSILON || yStd <= PerformanceTrendConstants.EPSILON) return null
        val covariance = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) } / xs.size
        return covariance / (xStd * yStd)
    }

    private fun winsorize(values: List<Double>): List<Double> {
        if (values.size < 5) return values
        val sorted = values.sorted()
        val lower = sorted[(sorted.size * 0.05).toInt().coerceAtMost(sorted.lastIndex)]
        val upper = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]
        return values.map { value -> value.coerceIn(lower, upper) }
    }
}
