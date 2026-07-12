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
        val rawLevels = metrics.map { metric -> alignment.valuesByMetric[metric] ?: return CointegrationDiagnostic(null, 0.0, null, false, "missing endogenous series") }
        val validRows = (1 until alignment.weeks.size).filter { time ->
            metrics.all { metric -> alignment.exactDifference(metric, time) != null && alignment.exactLag(metric, time, 1) != null }
        }
        val levels = rawLevels.mapIndexed { metricIndex, values -> validRows.map { time -> values[time - 1] } }
        if (levels.any { it.size < 24 }) return CointegrationDiagnostic(null, 0.0, null, false, "insufficient observations for Johansen trace")
        val differences = metrics.map { metric -> validRows.map { time -> alignment.exactDifference(metric, time)!! } }
        val laggedLevels = levels
        val s00 = covariance(differences)
        val s11 = covariance(laggedLevels)
        val s01 = crossCovariance(differences, laggedLevels)
        val s10 = transpose(s01)
        val johansen = runCatching { StableLinearAlgebra.johansenFormEigen(s00, s01, s10, s11) }.getOrNull()
            ?: return CointegrationDiagnostic(null, 0.0, null, false, "generalized eigen primitive failed")
        val eigenResult = johansen.eigenResult
        val eigen = eigenResult.eigenvalues.firstOrNull()?.coerceIn(0.0, 0.999999)
            ?: return CointegrationDiagnostic(null, 0.0, null, false, "generalized eigen primitive returned no valid roots")
        val trace = -laggedLevels.first().size * ln(1.0 - eigen)
        val heuristicProbability = 1.0 / (1.0 + exp(-(trace - TRACE_RANK_ONE_THRESHOLD) / 3.0))
        val supported = trace >= TRACE_RANK_ONE_THRESHOLD && heuristicProbability >= 0.80
        return CointegrationDiagnostic(
            rank = if (supported) 1 else 0,
            legacyHeuristicScore = heuristicProbability,
            johansenTraceStatistic = trace,
            isSupported = false,
            message = if (supported) {
                "Legacy heuristic cointegration screen suggests rank-1 structure, but VECM routing is disabled until full Johansen and Bayesian rank posterior are implemented"
            } else {
                "legacy heuristic cointegration evidence is insufficient or conflicted"
            },
            cointegrationVector = null,
            diagnostics = johansen.diagnostics + "legacy heuristic score; diagnostic only; not a Bayesian rank posterior"
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

    private fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> =
        Array(matrix[0].size) { row -> DoubleArray(matrix.size) { column -> matrix[column][row] } }

    private companion object {
        const val TRACE_RANK_ONE_THRESHOLD = 12.53
    }
}
