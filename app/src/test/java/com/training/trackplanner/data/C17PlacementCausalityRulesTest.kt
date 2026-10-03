package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class C17PlacementCausalityRulesTest {
    private fun exactNecessaryProof() = C17DisplacementProof(
        exactOwnerAndFromToEdge = true,
        exactTriggerB4B5B6Authority = true,
        triggerMaterialized = true,
        explicitHardConstraint = true,
        constraintResolvedAtDestination = true,
        destinationValid = true,
        targetFrequencyAndPrescriptionPreserved = true,
        noLowerChangeValidPlacement = true,
        triggerIsAuthorized = true
    )

    @Test fun `fits without displacement classifies old movement as drift`() {
        assertEquals(C17PlacementClassification.UNNECESSARY_PLACEMENT_DRIFT,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = true, proof = null))
    }

    @Test fun `exact authorized hard conflict can prove necessary displacement`() {
        assertEquals(C17PlacementClassification.NECESSARY_AUTHORIZED_DISPLACEMENT,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = false, proof = exactNecessaryProof()))
    }

    @Test fun `missing exact edge stays unproven even when a hard constraint is observed`() {
        val proof = exactNecessaryProof().copy(exactOwnerAndFromToEdge = false)
        assertEquals(C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = false, proof = proof))
    }

    @Test fun `missing exact B6 authority cannot authorize displacement`() {
        val proof = exactNecessaryProof().copy(exactTriggerB4B5B6Authority = false)
        assertEquals(C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = false, proof = proof))
    }

    @Test fun `rebalancer score improvement does not override a valid old placement`() {
        // Stage/cause is not a displacement proof: optional objective improvement is insufficient.
        assertEquals(C17PlacementClassification.UNNECESSARY_PLACEMENT_DRIFT,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = true, proof = null))
    }

    @Test fun `unsupported Power trigger cannot grant placement authority`() {
        val proof = exactNecessaryProof().copy(triggerIsAuthorized = false)
        assertEquals(C17PlacementClassification.DISPLACEMENT_CAUSED_BY_UNAUTHORIZED_TRIGGER,
            C17PlacementCausalityRules.classify(previousPlacementCounterfactualValid = false, proof = proof))
    }

    @Test fun `destination and lower change requirements are fail closed`() {
        assertEquals(C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT,
            C17PlacementCausalityRules.classify(false, exactNecessaryProof().copy(destinationValid = false)))
        assertEquals(C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT,
            C17PlacementCausalityRules.classify(false, exactNecessaryProof().copy(noLowerChangeValidPlacement = false)))
    }
}
