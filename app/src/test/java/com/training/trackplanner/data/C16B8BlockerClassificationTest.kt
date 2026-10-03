package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class C16B8BlockerClassificationTest {
    @Test
    fun exactCalibrationOnlyDeltaHasNoResidualBlocker() {
        assertEquals(C16PrimaryDisposition.EXPECTED_CALIBRATION_CUTOVER, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = false, residualMaterialDeltaCount = 0)
        ))
    }

    @Test
    fun calibrationPlusUnauthorizedOwnerRemainsSafetyBlocked() {
        assertEquals(C16PrimaryDisposition.TRUE_SAFETY_BLOCK, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 1, unauthorizedOwnerDelta = true)
        ))
    }

    @Test
    fun validNonStrengthTargetIsCurrentScopeLimitation() {
        assertEquals(C16PrimaryDisposition.CURRENT_SCOPE_LIMITATION, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 1, validNonStrengthCanonicalDelta = true)
        ))
    }

    @Test
    fun missingProvenanceIsNotMisclassifiedAsProgramDefectOrFalseNegative() {
        assertEquals(C16PrimaryDisposition.AUDIT_OR_PROVENANCE_GAP, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 0, provenanceClosed = false)
        ))
    }

    @Test
    fun b8RejectionWithNoResidualAndAllIndependentProofsIsPotentialFalseNegative() {
        assertEquals(C16PrimaryDisposition.POTENTIAL_B8_FALSE_NEGATIVE, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 0)
        ))
    }

    @Test
    fun falseNegativeClassificationFailsClosedForEachMissingPredicate() {
        val base = C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 0)
        val failures = listOf(
            base.copy(unauthorizedOwnerDelta = true),
            base.copy(validNonStrengthCanonicalDelta = true),
            base.copy(provenanceClosed = false),
            base.copy(targetAccepted = false),
            base.copy(collateralRegressionFree = false),
            base.copy(scopeSupported = false),
            base.copy(numericB4Authority = false),
            base.copy(exactB5Owner = false),
            base.copy(exactColdStartB6Authority = false),
            base.copy(fullMaterialization = false),
            base.copy(scheduleAllowed = false),
            base.copy(upstreamIdentitiesConsistent = false),
            base.copy(canonicalProgramDefect = true)
        )
        assertEquals(
            listOf(
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.CURRENT_SCOPE_LIMITATION,
                C16PrimaryDisposition.AUDIT_OR_PROVENANCE_GAP,
                C16PrimaryDisposition.TRUE_PROGRAM_DEFECT,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.CURRENT_SCOPE_LIMITATION,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_SAFETY_BLOCK,
                C16PrimaryDisposition.TRUE_PROGRAM_DEFECT
            ),
            failures.map(::classifyC16Primary)
        )
    }

    @Test
    fun comparisonOnlyDifferenceIsNotCalledCanonicalProgramDefect() {
        assertEquals(C16PrimaryDisposition.COMPARISON_ARTIFACT, classifyC16Primary(
            C16BlockerEvidence(b8Rejected = true, residualMaterialDeltaCount = 0, onlyComparisonArtifactRemains = true)
        ))
    }
}
