package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/** Exact owner scope and current prescriptions consumed by the post-build B6 realization pass. */
internal data class StimulusRealizationPrescriptionInputs(
    val ownerKeys: Set<StimulusPrescriptionOwnerIdentity>,
    val currentPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
)

/**
 * Recreates B6's legacy experimental-first `associateBy()` view using only the B5 seed, C5
 * incumbent baseline, and EXPERIMENTAL materialized rows. The incumbent map is merged last so
 * an existing owner continues to override its EXPERIMENTAL row exactly as before.
 */
internal fun buildStimulusRealizationPrescriptionInputs(
    selectionPlan: StimulusCandidateSelectionPlan,
    incumbentSeed: StimulusIncumbentIdentitySeed,
    prescriptionBaseline: StimulusIncumbentPrescriptionBaseline,
    experimentalItems: Iterable<ProgramSkeletonItem>
): StimulusRealizationPrescriptionInputs {
    val ownerKeys = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
    selectionPlan.selectedCandidates.forEach { candidate ->
        ownerKeys += StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
    }

    val seededOwners = incumbentSeed.owners.associateBy { owner ->
        StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
    }
    val directStableKeys = selectionPlan.traces.flatMap { it.controlDirectCapabilityIdentities }
    for (stableKey in directStableKeys) {
        // The seed is the identity source. Walk baseline key order within each stable key to
        // retain the legacy control.items filter order even though the seed itself is sorted.
        for (baselineOwner in prescriptionBaseline.prescriptions.keys) {
            if (baselineOwner.stableKey != stableKey) continue
            val seededOwner = seededOwners[baselineOwner] ?: continue
            ownerKeys += StimulusPrescriptionOwnerIdentity(seededOwner.stableKey, seededOwner.selectionRole)
        }
    }

    val experimentalPrescriptions = experimentalItems.asSequence()
        .map { item ->
            StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) to
                PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
        }
        .filter { it.first in ownerKeys }
        .toList()
        .associateBy({ it.first }, { it.second })

    val currentPrescriptions = linkedMapOf<StimulusPrescriptionOwnerIdentity, PlannedPrescription>().apply {
        putAll(experimentalPrescriptions)
        prescriptionBaseline.prescriptions.forEach { (owner, prescription) ->
            if (owner in ownerKeys) put(owner, prescription)
        }
    }
    return StimulusRealizationPrescriptionInputs(ownerKeys, currentPrescriptions)
}
