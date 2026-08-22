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
        val relaxationTrace: StrictRelaxationTrace,
        val adjustmentTrace: AnalysisAdjustmentTrace = AnalysisAdjustmentTrace()
    ) : StrictLabPlanningOutcome

    data class Failure(
        val originalRequest: StrictLabAnalysisRequest,
        val effectiveRequest: StrictLabAnalysisRequest,
        val bundle: StrictPhaseAInputBundle,
        val planned: StrictBvarPlanningResult.Failure,
        val relaxationTrace: StrictRelaxationTrace,
        val adjustmentTrace: AnalysisAdjustmentTrace = AnalysisAdjustmentTrace()
    ) : StrictLabPlanningOutcome
}

/** Uses only semantic policy and prefit availability, then reruns the canonical Phase A path. */
internal object StrictLabRelaxedPlanningPolicy {
    fun planAutomatically(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest
    ): StrictLabPlanningOutcome {
        val strict = plan(snapshot, request, StrictLabAnalysisMode.STRICT)
        if (strict is StrictLabPlanningOutcome.Success) {
            return strict.copy(adjustmentTrace = planningAdjustments(strict, strict.bundle.fingerprint))
        }
        val strictFingerprint = (strict as StrictLabPlanningOutcome.Failure).bundle.fingerprint
        return when (val adjusted = plan(snapshot, request, StrictLabAnalysisMode.RELAXED)) {
            is StrictLabPlanningOutcome.Success -> adjusted.copy(
                adjustmentTrace = planningAdjustments(adjusted, strictFingerprint)
            )
            is StrictLabPlanningOutcome.Failure -> adjusted.copy(
                adjustmentTrace = planningAdjustments(adjusted, strictFingerprint)
            )
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
            if (planned is StrictBvarPlanningResult.Failure && original.controls.isNotEmpty() &&
                planned.code.isRemovableControlFailure()
            ) {
                attemptedRoutes += StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS
                val removalOrder = controlRemovalOrder(snapshot, original.controls)
                planningDetails += "full request exhausted canonical prefit planning before control reduction"
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
                .filter { decision ->
                    decision.decisionReason.startsWith("relaxed semantic representation:") ||
                        decision.decisionReason.startsWith("semantic short-history representation:")
                }
                .map { decision ->
                    val observed = if (decision.decisionReason.startsWith("relaxed semantic representation:")) {
                        "INCONCLUSIVE"
                    } else {
                        "DIAGNOSTIC_UNAVAILABLE_SHORT_HISTORY"
                    }
                    "${decision.metric.stableId}: $observed -> semantic ${decision.transformation.name}"
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
        WeeklySnapshotPhaseAAdapter.isSemanticallyUsable(snapshot, feature, week)
    }

    private fun planningAdjustments(
        outcome: StrictLabPlanningOutcome,
        beforeFingerprint: String
    ): AnalysisAdjustmentTrace {
        val events = mutableListOf<AnalysisAdjustmentEvent>()
        val trace = when (outcome) {
            is StrictLabPlanningOutcome.Success -> outcome.relaxationTrace
            is StrictLabPlanningOutcome.Failure -> outcome.relaxationTrace
        }
        val finalBundle = when (outcome) {
            is StrictLabPlanningOutcome.Success -> outcome.bundle
            is StrictLabPlanningOutcome.Failure -> outcome.bundle
        }
        trace.representationOverrides.forEach { override ->
            val shortHistory = "DIAGNOSTIC_UNAVAILABLE_SHORT_HISTORY" in override
            events += AnalysisAdjustmentEvent(
                sequence = events.size + 1,
                type = AnalysisAdjustmentType.REPRESENTATION_SEMANTIC_FALLBACK,
                triggerCode = if (shortHistory) {
                    "SHORT_HISTORY_DIAGNOSTIC_UNAVAILABLE"
                } else {
                    "INCONCLUSIVE_REPRESENTATION"
                },
                affected = override.substringBefore(':'),
                observedCondition = override,
                action = "approved semantic representation applied",
                explanation = if (shortHistory) {
                    "The approved short-history semantic representation was used because ADF/KPSS evidence was unavailable."
                } else {
                    "The reviewed feature-family representation was used after an inconclusive diagnostic."
                },
                modelStructureChanged = true,
                samplingPolicyChanged = false,
                beforeFingerprint = beforeFingerprint,
                afterFingerprint = finalBundle.fingerprint
            )
        }
        trace.removedControls.forEach { control ->
            events += AnalysisAdjustmentEvent(
                sequence = events.size + 1,
                type = AnalysisAdjustmentType.REMOVE_CONTROL,
                triggerCode = "CONTROL_PREFIT_UNAVAILABLE",
                affected = control.value,
                observedCondition = "The selected control prevented a feasible canonical Phase A model.",
                action = "control removed and canonical Phase A rebuilt",
                beforeValue = "included",
                afterValue = "removed",
                explanation = "Controls are removed one at a time using deterministic prefit usability only.",
                modelStructureChanged = true,
                samplingPolicyChanged = false,
                beforeFingerprint = beforeFingerprint,
                afterFingerprint = finalBundle.fingerprint
            )
        }
        if (outcome is StrictLabPlanningOutcome.Success) {
            outcome.planned.removedOptionalCandidates.forEach { candidate ->
                events += AnalysisAdjustmentEvent(
                    sequence = events.size + 1,
                    type = AnalysisAdjustmentType.OPTIONAL_CANDIDATE_REDUCED,
                    triggerCode = "OPTIONAL_CANDIDATE_PREFIT_DEGRADATION",
                    affected = candidate.stableId,
                    observedCondition = "The optional candidate reduced the feasible common-row domain.",
                    action = "optional candidate removed before sampling",
                    explanation = "The canonical optional-candidate degradation order was preserved.",
                    modelStructureChanged = true,
                    samplingPolicyChanged = false,
                    beforeFingerprint = beforeFingerprint,
                    afterFingerprint = outcome.planned.input.fingerprint
                )
            }
            val comparison = outcome.planned.input.comparisonPlan
            if (comparison.pmax < comparison.requestedPmax) {
                events += AnalysisAdjustmentEvent(
                    sequence = events.size + 1,
                    type = AnalysisAdjustmentType.PMAX_DEGRADED,
                    triggerCode = "COMMON_ROW_DOMAIN",
                    affected = "Pmax",
                    observedCondition = "The requested lag maximum did not preserve the minimum common-row domain.",
                    action = "Pmax deterministically reduced",
                    beforeValue = comparison.requestedPmax.toString(),
                    afterValue = comparison.pmax.toString(),
                    explanation = "Pmax was reduced no lower than the canonical minimum while minimumCommonRows remained 3.",
                    modelStructureChanged = true,
                    samplingPolicyChanged = false,
                    beforeFingerprint = beforeFingerprint,
                    afterFingerprint = outcome.planned.input.fingerprint
                )
            }
        }
        return AnalysisAdjustmentTrace(events)
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

private fun StrictBvarPlanningFailureCode.isRemovableControlFailure(): Boolean = this in setOf(
    StrictBvarPlanningFailureCode.PREPARATION_FAILED,
    StrictBvarPlanningFailureCode.FOCAL_FEATURE_UNAVAILABLE,
    StrictBvarPlanningFailureCode.NO_FOCAL_VARIATION,
    StrictBvarPlanningFailureCode.NO_TARGET_VARIATION,
    StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN,
    StrictBvarPlanningFailureCode.METADATA_INCOMPLETE,
    StrictBvarPlanningFailureCode.REPRESENTATION_POLICY_UNAVAILABLE,
    StrictBvarPlanningFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT,
    StrictBvarPlanningFailureCode.SCALING_UNAVAILABLE,
    StrictBvarPlanningFailureCode.SOURCE_IDENTITY_UNAVAILABLE
)

internal const val RELAXED_CONTROL_REDUCTION_POLICY_VERSION = "relaxed-control-reduction-prefit-availability-v1"
