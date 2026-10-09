package com.training.trackplanner.data.personalized

/**
 * The finite allocator is intentionally domain-neutral and consumes candidates in input order.
 * Establish the already-authorized canonical priority before calling that frozen kernel.
 */
internal fun orderMaterialCandidatesForFiniteAllocation(
    candidates: List<PlannedExercise>,
    isResistance: (stableKey: String) -> Boolean
): List<PlannedExercise> = candidates.sortedWith(
    compareByDescending<PlannedExercise> { it.priority }
        // Preserve the existing equal-priority preference and deterministic identity tie-breaks.
        .thenByDescending { isResistance(it.stableKey) }
        .thenBy { it.stableKey }
        .thenBy { it.role }
        .thenBy { it.styleVariant }
)
