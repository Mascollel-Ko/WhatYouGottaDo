package com.training.trackplanner.data.personalized

/**
 * The canonical B1-B4 analysis result.  It is computed from the prepared
 * history/state inputs and does not contain, identify, or require a CONTROL
 * program.  CONTROL may mirror the fields for persistence and diagnostics,
 * but this value remains the source passed to canonical B5/B6 consumers.
 */
data class CanonicalStimulusPlanningResult(
    val athleteStimulusNeedProfile: AthleteStimulusNeedProfile,
    val qualityDoseHistory: LedgerBackedQualityDoseHistory,
    val decisionPortfolio: StimulusTrainingDecisionPortfolio,
    val targetPlan: StimulusTargetPlan,
    /** Read-only B4 comparison diagnostics derived after CONTROL materializes. */
    val controlProgramAudit: StimulusTargetControlProgramAudit? = null
) {
    fun withControlProgramAudit(audit: StimulusTargetControlProgramAudit): CanonicalStimulusPlanningResult =
        copy(controlProgramAudit = audit)

    /** Compatibility-only mirror for the existing persisted personalizedDecision shape. */
    fun targetPlanCompatibilityMirror(): StimulusTargetPlan = targetPlan.copy(controlProgramAudit = controlProgramAudit)
}

/** One CONTROL skeleton paired with the independent canonical planning result. */
internal data class CanonicalPreparedProgram(
    val program: com.training.trackplanner.data.GeneratedProgramSkeleton,
    val canonicalPlanning: CanonicalStimulusPlanningResult
)
