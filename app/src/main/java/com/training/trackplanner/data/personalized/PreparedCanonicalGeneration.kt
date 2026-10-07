package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton

/** Immutable inputs shared by the canonical EXPERIMENTAL pass and the late CONTROL pass. */
internal data class PreparedCanonicalGenerationContext(
    val snapshot: PlanningHistorySnapshot,
    val state: AthletePlanningState,
    val gaps: List<AdaptationGap>,
    val intent: BlockIntent,
    val resolvedRequest: ResolvedPreparedProgramRequest,
    val priorDecisionId: String?,
    val legacyNeeds: AthleteNeedsProfile,
    val legacyDoseHistory: QualityDoseHistory,
    val canonicalPlanningOutcome: CanonicalPlanningOutcome
)

/** B5/B6 artifact completed without a CONTROL program or any CONTROL diagnostics. */
internal data class CanonicalExperimentalGeneration(
    val program: GeneratedProgramSkeleton,
    val selectionPlan: StimulusCandidateSelectionPlan,
    val prescriptionContext: CanonicalPrescriptionContext,
    val authorizationPlan: StimulusPrescriptionAuthorizationPlan,
    val experimentalAudit: StimulusTargetControlProgramAudit,
    val prescriptionRealizationPlan: StimulusPrescriptionRealizationPlan,
    val materializationAudits: List<StimulusPrescriptionMaterializationAudit>
)

internal enum class ProductionGenerationPhase {
    CANONICAL_PREPARED,
    B5_COMPLETE,
    B6_PRE_AUTHORITY_COMPLETE,
    EXPERIMENTAL_BUILD,
    B6_POST_MATERIALIZATION_COMPLETE,
    CONTROL_BUILD,
    CONTROL_AUDIT,
    COMPARISON,
    B7,
    B8,
    B9
}

/** Internal diagnostic seam for behavioral ordering and shared-context tests. */
internal data class ProductionGenerationObservation(
    val phase: ProductionGenerationPhase,
    val context: PreparedCanonicalGenerationContext,
    val selectionPlan: StimulusCandidateSelectionPlan? = null,
    val authorizationPlan: StimulusPrescriptionAuthorizationPlan? = null
)
