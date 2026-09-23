package com.training.trackplanner.data.personalized

/**
 * Mechanical seam for an exact prescription authorized before feasibility. The provider is
 * keyed by the full B5 identity and may return a prefix when downstream capacity asks for fewer
 * sets.
 */
fun interface ExactPrescriptionAuthorizationProvider {
    fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int = item.targetSets): PlannedPrescription?

    val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
        get() = emptyMap()
}

internal fun ExactPrescriptionAuthorizationProvider.prefixFor(
    item: PlannedExercise,
    requestedSets: Int = item.targetSets
): PlannedPrescription? = authorizedPrescriptionFor(item, requestedSets.coerceAtLeast(0))?.let { prescription ->
    val count = requestedSets.coerceIn(0, prescription.sets.size)
    prescription.copy(sets = prescription.sets.take(count).mapIndexed { index, set -> set.copy(setIndex = index + 1) })
}
