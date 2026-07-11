package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.exp
import kotlin.math.ln

internal class CointegrationAnalyzer {
    fun analyze(
        metrics: List<TrendMetricId>,
        alignment: TimeSeriesAlignment,
        integration: List<IntegrationDiagnostic>
    ): CointegrationDiagnostic {
        if (metrics.size < 2 || metrics.any { metric -> integration.firstOrNull { it.metric == metric }?.levelOrder != IntegrationOrder.I1 }) {
            return CointegrationDiagnostic(null, 0.0, null, false, "VECM requires an all-I(1) endogenous block")
        }
        val levels = metrics.map { metric -> alignment.valuesByMetric[metric] ?: return CointegrationDiagnostic(null, 0.0, null, false, "missing endogenous series") }
        if (levels.any { it.size < 24 }) return CointegrationDiagnostic(null, 0.0, null, false, "insufficient observations for Johansen trace")
        val differences = levels.map { values -> values.zipWithNext { first, second -> second - first } }
        val laggedLevels = levels.map { values -> values.dropLast(1) }
        val s00 = covariance(differences)
        val s11 = covariance(laggedLevels)
        val s01 = crossCovariance(differences, laggedLevels)
        val s10 = transpose(s01)
        val inverse00 = BayesianLinearRegression.invert(s00) ?: return CointegrationDiagnostic(null, 0.0, null, false, "singular differenced covariance")
        val inverse11 = BayesianLinearRegression.invert(s11) ?: return CointegrationDiagnostic(null, 0.0, null, false, "singular level covariance")
        val johansen = multiply(multiply(multiply(inverse11, s10), inverse00), s01)
        val eigen = largestEigenvalue(johansen).coerceIn(0.0, 0.999999)
        val trace = -laggedLevels.first().size * ln(1.0 - eigen)
        val posterior = 1.0 / (1.0 + exp(-(trace - TRACE_RANK_ONE_THRESHOLD) / 3.0))
        val supported = trace >= TRACE_RANK_ONE_THRESHOLD && posterior >= 0.80
        val beta = dominantEigenvector(johansen)
        return CointegrationDiagnostic(
            rank = if (supported) 1 else 0,
            posteriorProbabilityRankPositive = posterior,
            johansenTraceStatistic = trace,
            isSupported = supported,
            message = if (supported) "Johansen trace and Bayesian rank posterior support rank 1" else "cointegration evidence is insufficient or conflicted",
            cointegrationVector = beta.takeIf { supported }
        )
    }

    private fun covariance(values: List<List<Double>>): Array<DoubleArray> = crossCovariance(values, values)

    private fun crossCovariance(left: List<List<Double>>, right: List<List<Double>>): Array<DoubleArray> {
        val size = left.first().size
        val leftMeans = left.map(List<Double>::average)
        val rightMeans = right.map(List<Double>::average)
        return Array(left.size) { row ->
            DoubleArray(right.size) { column ->
                (0 until size).sumOf { index -> (left[row][index] - leftMeans[row]) * (right[column][index] - rightMeans[column]) } / size
            }
        }
    }

    private fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(right[0].size) { column -> left[row].indices.sumOf { index -> left[row][index] * right[index][column] } } }

    private fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> =
        Array(matrix[0].size) { row -> DoubleArray(matrix.size) { column -> matrix[column][row] } }

    private fun largestEigenvalue(matrix: Array<DoubleArray>): Double {
        return dominantEigenvector(matrix).let { vector ->
            val product = DoubleArray(matrix.size) { row -> matrix[row].indices.sumOf { column -> matrix[row][column] * vector[column] } }
            val denominator = vector.sumOf { it * it }.coerceAtLeast(1e-12)
            vector.indices.sumOf { index -> vector[index] * product[index] } / denominator
        }
    }

    private fun dominantEigenvector(matrix: Array<DoubleArray>): List<Double> {
        var vector = DoubleArray(matrix.size) { 1.0 / matrix.size }
        repeat(40) {
            val next = DoubleArray(matrix.size) { row -> matrix[row].indices.sumOf { column -> matrix[row][column] * vector[column] } }
            val scale = next.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1e-12) ?: return vector.toList()
            vector = DoubleArray(next.size) { index -> next[index] / scale }
        }
        val norm = kotlin.math.sqrt(vector.sumOf { it * it }).coerceAtLeast(1e-12)
        return vector.map { it / norm }
    }

    private companion object {
        const val TRACE_RANK_ONE_THRESHOLD = 12.53
    }
}
