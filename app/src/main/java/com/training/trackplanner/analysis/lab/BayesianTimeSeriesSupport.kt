package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

internal class TimeSeriesAlignmentService {
    fun align(
        metrics: Collection<TrendMetricId>,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): TimeSeriesAlignment? {
        val unique = metrics.distinct()
        if (unique.isEmpty()) return null
        val maps = unique.associateWith { metric ->
            series[metric].orEmpty().mapNotNull { point -> point.value?.let { point.weekStart to it } }.toMap()
        }
        val allWeeks = maps.values.flatMap { it.keys }.toSortedSet()
        if (allWeeks.isEmpty()) return null
        val missingRates = maps.mapValues { (_, values) -> 1.0 - values.size.toDouble() / allWeeks.size }
        val commonWeeks = allWeeks.filter { week -> maps.values.all { values -> week in values } }
        return TimeSeriesAlignment(
            weeks = commonWeeks,
            valuesByMetric = maps.mapValues { (_, values) -> commonWeeks.map(values::getValue) },
            excludedMetrics = emptyMap(),
            missingRates = missingRates
        )
    }

    fun usableCandidate(
        metric: TrendMetricId,
        requiredWeeks: Set<LocalDate>,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): String? {
        val values = series[metric].orEmpty().mapNotNull { point -> point.value?.let { point.weekStart to it } }.toMap()
        val missingRate = if (requiredWeeks.isEmpty()) 1.0 else 1.0 - values.keys.intersect(requiredWeeks).size.toDouble() / requiredWeeks.size
        if (missingRate > MAX_MISSING_RATE) return "missing rate exceeds 25%"
        val present = values.filterKeys { it in requiredWeeks }.values
        if (present.size < MIN_OBSERVATIONS || variance(present) <= EPSILON) return "series has insufficient variation"
        return null
    }

    fun restrictToWeeks(
        alignment: TimeSeriesAlignment,
        weeks: List<LocalDate>
    ): TimeSeriesAlignment? {
        val indices = weeks.mapNotNull { week -> alignment.weeks.indexOf(week).takeIf { it >= 0 } }
        if (indices.size != weeks.size) return null
        return alignment.copy(
            weeks = weeks,
            valuesByMetric = alignment.valuesByMetric.mapValues { (_, values) -> indices.map(values::get) }
        )
    }

    fun stationarize(
        alignment: TimeSeriesAlignment,
        diagnostics: List<IntegrationDiagnostic>
    ): Pair<TimeSeriesAlignment, Map<TrendMetricId, String>>? {
        val orders = diagnostics.associate { it.metric to it.levelOrder }
        val transformed = alignment.valuesByMetric.mapValues { (metric, values) ->
            when (orders[metric]) {
                IntegrationOrder.I1 -> values.zipWithNext { first, second -> second - first }
                else -> values.drop(1)
            }
        }
        if (transformed.values.any { it.size < MIN_OBSERVATIONS }) return null
        val descriptions = alignment.valuesByMetric.keys.associateWith { metric ->
            if (orders[metric] == IntegrationOrder.I1) "first difference" else "level"
        }
        return alignment.copy(
            weeks = alignment.weeks.drop(1),
            valuesByMetric = transformed
        ) to descriptions
    }

    private companion object {
        const val MIN_OBSERVATIONS = 8
        const val MAX_MISSING_RATE = 0.25
        const val EPSILON = 1e-9
    }
}

internal class IntegrationOrderAnalyzer {
    fun diagnose(metric: TrendMetricId, values: List<Double>): IntegrationDiagnostic {
        val level = stationarity(values)
        val differences = values.zipWithNext { first, second -> second - first }
        val difference = stationarity(differences)
        val order = when {
            level.stationary -> IntegrationOrder.I0
            difference.stationary -> IntegrationOrder.I1
            differences.size >= 16 && difference.adfStatistic >= -0.5 && difference.kpssStatistic > 1.0 -> IntegrationOrder.I2_OR_HIGHER
            else -> IntegrationOrder.INCONCLUSIVE
        }
        return IntegrationDiagnostic(
            metric = metric,
            levelOrder = order,
            adfLevelStatistic = level.adfStatistic,
            kpssLevelStatistic = level.kpssStatistic,
            adfDifferenceStatistic = difference.adfStatistic,
            kpssDifferenceStatistic = difference.kpssStatistic,
            message = when (order) {
                IntegrationOrder.I0 -> "level-stationary by ADF and KPSS"
                IntegrationOrder.I1 -> "first difference is stationary by ADF and KPSS"
                IntegrationOrder.I2_OR_HIGHER -> "first difference is not stationary"
                IntegrationOrder.INCONCLUSIVE -> "ADF and KPSS are inconclusive"
            }
        )
    }

    private fun stationarity(values: List<Double>): Stationarity {
        if (values.size < 8 || variance(values) <= EPSILON) return Stationarity(0.0, Double.POSITIVE_INFINITY, false)
        val y = values.drop(1).zip(values.dropLast(1)).map { (current, previous) -> current - previous }
        val x = values.dropLast(1)
        val meanX = x.average()
        val meanY = y.average()
        val centeredX = x.map { it - meanX }
        val denominator = centeredX.sumOf { it * it }.coerceAtLeast(EPSILON)
        val rho = centeredX.indices.sumOf { index -> centeredX[index] * (y[index] - meanY) } / denominator
        val residuals = centeredX.indices.map { index -> y[index] - meanY - rho * centeredX[index] }
        val sigma2 = residuals.sumOf { it * it } / maxOf(1, y.size - 2)
        val adf = rho / sqrt((sigma2 / denominator).coerceAtLeast(EPSILON))
        val centered = values.map { it - values.average() }
        val cumulative = centered.runningFold(0.0) { total, value -> total + value }.drop(1)
        val longRun = neweyWestVariance(centered).coerceAtLeast(EPSILON)
        val kpss = cumulative.sumOf { it * it } / (values.size * values.size * longRun)
        return Stationarity(adf, kpss, adf < ADF_REJECT && kpss < KPSS_RETAIN)
    }

