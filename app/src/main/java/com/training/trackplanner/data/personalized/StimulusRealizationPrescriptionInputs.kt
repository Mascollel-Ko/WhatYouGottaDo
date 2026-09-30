package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/** B6's current prescription evidence derived only from selected B5 owners and actual history. */
internal data class CanonicalPrescriptionContext(
    val prescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>,
    val historyBackedOwners: Set<StimulusPrescriptionOwnerIdentity>
) {
    companion object {
        val EMPTY = CanonicalPrescriptionContext(emptyMap(), emptySet())
    }
}

/** Exact owner scope and the already materialized canonical prescriptions used by B6 realization. */
internal data class StimulusRealizationPrescriptionInputs(
    val ownerKeys: Set<StimulusPrescriptionOwnerIdentity>,
    val currentPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
)

/**
 * Builds B6 inputs from B5-selected owners and the same history-aware planner used by the
 * builder. The CONTROL program and its projected seed/baseline never enter this boundary.
 */
internal fun buildCanonicalPrescriptionContext(
    targetPlan: StimulusTargetPlan,
    selectionPlan: StimulusCandidateSelectionPlan,
    snapshot: PlanningHistorySnapshot,
    strengthIntent: StrengthIntent,
    prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
): CanonicalPrescriptionContext {
    val qualityOwnerKeys = targetPlan.qualityTargets.flatMapTo(linkedSetOf()) { target ->
        val targetId = "QUALITY:${target.quality.name}"
        selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }.map {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
    }
    val prescriptions = linkedMapOf<StimulusPrescriptionOwnerIdentity, PlannedPrescription>()
    val historyBacked = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
    selectionPlan.selectedCandidates.forEach { candidate ->
        val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
        if (identity !in qualityOwnerKeys) return@forEach
        val plannedItem = selectionPlan.materialDemand.candidates.firstOrNull { it.stableKey == candidate.stableKey }
            ?: PlannedExercise(candidate.stableKey, candidate.selectionRole, "B5 canonical owner", 0)
        val canonicalItem = plannedItem.copy(role = candidate.selectionRole)
        prescriptions[identity] = prescriptionPlanner.prescribe(
            snapshot, strengthIntent, canonicalItem, StrengthProgrammingStyle.NONE
        )
        if (snapshot.allConfirmedSets.any { it.stableKey == candidate.stableKey }) historyBacked += identity
    }
    return CanonicalPrescriptionContext(
        prescriptions = prescriptions,
        historyBackedOwners = historyBacked
    )
}

/**
 * Retains the exact B5 owner set and uses finalized EXPERIMENTAL rows as the realization view.
 * If an owner did not materialize, its canonical history/provisional planner row remains the
 * read-only fallback for diagnostics.
 */
internal fun buildStimulusRealizationPrescriptionInputs(
    selectionPlan: StimulusCandidateSelectionPlan,
    canonicalPrescriptionContext: CanonicalPrescriptionContext,
    experimentalItems: Iterable<ProgramSkeletonItem>
): StimulusRealizationPrescriptionInputs {
    val ownerKeys = selectionPlan.selectedCandidates.mapTo(linkedSetOf()) { candidate ->
        StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
    }
    val materialized = experimentalItems.asSequence().map { item ->
        StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) to
            PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
    }.filter { it.first in ownerKeys }.toList().toMap()
    val prescriptions = linkedMapOf<StimulusPrescriptionOwnerIdentity, PlannedPrescription>().apply {
        canonicalPrescriptionContext.prescriptions.filterKeys { it in ownerKeys }.forEach { (owner, prescription) ->
            put(owner, prescription)
        }
        putAll(materialized)
    }
    return StimulusRealizationPrescriptionInputs(ownerKeys, prescriptions)
}
