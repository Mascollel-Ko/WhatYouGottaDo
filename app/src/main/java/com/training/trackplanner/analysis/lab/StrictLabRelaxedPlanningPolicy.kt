package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningResult
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarV07PlanningAuthority
import com.training.trackplanner.analysis.lab.pipeline.StrictFeatureSelection
import com.training.trackplanner.analysis.lab.pipeline.StrictPhaseAInputBundle
import com.training.trackplanner.analysis.lab.pipeline.WeeklySnapshotPhaseAAdapter
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState

internal sealed interface StrictLabPlanningOutcome {
    data class Success(
        val originalRequest: StrictLabAnalysisRequest,
        val effectiveRequest: StrictLabAnalysisRequest,
        val bundle: StrictPhaseAInputBundle,
        val planned: StrictBvarPlanningResult.Success,
        val relaxationTrace: StrictRelaxationTrace
    ) : StrictLabPlanningOutcome

    data class Failure(
        val originalRequest: StrictLabAnalysisRequest,
        val effectiveRequest: StrictLabAnalysisRequest,
        val bundle: StrictPhaseAInputBundle,
        val planned: StrictBvarPlanningResult.Failure,
        val relaxationTrace: StrictRelaxationTrace
    ) : StrictLabPlanningOutcome
}

/** Uses only semantic policy and prefit availability, then reruns the canonical Phase A path. */
internal object StrictLabRelaxedPlanningPolicy {
    fun availableRoutes(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest,
        failureCode: StrictLabFailureCode,
        analysisMode: StrictLabAnalysisMode,
        diagnostics: List<String> = emptyList()
    ): Set<StrictRelaxationRoute> {
        if (analysisMode == StrictLabAnalysisMode.RELAXED) return emptySet()
        return when (failureCode) {
            StrictLabFailureCode.MCMC_CONVERGENCE_FAILED,
            StrictLabFailureCode.LAG_POSTERIOR_MIXING_FAILED,
            StrictLabFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED ->
                setOf(StrictRelaxationRoute.RELAX_SAMPLING_RELIABILITY)
            StrictLabFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN ->
                setOf(StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS).takeIf {
                    request.controls.isNotEmpty()
                }.orEmpty()
            StrictLabFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT -> {
                val hasInconclusive = diagnostics.any { it.contains("INCONCLUSIVE") }
                val hasSemanticRoute = (listOf(request.xFeature) + request.yFeatures + request.controls).any { feature ->
                    snapshot.descriptors[feature]?.family?.let(WeeklySnapshotPhaseAAdapter::semanticTransformation) != null
                }
                setOf(StrictRelaxationRoute.RELAXED_REPRESENTATION).takeIf {
                    hasInconclusive && hasSemanticRoute
                }.orEmpty()
            }
            else -> emptySet()
        }
    }

    fun plan(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest,
        analysisMode: StrictLabAnalysisMode
    ): StrictLabPlanningOutcome {
        val original = request.normalized()
        var effective = original
        var bundle = adapt(snapshot, effective, analysisMode, emptySet())
        var planned = StrictBvarV07PlanningAuthority.plan(bundle)
        val attemptedRoutes = linkedSetOf<StrictRelaxationRoute>()
        val appliedRoutes = linkedSetOf<StrictRelaxationRoute>()
        val planningDetails = mutableListOf<String>()

        if (analysisMode == StrictLabAnalysisMode.RELAXED) {
            if (planned is StrictBvarPlanningResult.Failure &&
                planned.code == StrictBvarPlanningFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT
            ) {
                attemptedRoutes += StrictRelaxationRoute.RELAXED_REPRESENTATION
            }
            if (planned is StrictBvarPlanningResult.Failure &&
                planned.code == StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN &&
                original.controls.isNotEmpty()
            ) {
                attemptedRoutes += StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS
                val removalOrder = controlRemovalOrder(snapshot, original.controls)
                planningDetails += "full request exhausted canonical optional-candidate and Pmax degradation"
                planningDetails += "controlReductionPolicy=$RELAXED_CONTROL_REDUCTION_POLICY_VERSION"
                for (index in removalOrder.indices) {
                    val removed = removalOrder.take(index + 1).toSet()
                    effective = original.copy(controls = original.controls.filterNot { it in removed }).normalized()
                    planningDetails += "control reduction attempt ${index + 1}: removed=${removed.joinToString(",") { it.value }}"
                    bundle = adapt(snapshot, effective, analysisMode, removed)
                    planned = StrictBvarV07PlanningAuthority.plan(bundle)
                    if (planned is StrictBvarPlanningResult.Success) break
                }
                if (planned is StrictBvarPlanningResult.Success) {
                    appliedRoutes += StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS
                }
            }
        }

        val representationOverrides = if (planned is StrictBvarPlanningResult.Success) {
            planned.context.canonicalTransformationPlan.decisionsByMetric.values
                .filter { it.decisionReason.startsWith("relaxed semantic representation:") }
                .map { decision ->
                    "${decision.metric.stableId}: INCONCLUSIVE -> semantic ${decision.transformation.name}"
                }
                .sorted()
        } else {
            emptyList()
        }
        if (representationOverrides.isNotEmpty()) {
            attemptedRoutes += StrictRelaxationRoute.RELAXED_REPRESENTATION
            appliedRoutes += StrictRelaxationRoute.RELAXED_REPRESENTATION
        }
        val trace = StrictRelaxationTrace(
            originalControls = original.controls,
            effectiveControls = effective.controls,
            attemptedRoutes = attemptedRoutes,
            appliedRoutes = appliedRoutes,
            representationOverrides = representationOverrides,
            planningDetails = planningDetails
        )
        return when (planned) {
            is StrictBvarPlanningResult.Success -> StrictLabPlanningOutcome.Success(original, effective, bundle, planned, trace)
            is StrictBvarPlanningResult.Failure -> StrictLabPlanningOutcome.Failure(original, effective, bundle, planned, trace)
        }
    }

    fun controlRemovalOrder(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        controls: List<com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey>
    ): List<com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey> = controls.distinct().sortedWith(
        compareBy<com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey> { feature ->
            usableClosedWeeks(snapshot, feature)
        }.thenByDescending { feature ->
            snapshot.closedWeeks.size - usableClosedWeeks(snapshot, feature)
        }.thenBy { it.value }
    )

    private fun usableClosedWeeks(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        feature: com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
    ): Int = snapshot.closedWeeks.count { week ->
        val cell = snapshot.cell(feature, week)
        cell != null && cell.state in setOf(WeeklyCellState.OBSERVED, WeeklyCellState.STRUCTURAL_ZERO) &&
            cell.value?.isFinite() == true
    }

    private fun adapt(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest,
        analysisMode: StrictLabAnalysisMode,
        excludedAutomaticFeatures: Set<com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey>
    ): StrictPhaseAInputBundle = WeeklySnapshotPhaseAAdapter.adapt(
        snapshot,
        StrictFeatureSelection(
            request.xFeature,
            request.yFeatures,
            request.controls,
            request.requestedHorizon
        ),
        analysisMode,
        excludedAutomaticFeatures
    )
}

internal const val RELAXED_CONTROL_REDUCTION_POLICY_VERSION = "relaxed-control-reduction-prefit-availability-v1"
