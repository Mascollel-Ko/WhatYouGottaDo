package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.floor

internal class EndogenousVariableSelector(
    private val alignmentService: TimeSeriesAlignmentService = TimeSeriesAlignmentService(),
    private val localProjectionEstimator: BayesianLocalProjectionEstimator = BayesianLocalProjectionEstimator(),
    private val choleskyShockIdentifier: CholeskyShockIdentifier = CholeskyShockIdentifier()
) {
    fun select(
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        requestedHorizon: Int,
        baseAlignment: TimeSeriesAlignment,
        preparedSeries: Map<TrendMetricId, PreparedMetricSeries>
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
                val reason = alignmentService.usablePreparedCandidate(descriptor.id, baseAlignment.weeks.toSet(), preparedSeries)
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
                candidateRollingPredictiveGain(candidate, xMetric, yMetrics, controls, currentSystem, preparedSeries, requestedHorizon.coerceAtLeast(1))?.let { candidate to it }
            }
            val best = ranked.maxByOrNull { it.second }
            if (best == null || best.second < MIN_LOG_PREDICTIVE_GAIN) {
                diagnostics += "No remaining automatic endogenous candidate improved rolling-origin log predictive density."
                break
            }
            val expandedSystem = currentSystem + best.first
            val preparedSystem = runCatching {
                PreparedTimeSeriesSystem.createValidated(
                    expandedSystem + controls,
                    preparedSeries,
                    lag = BASE_LAG,
                    horizon = requestedHorizon.coerceAtLeast(1),
                    rowRequirements = rowRequirements(xMetric, yMetrics, expandedSystem, controls, BASE_LAG, requestedHorizon.coerceAtLeast(1))
                )
            }.getOrNull()
            val commonSourceWeeks = preparedSystem?.commonUsableRows.orEmpty().map { it.sourceWeek }.toSet()
            val alignment = alignmentService.alignmentFromPrepared(expandedSystem + controls, preparedSeries)
            val fit = alignment?.let {
                BayesianVarEstimator().fitSystem(
                    it,
                    expandedSystem,
                    controls,
                    lag = 1,
                    includeErrorCorrection = false,
                    allowedSourceWeeks = commonSourceWeeks
                )
            }
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
        preparedSeries: Map<TrendMetricId, PreparedMetricSeries>,
        requestedHorizon: Int
    ): Double? {
        val expandedSystem = runCatching {
            PreparedTimeSeriesSystem.createValidated(
                (currentSystem + candidate) + controls,
                preparedSeries,
                lag = BASE_LAG,
                horizon = requestedHorizon,
                rowRequirements = rowRequirements(xMetric, yMetrics, currentSystem + candidate, controls, BASE_LAG, requestedHorizon)
            )
        }.getOrNull() ?: return null
        val commonSourceWeeks = expandedSystem.commonUsableRows.map { it.sourceWeek }.toSet()
        val expanded = alignmentService.alignmentFromPrepared((currentSystem + candidate) + controls, preparedSeries) ?: return null
        val base = alignmentService.alignmentFromPrepared(currentSystem + controls, preparedSeries)
            ?: return null
        val baseScore = yMetrics.mapNotNull { yMetric ->
            localProjectionEstimator.rollingPredictiveScore(base, xMetric, yMetric, currentSystem, controls, horizon = requestedHorizon, allowedSourceWeeks = commonSourceWeeks)
        }
        val expandedScore = yMetrics.mapNotNull { yMetric ->
            localProjectionEstimator.rollingPredictiveScore(expanded, xMetric, yMetric, currentSystem + candidate, controls, horizon = requestedHorizon, allowedSourceWeeks = commonSourceWeeks)
        }
        if (baseScore.size != yMetrics.size || expandedScore.size != yMetrics.size) return null
        return expandedScore.sumOf { it.logPredictiveDensity } - baseScore.sumOf { it.logPredictiveDensity }
    }

    private fun rowRequirements(
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        endogenous: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        horizon: Int
    ): List<VariableRowRequirement> {
        val lagOffsets = (1..lag).toSet()
        val rolesByMetric = linkedMapOf<TrendMetricId, MutableSet<TimeSeriesVariableRole>>()
        fun add(metric: TrendMetricId, vararg roles: TimeSeriesVariableRole) {
            rolesByMetric.getOrPut(metric) { mutableSetOf() }.addAll(roles)
        }
        endogenous.forEach { add(it, TimeSeriesVariableRole.ENDOGENOUS_STATE) }
        add(xMetric, TimeSeriesVariableRole.SHOCK_SOURCE)
        yMetrics.forEach { add(it, TimeSeriesVariableRole.RESPONSE) }
        controls.forEach { add(it, TimeSeriesVariableRole.CONTEMPORANEOUS_CONTROL) }
        return rolesByMetric.map { (metric, roles) ->
            VariableRowRequirement(
                metric = metric,
                roles = roles,
                requireSourceValue = true,
                requiredLagOffsets = if (TimeSeriesVariableRole.ENDOGENOUS_STATE in roles || TimeSeriesVariableRole.SHOCK_SOURCE in roles) lagOffsets else emptySet(),
                requiredTargetOffsets = if (TimeSeriesVariableRole.RESPONSE in roles) setOf(horizon) else emptySet(),
                requireShockEstimationRows = TimeSeriesVariableRole.SHOCK_SOURCE in roles
            )
        }
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