    private fun neweyWestVariance(values: List<Double>): Double {
        val lag = sqrt(values.size.toDouble()).toInt().coerceAtLeast(1)
        var variance = values.sumOf { it * it } / values.size
        for (offset in 1..lag) {
            val covariance = values.drop(offset).indices.sumOf { index -> values[index + offset] * values[index] } / values.size
            variance += 2.0 * (1.0 - offset.toDouble() / (lag + 1)) * covariance
        }
        return variance
    }

    private data class Stationarity(val adfStatistic: Double, val kpssStatistic: Double, val stationary: Boolean)

    private companion object {
        const val EPSILON = 1e-9
        const val ADF_REJECT = -2.86
        const val KPSS_RETAIN = 0.463
    }
}

internal class BayesianLinearRegression private constructor() {
    data class Posterior(
        val mean: DoubleArray,
        val covariance: Array<DoubleArray>,
        val residualVariance: Double,
        val logEvidence: Double
    )

    companion object {
        fun fit(x: List<DoubleArray>, y: DoubleArray, priorPrecision: DoubleArray): Posterior? {
            val columns = x.firstOrNull()?.size ?: return null
            if (x.size != y.size || priorPrecision.size != columns) return null
            val xtx = Array(columns) { DoubleArray(columns) }
            val xty = DoubleArray(columns)
            x.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { i, xi ->
                    xty[i] += xi * y[rowIndex]
                    row.forEachIndexed { j, xj -> xtx[i][j] += xi * xj }
                }
            }
            priorPrecision.indices.forEach { index -> xtx[index][index] += priorPrecision[index] }
            val covarianceBase = invert(xtx) ?: return null
            val mean = DoubleArray(columns) { row -> covarianceBase[row].indices.sumOf { col -> covarianceBase[row][col] * xty[col] } }
            val residuals = x.indices.map { row -> y[row] - x[row].indices.sumOf { col -> x[row][col] * mean[col] } }
            val sigma2 = (residuals.sumOf { it * it } / maxOf(1, x.size - columns)).coerceAtLeast(1e-9)
            val covariance = Array(columns) { row -> DoubleArray(columns) { col -> covarianceBase[row][col] * sigma2 } }
            val logEvidence = -0.5 * (x.size * ln(2.0 * Math.PI * sigma2) + residuals.sumOf { it * it } / sigma2 + logDeterminant(xtx))
            return Posterior(mean, covariance, sigma2, logEvidence)
        }

        fun invert(matrix: Array<DoubleArray>): Array<DoubleArray>? {
            val n = matrix.size
            val augmented = Array(n) { row -> DoubleArray(n * 2) { col -> if (col < n) matrix[row][col] else if (col - n == row) 1.0 else 0.0 } }
            for (pivot in 0 until n) {
                val best = (pivot until n).maxBy { row -> abs(augmented[row][pivot]) }
                if (abs(augmented[best][pivot]) <= 1e-10) return null
                val swap = augmented[pivot]
                augmented[pivot] = augmented[best]
                augmented[best] = swap
                val divisor = augmented[pivot][pivot]
                augmented[pivot].indices.forEach { column -> augmented[pivot][column] /= divisor }
                augmented.indices.filter { it != pivot }.forEach { row ->
                    val factor = augmented[row][pivot]
                    augmented[row].indices.forEach { column -> augmented[row][column] -= factor * augmented[pivot][column] }
                }
            }
            return Array(n) { row -> augmented[row].copyOfRange(n, n * 2) }
        }

        private fun logDeterminant(matrix: Array<DoubleArray>): Double {
            val copy = Array(matrix.size) { row -> matrix[row].copyOf() }
            var logDet = 0.0
            for (pivot in copy.indices) {
                val best = (pivot until copy.size).maxBy { row -> abs(copy[row][pivot]) }
                if (abs(copy[best][pivot]) <= 1e-10) return 1e6
                if (best != pivot) {
                    val swap = copy[pivot]
                    copy[pivot] = copy[best]
                    copy[best] = swap
                }
                val value = abs(copy[pivot][pivot])
                logDet += ln(value)
                for (row in pivot + 1 until copy.size) {
                    val factor = copy[row][pivot] / copy[pivot][pivot]
                    for (column in pivot until copy.size) copy[row][column] -= factor * copy[pivot][column]
                }
            }
            return logDet
        }
    }
}

internal fun variance(values: Collection<Double>): Double {
    if (values.isEmpty()) return 0.0
    val mean = values.average()
    return values.sumOf { value -> (value - mean) * (value - mean) } / values.size
}

internal fun normalizeLogWeights(logWeights: Map<Int, Double>): Map<Int, Double> {
    val max = logWeights.values.maxOrNull() ?: return emptyMap()
    val weights = logWeights.mapValues { (_, value) -> exp(value - max) }
    val total = weights.values.sum().coerceAtLeast(1e-12)
    return weights.mapValues { (_, value) -> value / total }
}
