package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.floor

internal class EndogenousVariableSelector(
    private val alignmentService: TimeSeriesAlignmentService = TimeSeriesAlignmentService(),
    private val integrationOrderAnalyzer: IntegrationOrderAnalyzer = IntegrationOrderAnalyzer(),
    private val localProjectionEstimator: BayesianLocalProjectionEstimator = BayesianLocalProjectionEstimator(),
    private val choleskyShockIdentifier: CholeskyShockIdentifier = CholeskyShockIdentifier()
) {
    fun select(
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        requestedHorizon: Int,
        baseAlignment: TimeSeriesAlignment,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): AutomaticEndogenousSelection {
        val mandatory = (listOf(xMetric) + yMetrics).distinct()
        val maxEndogenous = maximumEndogenousCount(baseAlignment.weeks.size, controls.size, requestedHorizon)
        val diagnostics = mutableListOf<String>()
        if (mandatory.size > maxEndogenous) {
            diagnostics += "Automatic endogenous selection was skipped because the required X/Y system exceeds the sample-based K cap ($maxEndogenous)."
            return AutomaticEndogenousSelection(emptyList(), diagnostics)
        }
        val candidates = AnalysisMetricRegistry.descriptors
            .asSequence()
            .filter { descriptor -> descriptor.supportsMultivariate && descriptor.id !in mandatory && descriptor.id !in controls }
            .filter { descriptor -> descriptor.category != AnalysisMetricCategory.DERIVED }
            .mapNotNull { descriptor ->
                val reason = alignmentService.usableCandidate(descriptor.id, baseAlignment.weeks.toSet(), metricSeries)
                if (reason != null) {
                    diagnostics += "${descriptor.displayName} excluded: $reason."
                    null
                } else descriptor.id
            }
            .toMutableList()
        val selected = mutableListOf<TrendMetricId>()
        while (mandatory.size + selected.size < maxEndogenous && candidates.isNotEmpty()) {
            val currentSystem = mandatory + selected
            val ranked = candidates.mapNotNull { candidate ->
                candidateRollingPredictiveGain(candidate, xMetric, yMetrics, controls, currentSystem, metricSeries)?.let { candidate to it }
            }
            val best = ranked.maxByOrNull { it.second }
            if (best == null || best.second < MIN_LOG_PREDICTIVE_GAIN) {
                diagnostics += "No remaining automatic endogenous candidate improved rolling-origin log predictive density."
                break
            }
            val expandedSystem = currentSystem + best.first
            val alignment = alignmentService.align(expandedSystem + controls, metricSeries)
            val fit = alignment?.let { BayesianVarEstimator().fitSystem(it, expandedSystem, controls, lag = 1, includeErrorCorrection = false) }
            if (fit == null || strictCholeskyFactorOrNull(fit.residualCovariance) == null || !choleskyShockIdentifier.posteriorPredictivePass(fit)) {
                diagnostics += "${displayName(best.first)} excluded: the expanded Bayesian dynamic system failed stability or posterior predictive coverage checks."
                candidates.remove(best.first)
                continue
            }
            selected += best.first
            candidates.remove(best.first)
            diagnostics += "${displayName(best.first)} included: rolling-origin log predictive density improved by ${"%.2f".format(best.second)}."
        }
        return AutomaticEndogenousSelection(selected, diagnostics)
    }

    private fun candidateRollingPredictiveGain(
        candidate: TrendMetricId,
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        currentSystem: List<TrendMetricId>,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): Double? {
        val expanded = alignmentService.align((currentSystem + candidate) + controls, series) ?: return null
        val base = alignmentService.align(currentSystem + controls, series)?.let { alignmentService.restrictToWeeks(it, expanded.weeks) } ?: return null
        val candidateDiagnostic = integrationOrderAnalyzer.diagnose(candidate, expanded.valuesByMetric[candidate].orEmpty())
        if (candidateDiagnostic.levelOrder == IntegrationOrder.I2_OR_HIGHER) return null
        val baseScore = yMetrics.mapNotNull { yMetric ->
            localProjectionEstimator.rollingPredictiveScore(base, xMetric, yMetric, currentSystem, controls)
        }
        val expandedScore = yMetrics.mapNotNull { yMetric ->
            localProjectionEstimator.rollingPredictiveScore(expanded, xMetric, yMetric, currentSystem + candidate, controls)
        }
        if (baseScore.size != yMetrics.size || expandedScore.size != yMetrics.size) return null
        return expandedScore.sumOf { it.logPredictiveDensity } - baseScore.sumOf { it.logPredictiveDensity }
    }

    private fun maximumEndogenousCount(observations: Int, controlCount: Int, horizon: Int): Int {
        val sampleCap = floor(((observations - BASE_LAG - horizon).toDouble() / 4.0 - 1.0 - controlCount - DETERMINISTIC_TERMS) / BASE_LAG)
            .toInt()
        return sampleCap.coerceAtLeast(0).coerceAtMost(PRODUCT_K_MAX)
    }

    private fun displayName(metric: TrendMetricId): String = AnalysisMetricRegistry.descriptor(metric)?.displayName ?: metric.name

    private companion object {
        const val BASE_LAG = 1
        const val DETERMINISTIC_TERMS = 1
        const val PRODUCT_K_MAX = 8
        const val MIN_LOG_PREDICTIVE_GAIN = 0.05
    }
}
