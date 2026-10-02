package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.junit.Assert.assertEquals
import org.junit.Test

class C13CanonicalQualityGapCensusTest {
    @Test
    fun selectedOwnerWithoutExecutableB6IsClassifiedAtAuthorityBoundary() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            reasonCodes = setOf("B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"),
            numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
            strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            targetUnmet = true,
            finalUnitsStatus = StimulusTargetControlStatus.DIRECT_ABSENT
        ))
        assertEquals(C13TargetGapRootCause.TARGET_HAS_NO_EXECUTABLE_B6, result.rootCause)
        assertEquals(C13FirstShortfallStage.B6_AUTHORITY, result.firstShortfallStage)
        assertEquals(C13B6FailureCause.TARGET_DOSE_WITHOUT_PRESCRIPTION, result.b6FailureCause)
    }

    @Test
    fun missingNumericAuthorityPrecedesIncidentalProbeRepMismatch() {
        val result = classifyC13B6Failure(
            status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            realizationStatus = StimulusPrescriptionResolutionStatus.NO_PRESCRIPTION_CHANGE_AUTHORIZED,
            reasonCodes = setOf("HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE", "PLANNED_REPS_OUTSIDE_HYPERTROPHY_MODEL"),
            executionAuthority = StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT,
            hasPrescription = false,
            materializationState = StimulusPrescriptionMaterializationState.NOT_MATERIALIZED,
            numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
            strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS
        )
        assertEquals(C13B6FailureCause.TARGET_DOSE_WITHOUT_PRESCRIPTION, result)
    }

    @Test
    fun exactUnresolvedPosteriorReferenceIsNotCollapsedIntoGenericB6Failure() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            reasonCodes = setOf("CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"),
            numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
            strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            realizationStatus = StimulusPrescriptionResolutionStatus.NO_PRESCRIPTION_CHANGE_AUTHORIZED,
            targetUnmet = true
        ))
        assertEquals(C13B6FailureCause.NO_PERSONAL_REFERENCE, result.b6FailureCause)
    }

    @Test
    fun authorizedButPartialMaterializationStopsBeforeFinalAudit() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorizedPrescriptionExists = true,
            materializationState = StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED,
            targetUnmet = true
        ))
        assertEquals(C13TargetGapRootCause.AUTHORIZED_BUT_NOT_FULLY_MATERIALIZED, result.rootCause)
        assertEquals(C13FirstShortfallStage.EXACT_PRESCRIPTION_MATERIALIZATION, result.firstShortfallStage)
        assertEquals(C13B6FailureCause.MATERIALIZATION_FAILURE_AFTER_AUTHORIZATION, result.b6FailureCause)
    }

    @Test
    fun finalIncompatiblePrescriptionClassIsNotMisreportedAsCapacity() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorizedPrescriptionExists = true,
            materializationState = StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            finalIncompatibleDirectUnits = 3,
            targetUnmet = true
        ))
        assertEquals(C13TargetGapRootCause.PRESCRIPTION_CLASS_MISMATCH, result.rootCause)
        assertEquals(C13FirstShortfallStage.FINAL_TARGET_AUDIT, result.firstShortfallStage)
    }

    @Test
    fun onlyExactAcceptedCapacityTraceCanAttributeBuilderCapacity() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorizedPrescriptionExists = true,
            materializationState = StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            exactCapacityReduction = true,
            exactCapacityReductionStage = OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION,
            targetUnmet = true
        ))
        assertEquals(C13TargetGapRootCause.BUILDER_CAPACITY_CONSTRAINED, result.rootCause)
        assertEquals(C13FirstShortfallStage.FINITE_ALLOCATION, result.firstShortfallStage)
    }

    @Test
    fun matchingCanonicalFinalUnitsWithUnmetB7IsAnAccountingCandidate() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorizedPrescriptionExists = true,
            materializationState = StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            numericAuthority = StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
            numericFinalUnitsWithinTarget = true,
            numericFinalSessionsWithinTarget = true,
            targetUnmet = true
        ))
        assertEquals(C13TargetGapRootCause.TARGET_ACCOUNTING_MISMATCH, result.rootCause)
        assertEquals(C13FirstShortfallStage.FINAL_TARGET_AUDIT, result.firstShortfallStage)
    }

    @Test
    fun regressedTargetIsNotCollapsedIntoGenericUnmetOutcome() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorizedPrescriptionExists = true,
            materializationState = StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            outcomeStatus = StimulusExperimentalTargetOutcomeStatus.REGRESSED,
            targetUnmet = false
        ))
        assertEquals(C13TargetGapRootCause.TARGET_REGRESSED, result.rootCause)
        assertEquals(C13FirstShortfallStage.FINAL_TARGET_AUDIT, result.firstShortfallStage)
    }

    @Test
    fun modelUnavailableAndNoSelectionStayDistinct() {
        val modelUnavailable = classifyC13TargetGap(facts(
            realizationStatus = StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE,
            targetUnmet = true
        ))
        assertEquals(C13B6FailureCause.MODEL_UNAVAILABLE_TRUE_GAP, modelUnavailable.b6FailureCause)
        assertEquals(C13FirstShortfallStage.B6_REALIZATION, modelUnavailable.firstShortfallStage)

        val noIdentity = classifyC13TargetGap(facts(selectedOwnerExists = false, targetUnmet = true))
        assertEquals(C13TargetGapRootCause.B5_SELECTED_INSUFFICIENT_IDENTITY, noIdentity.rootCause)
        assertEquals(C13FirstShortfallStage.B5_SELECTION, noIdentity.firstShortfallStage)
    }

    @Test
    fun targetScopedModelUnavailableAuthorityIsRetainedWhenOwnerBindingIsAbsent() {
        val result = classifyC13TargetGap(facts(
            authorizationStatus = StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
            executionAuthority = StimulusPrescriptionExecutionAuthority.UNRESOLVED,
            reasonCodes = setOf("CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE"),
            targetUnmet = true
        ))
        assertEquals(C13TargetGapRootCause.TARGET_HAS_NO_EXECUTABLE_B6, result.rootCause)
        assertEquals(C13FirstShortfallStage.B6_REALIZATION, result.firstShortfallStage)
        assertEquals(C13B6FailureCause.MODEL_UNAVAILABLE_TRUE_GAP, result.b6FailureCause)
    }

    private fun facts(
        selectedOwnerExists: Boolean = true,
        realizationStatus: StimulusPrescriptionResolutionStatus? = null,
        reasonCodes: Set<String> = emptySet(),
        authorizationStatus: StimulusPrescriptionAuthorizationStatus? = null,
        executionAuthority: StimulusPrescriptionExecutionAuthority? = null,
        authorizedPrescriptionExists: Boolean = false,
        materializationState: StimulusPrescriptionMaterializationState? = null,
        numericAuthority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
        strategy: StimulusDoseStrategy = StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
        outcomeStatus: StimulusExperimentalTargetOutcomeStatus? = StimulusExperimentalTargetOutcomeStatus.UNCHANGED,
        targetUnmet: Boolean = false,
        finalUnitsStatus: StimulusTargetControlStatus? = StimulusTargetControlStatus.WITHIN_BAND,
        finalSessionsStatus: StimulusTargetControlStatus? = StimulusTargetControlStatus.WITHIN_BAND,
        finalIncompatibleDirectUnits: Int = 0,
        exactCapacityReduction: Boolean = false,
        exactCapacityReductionStage: OwnerAllocationStage? = null,
        numericFinalUnitsWithinTarget: Boolean? = null,
        numericFinalSessionsWithinTarget: Boolean? = null,
        ownerCompetesAcrossTargets: Boolean = false
    ) = C13TargetGapFacts(
        selectedOwnerExists = selectedOwnerExists,
        realizationStatus = realizationStatus,
        reasonCodes = reasonCodes,
        authorizationStatus = authorizationStatus,
        executionAuthority = executionAuthority,
        authorizedPrescriptionExists = authorizedPrescriptionExists,
        materializationState = materializationState,
        numericAuthority = numericAuthority,
        strategy = strategy,
        outcomeStatus = outcomeStatus,
        targetUnmet = targetUnmet,
        finalUnitsStatus = finalUnitsStatus,
        finalSessionsStatus = finalSessionsStatus,
        finalIncompatibleDirectUnits = finalIncompatibleDirectUnits,
        exactCapacityReduction = exactCapacityReduction,
        exactCapacityReductionStage = exactCapacityReductionStage,
        numericFinalUnitsWithinTarget = numericFinalUnitsWithinTarget,
        numericFinalSessionsWithinTarget = numericFinalSessionsWithinTarget,
        ownerCompetesAcrossTargets = ownerCompetesAcrossTargets
    )
}
