package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class C12ControlFallbackClassifierTest {
    @Test
    fun targetUnmetIsPrimaryAndProvenanceRemainsSecondary() {
        val result = C12ControlFallbackClassifier.classify(
            C12GateEvidence(targetOutcome = C12TargetOutcome.UNMET, provenanceClosed = false, b7Eligible = false)
        )
        assertEquals(C12FirstFailingGate.B7, result.firstFailingGate)
        assertEquals(C12PrimaryClassification.TARGET_NOT_SATISFIED, result.primaryClassification)
        assertEquals(setOf(C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE), result.secondaryClassifications)
        assertEquals(C12FallbackDisposition.TRUE_CONTROL_FALLBACK, result.disposition)
    }

    @Test
    fun exactB5IdentityDoesNotReplaceMissingB6Authority() {
        val result = C12ControlFallbackClassifier.classify(C12GateEvidence(b6Exact = false))
        assertEquals(C12FirstFailingGate.B6, result.firstFailingGate)
        assertEquals(C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE, result.primaryClassification)
    }

    @Test
    fun completeChainExceptProvenanceIsNotAFalseNegative() {
        val result = C12ControlFallbackClassifier.classify(
            C12GateEvidence(provenanceClosed = false, b7Eligible = false, b8Authorized = false)
        )
        assertEquals(C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE, result.primaryClassification)
        assertEquals(C12FallbackDisposition.TRUE_CONTROL_FALLBACK, result.disposition)
    }

    @Test
    fun unsupportedScopeAfterB7IsReportedAtScopeGate() {
        val result = C12ControlFallbackClassifier.classify(C12GateEvidence(scopeSupported = false, b8Authorized = false))
        assertEquals(C12FirstFailingGate.SCOPE, result.firstFailingGate)
        assertEquals(C12PrimaryClassification.SCOPE_UNSUPPORTED, result.primaryClassification)
    }

    @Test
    fun completeEvidenceRejectedOnlyByB7OrB8IsPotentialFalseNegative() {
        val b7 = C12ControlFallbackClassifier.classify(C12GateEvidence(b7Eligible = false, b8Authorized = false))
        assertEquals(C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE, b7.primaryClassification)
        assertEquals(C12FallbackDisposition.POTENTIAL_FALSE_NEGATIVE_GATE, b7.disposition)

        val b8 = C12ControlFallbackClassifier.classify(C12GateEvidence(b8Authorized = false))
        assertEquals(C12FirstFailingGate.B8, b8.firstFailingGate)
        assertEquals(C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE, b8.primaryClassification)
    }

    @Test
    fun exactB8AuthorityMaterializationAndProvenanceReasonsRemainSeparateBlockers() {
        val result = C12ControlFallbackClassifier.classify(C12GateEvidence(
            b8Authorized = false,
            b8Blockers = c12B8Blockers(listOf(
                "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY",
                "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED",
                "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION"
            ))
        ))
        assertEquals(C12FirstFailingGate.B8, result.firstFailingGate)
        assertEquals(C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE, result.primaryClassification)
        assertEquals(setOf(
            C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE,
            C12PrimaryClassification.MATERIALIZATION_INCOMPLETE
        ), result.secondaryClassifications)
        assertEquals(C12FallbackDisposition.TRUE_CONTROL_FALLBACK, result.disposition)
    }

    @Test
    fun unknownB8ReasonIsExplicitUpstreamBlockerAndCannotBecomeFalseNegative() {
        val result = C12ControlFallbackClassifier.classify(C12GateEvidence(
            b8Authorized = false,
            b8Blockers = c12B8Blockers(listOf("B8_FUTURE_UNMAPPED_REASON"))
        ))
        assertEquals(C12PrimaryClassification.UPSTREAM_INCONSISTENCY, result.primaryClassification)
        assertEquals(C12FallbackDisposition.TRUE_CONTROL_FALLBACK, result.disposition)
    }

    @Test
    fun primaryAndSecondaryOrderingIsStableForMultipleBlockers() {
        val evidence = C12GateEvidence(
            b4Consistent = false,
            b5Exact = false,
            b6Exact = false,
            materializationComplete = false,
            targetOutcome = C12TargetOutcome.REGRESSED,
            provenanceClosed = false,
            scopeSupported = false,
            placementScheduleAllowed = false
        )
        val result = C12ControlFallbackClassifier.classify(evidence)
        assertEquals(C12FirstFailingGate.B4, result.firstFailingGate)
        assertEquals(C12PrimaryClassification.TARGET_NOT_SATISFIED, result.primaryClassification)
        assertTrue(C12PrimaryClassification.UPSTREAM_INCONSISTENCY in result.secondaryClassifications)
        assertEquals(result, C12ControlFallbackClassifier.classify(evidence))
    }
}
