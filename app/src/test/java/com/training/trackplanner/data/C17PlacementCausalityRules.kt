package com.training.trackplanner.data

/** Audit-only classification. It cannot grant production placement authority. */
internal enum class C17PlacementClassification {
    NECESSARY_AUTHORIZED_DISPLACEMENT,
    UNNECESSARY_PLACEMENT_DRIFT,
    DISPLACEMENT_CAUSED_BY_UNAUTHORIZED_TRIGGER,
    UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT
}

internal data class C17DisplacementProof(
    val exactOwnerAndFromToEdge: Boolean,
    val exactTriggerB4B5B6Authority: Boolean,
    val triggerMaterialized: Boolean,
    val explicitHardConstraint: Boolean,
    val constraintResolvedAtDestination: Boolean,
    val destinationValid: Boolean,
    val targetFrequencyAndPrescriptionPreserved: Boolean,
    val noLowerChangeValidPlacement: Boolean,
    val triggerIsAuthorized: Boolean
)

internal object C17PlacementCausalityRules {
    fun classify(previousPlacementCounterfactualValid: Boolean, proof: C17DisplacementProof?): C17PlacementClassification {
        if (previousPlacementCounterfactualValid) {
            return if (proof == null) C17PlacementClassification.UNNECESSARY_PLACEMENT_DRIFT
            else C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT
        }
        if (proof == null) return C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT
        if (!proof.triggerIsAuthorized) return C17PlacementClassification.DISPLACEMENT_CAUSED_BY_UNAUTHORIZED_TRIGGER
        val necessary = proof.exactOwnerAndFromToEdge && proof.exactTriggerB4B5B6Authority &&
            proof.triggerMaterialized && proof.explicitHardConstraint && proof.constraintResolvedAtDestination &&
            proof.destinationValid && proof.targetFrequencyAndPrescriptionPreserved && proof.noLowerChangeValidPlacement
        return if (necessary) C17PlacementClassification.NECESSARY_AUTHORIZED_DISPLACEMENT
        else C17PlacementClassification.UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT
    }
}
