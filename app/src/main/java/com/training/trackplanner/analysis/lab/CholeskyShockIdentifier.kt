package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.abs

internal class CholeskyShockIdentifier {
    fun canonicalOrder(metrics: Collection<TrendMetricId>): List<TrendMetricId> = metrics.distinct().sortedWith(
        compareBy<TrendMetricId> { temporalGroup(it) }.thenBy { it.name }
    )

    fun structuralShockSeries(
        fit: SystemFit,
        system: List<TrendMetricId>,
        shockMetric: TrendMetricId
    ): Map<Int, Double>? {
        val impact = strictCholeskyFactorOrNull(fit.residualCovariance) ?: return null
        val shockIndex = system.indexOf(shockMetric).takeIf { it >= 0 } ?: return null
        if (fit.residuals.size != fit.timeIndices.size) return null
        return fit.timeIndices.indices.associate { index ->
            val structural = StableLinearAlgebra.applyLowerTriangular(
                impact,
                Array(fit.residuals[index].size) { row -> doubleArrayOf(fit.residuals[index][row]) }
            )
            fit.timeIndices[index] to structural[shockIndex][0]
        }
    }

    fun alternativeOrder(system: List<TrendMetricId>): List<TrendMetricId>? {
        val index = system.indices.firstOrNull { current ->
            current < system.lastIndex && temporalGroup(system[current]) == temporalGroup(system[current + 1])
        } ?: return null
        return system.toMutableList().also { reordered ->
            val swap = reordered[index]
            reordered[index] = reordered[index + 1]
            reordered[index + 1] = swap
        }
    }

    fun sensitivity(
        baseline: Map<TrendMetricId, List<BayesianIrfPoint>>,
        alternative: Map<TrendMetricId, List<BayesianIrfPoint>>,
        selectedResponses: List<TrendMetricId>
    ): CholeskySensitivityDiagnostic {
        val sensitive = selectedResponses.any { response ->
            val base = baseline[response].orEmpty()
            val alternate = alternative[response].orEmpty()
            val sameHorizon = base.zip(alternate).filter { (left, right) -> left.horizonWeeks == right.horizonWeeks }
            val signChanged = sameHorizon.any { (left, right) -> left.estimate * right.estimate < 0.0 }
            val basePeak = base.maxByOrNull { abs(it.estimate) }?.horizonWeeks
            val alternativePeak = alternate.maxByOrNull { abs(it.estimate) }?.horizonWeeks
            signChanged || (basePeak != null && alternativePeak != null && basePeak != alternativePeak)
        }
        return CholeskySensitivityDiagnostic(
            isOrderSensitive = sensitive,
            message = if (sensitive) {
                "Changing an adjacent canonical Cholesky order changes a response sign or peak horizon; interpret the structural response cautiously."
            } else {
                "The adjacent canonical-order sensitivity check did not change response signs or peak horizons."
            }
        )
    }

    fun posteriorPredictivePass(fit: SystemFit): Boolean =
        fit.posteriorPredictiveCoverage80.isFinite() && fit.posteriorPredictiveCoverage80 in MIN_COVERAGE..MAX_COVERAGE

    private fun temporalGroup(metric: TrendMetricId): Int = when (AnalysisMetricRegistry.descriptor(metric)?.category) {
        AnalysisMetricCategory.VOLUME,
        AnalysisMetricCategory.BADMINTON,
        AnalysisMetricCategory.TRANSFER -> 0
        AnalysisMetricCategory.FATIGUE,
        AnalysisMetricCategory.RECOVERY,
        AnalysisMetricCategory.MUSCLE_LOAD -> 1
        AnalysisMetricCategory.STRENGTH -> 2
        AnalysisMetricCategory.PERFORMANCE,
        AnalysisMetricCategory.SMASH_SPEED -> 3
        else -> 4
    }

    private companion object {
        const val MIN_COVERAGE = 0.35
        const val MAX_COVERAGE = 0.98
    }
}
