package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/** B6's current prescription evidence derived only from selected B5 owners and actual history. */
internal data class CanonicalPrescriptionContext(
    val prescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>,
    val historyBackedOwners: Set<StimulusPrescriptionOwnerIdentity>,
    val prescriptionsByQuality: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription> = emptyMap(),
    val historyBackedAuthorities: Set<StimulusPrescriptionAuthorityIdentity> = emptySet()
) {
    companion object {
        val EMPTY = CanonicalPrescriptionContext(emptyMap(), emptySet())
    }
}

/** Exact owner scope and the already materialized canonical prescriptions used by B6 realization. */
internal data class StimulusRealizationPrescriptionInputs(
    val ownerKeys: Set<StimulusPrescriptionOwnerIdentity>,
    val currentPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>,
    val currentPrescriptionsByQuality: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription> = emptyMap()
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
    prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner(),
    plannedPrescriptionResolver: StimulusPlannedPrescriptionResolver = StimulusPlannedPrescriptionResolver()
): CanonicalPrescriptionContext {
    val qualityPrescriptions = linkedMapOf<StimulusPrescriptionAuthorityIdentity, PlannedPrescription>()
    val historyBackedAuthorities = linkedSetOf<StimulusPrescriptionAuthorityIdentity>()
    val prescriptions = linkedMapOf<StimulusPrescriptionOwnerIdentity, PlannedPrescription>()
    val ownerHistoryBacked = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
    val ownerProjectionUsesHistory = mutableMapOf<StimulusPrescriptionOwnerIdentity, Boolean>()
    targetPlan.qualityTargets.forEach { target ->
        val targetId = "QUALITY:${target.quality.name}"
        selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }.forEach candidateLoop@{ candidate ->
            val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
            val authority = StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, target.quality)
            val plannedItem = selectionPlan.materialDemand.candidates.firstOrNull {
                it.stableKey == owner.stableKey && it.role == owner.selectionRole
            } ?: PlannedExercise(
                owner.stableKey, owner.selectionRole, "B5 canonical owner", 0,
                targetSets = candidate.targetSetsFromExistingPrescription.coerceAtLeast(0)
            )
            val canonicalItem = plannedItem.copy(role = owner.selectionRole)
            val style = when (target.quality) {
                com.training.trackplanner.data.TrainableQuality.STRENGTH -> StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS
                com.training.trackplanner.data.TrainableQuality.HYPERTROPHY -> StrengthProgrammingStyle.TOP_SET_HYPERTROPHY
                else -> StrengthProgrammingStyle.NONE
            }
            val canonical = prescriptionPlanner.prescribe(snapshot, strengthIntent, canonicalItem, style)
            val reviewedPersonal = targetCompatiblePersonalHistoryPrescription(
                quality = target.quality,
                item = canonicalItem,
                snapshot = snapshot,
                requestedSets = canonicalItem.targetSets,
                restSeconds = canonical.restSeconds,
                requireReviewedAuthority = true
            )
            val targetPrescription = when (target.quality) {
                com.training.trackplanner.data.TrainableQuality.STRENGTH -> {
                    val canonicalAuthority = snapshot.canonicalStrengthSignals[owner.stableKey]?.observationCount?.let { it >= 2 } == true
                    val compatible = plannedPrescriptionResolver.compatibility(target.quality, canonical, snapshot, owner.stableKey).status ==
                        PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT
                    when {
                        reviewedPersonal != null -> reviewedPersonal
                        canonicalAuthority && compatible -> canonical.copy(weightSource = "TARGET_COMPATIBLE_CANONICAL_STRENGTH_AUTHORITY")
                        else -> canonical
                    }
                }
                com.training.trackplanner.data.TrainableQuality.HYPERTROPHY -> {
                    when {
                        reviewedPersonal != null -> reviewedPersonal
                        canonical.sets.isNotEmpty() && canonical.sets.all {
                            provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE
                        } -> canonical.copy(
                            sets = canonical.sets.map { it.copy(targetRpeMin = 7.0) },
                            weightSource = if (canonical.sets.all { it.weightKg > 0.0 })
                                "TARGET_COMPATIBLE_CANONICAL_HYPERTROPHY_PRESCRIPTION"
                            else "TARGET_COMPATIBLE_PROVISIONAL_RPE_NO_INVENTED_LOAD"
                        )
                        else -> PlannedPrescription(
                            text = "Target-compatible hypertrophy provisional RPE prescription",
                            sets = List(canonicalItem.targetSets.coerceAtLeast(0)) { index ->
                                com.training.trackplanner.data.ProgramSetPrescription(index + 1, 8, 0.0, 0, 7.0)
                            },
                            restSeconds = canonical.restSeconds,
                            weightSource = "TARGET_COMPATIBLE_PROVISIONAL_RPE_NO_INVENTED_LOAD"
                        )
                    }
                }
                else -> canonical
            }
            qualityPrescriptions[authority] = targetPrescription
            if (reviewedPersonal != null) {
                historyBackedAuthorities += authority
                ownerHistoryBacked += owner
            }
            val usesHistory = reviewedPersonal != null
            if (owner !in prescriptions || (usesHistory && ownerProjectionUsesHistory[owner] != true)) {
                prescriptions[owner] = targetPrescription
                ownerProjectionUsesHistory[owner] = usesHistory
            }
        }
    }
    return CanonicalPrescriptionContext(
        prescriptions = prescriptions,
        historyBackedOwners = ownerHistoryBacked,
        prescriptionsByQuality = qualityPrescriptions,
        historyBackedAuthorities = historyBackedAuthorities
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
    val currentByQuality = canonicalPrescriptionContext.prescriptionsByQuality.mapValues { (authority, canonical) ->
        materialized[authority.owner] ?: canonical
    }
    return StimulusRealizationPrescriptionInputs(ownerKeys, prescriptions, currentByQuality)
}
