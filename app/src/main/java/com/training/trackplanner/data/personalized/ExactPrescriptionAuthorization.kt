package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/**
 * Mechanical seam for an exact prescription authorized before feasibility. The provider is
 * keyed by the full B5 identity and, when quality-specific, the exact executable authority
 * identity. It may return a prefix when downstream capacity asks for fewer sets.
 */
fun interface ExactPrescriptionAuthorizationProvider {
    fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription?

    /** Quality-specific authority lookup; callers must never infer quality from prescription shape. */
    fun authorizedPrescriptionFor(
        item: PlannedExercise,
        quality: com.training.trackplanner.data.TrainableQuality,
        requestedSets: Int
    ): PlannedPrescription? = authorizedPrescriptions[
        StimulusPrescriptionAuthorityIdentity(item.stableKey, item.role, quality)
    ]?.let { prescription ->
        if (requestedSets < 0 || requestedSets > prescription.sets.size) null
        else prescription.copy(sets = prescription.sets.take(requestedSets).mapIndexed { index, set -> set.copy(setIndex = index + 1) })
    }

    val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
        get() = emptyMap()

    /** Lossless authority table. The owner-only compatibility map is intentionally optional. */
    val authorizedPrescriptions: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription>
        get() = emptyMap()

    val multiQualityResolutions: Map<StimulusPrescriptionOwnerIdentity, StimulusMultiQualityPrescriptionResolution>
        get() = emptyMap()

    val conflictingOwners: Set<StimulusPrescriptionOwnerIdentity>
        get() = multiQualityResolutions.filterValues {
            it.status == StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
        }.keys
}

internal fun ExactPrescriptionAuthorizationProvider.authorizedPrescriptionFor(item: PlannedExercise): PlannedPrescription? =
    authorizedPrescriptionFor(item, item.targetSets)

/** Returns the exact owner-only authorized slice at a funded offset. Conflicting owners have no projection. */
internal fun ExactPrescriptionAuthorizationProvider.sliceFor(
    item: PlannedExercise,
    startOffset: Int,
    requestedSets: Int
): PlannedPrescription? {
    if (startOffset < 0 || requestedSets < 0) return null
    val identity = StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)
    val authorized = authorizedOwners[identity]
        ?: authorizedPrescriptionFor(item, item.targetSets)
        ?: return null
    if (startOffset > authorized.sets.size || startOffset + requestedSets > authorized.sets.size) return null
    return authorized.copy(sets = authorized.sets.drop(startOffset).take(requestedSets)
        .mapIndexed { index, set -> set.copy(setIndex = index + 1) })
}

/** Quality-specific funded slice. No other quality's offset or set multiset may be borrowed. */
internal fun ExactPrescriptionAuthorizationProvider.sliceFor(
    item: PlannedExercise,
    quality: com.training.trackplanner.data.TrainableQuality,
    startOffset: Int,
    requestedSets: Int
): PlannedPrescription? {
    if (startOffset < 0 || requestedSets < 0) return null
    val authorized = authorizedPrescriptions[
        StimulusPrescriptionAuthorityIdentity(item.stableKey, item.role, quality)
    ] ?: authorizedPrescriptionFor(item, quality, item.targetSets) ?: return null
    if (startOffset > authorized.sets.size || startOffset + requestedSets > authorized.sets.size) return null
    return authorized.copy(sets = authorized.sets.drop(startOffset).take(requestedSets)
        .mapIndexed { index, set -> set.copy(setIndex = index + 1) })
}

internal fun ExactPrescriptionAuthorizationProvider.prefixFor(
    item: PlannedExercise,
    requestedSets: Int = item.targetSets
): PlannedPrescription? = sliceFor(item, 0, requestedSets)

internal fun ExactPrescriptionAuthorizationProvider.prefixFor(
    item: PlannedExercise,
    quality: com.training.trackplanner.data.TrainableQuality,
    requestedSets: Int = item.targetSets
): PlannedPrescription? = sliceFor(item, quality, 0, requestedSets)

internal data class AuthorizedPrescriptionSubsetValidation(
    val valid: Boolean,
    val reasonCodes: List<String> = emptyList()
)

/**
 * Validates a complete week's rows as a multiset subset of one exact authorization. Rows may be
 * split or moved between days, but no authorized set instance may be reused or replaced.
 */
internal fun validateAuthorizedWeeklySubset(
    rows: List<ProgramSkeletonItem>,
    authorized: PlannedPrescription,
    stableKey: String,
    selectionRole: String
): AuthorizedPrescriptionSubsetValidation {
    val reasons = linkedSetOf<String>()
    rows.groupBy(ProgramSkeletonItem::weekNumber).values.forEach { weekRows ->
        val remaining = authorized.sets.groupingBy { it.semanticKey() }.eachCount().toMutableMap()
        weekRows.forEach { row ->
            if (row.exerciseStableKey != stableKey || row.selectionRole != selectionRole) {
                reasons += "B6_UNAUTHORIZED_SET_IDENTITY"
            }
            if (row.restSeconds != authorized.restSeconds || row.weightSource != authorized.weightSource) {
                reasons += "B6_PRESCRIPTION_AUTHORITY_MISMATCH"
            }
            row.setPrescriptions.forEach { set ->
                val key = set.semanticKey()
                val available = remaining[key] ?: 0
                if (available <= 0) {
                    if (authorized.sets.any { it.semanticKey() == key }) {
                        reasons += "B6_AUTHORIZED_SET_REUSED"
                    } else {
                        reasons += "B6_UNAUTHORIZED_SET_CONTENT"
                    }
                    reasons += "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED"
                } else {
                    remaining[key] = available - 1
                }
            }
        }
    }
    return AuthorizedPrescriptionSubsetValidation(reasons.isEmpty(), reasons.toList())
}

private fun com.training.trackplanner.data.ProgramSetPrescription.semanticKey(): String =
    "$reps|$weightKg|$seconds"
