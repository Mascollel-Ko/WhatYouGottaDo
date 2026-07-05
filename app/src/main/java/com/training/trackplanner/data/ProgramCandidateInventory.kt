package com.training.trackplanner.data

internal data class ProgramCandidateInventoryResult(
    val allActive: Int,
    val programSelectable: Int,
    val equipmentMatched: Int,
    val notExcludedByUser: Int,
    val candidates: List<ProgramCandidate>,
    val reservoir: ProgramCandidateReservoir = ProgramCandidateReservoir(candidates)
)

internal class ProgramCandidateInventory(
    private val slotCapabilityResolver: SlotCapabilityResolver = SlotCapabilityResolver.DEFAULT
) {
    fun collect(
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
        availableEquipment: Set<String>,
        excludedExerciseStableKeys: Set<String> = emptySet()
    ): ProgramCandidateInventoryResult {
        val active = exercises.filter(Exercise::isActive)
        val hardEligible = active.map { exercise ->
            val metadata = runtimeMetadataCatalog.resolve(exercise)
            ProgramCandidate(
                exercise = exercise,
                metadata = metadata,
                canonical = metadata != null,
                slotCapabilities = slotCapabilityResolver.resolve(exercise, metadata)
            )
        }.filterNot { it.isHardExcludedFromProgramInventory() }
        val normalizedExcluded = excludedExerciseStableKeys.filter(String::isNotBlank).toSet()
        val notExcluded = hardEligible.filter { candidate -> candidate.exercise.stableKey !in normalizedExcluded }
        return ProgramCandidateInventoryResult(
            allActive = active.size,
            programSelectable = hardEligible.size,
            equipmentMatched = hardEligible.size,
            notExcludedByUser = notExcluded.size,
            candidates = notExcluded,
            reservoir = ProgramCandidateReservoir(notExcluded)
        )
    }

    private fun ProgramCandidate.isHardExcludedFromProgramInventory(): Boolean {
        if (isDirectSportSession) return true
        if (exercise.resolvedActivityKind() != ActivityKind.TRAINING_EXERCISE) return true
        if (metadata?.planningEligibility in HARD_PLANNING_EXCLUSIONS) return true
        return exercise.resolvedPlanningEligibility() in HARD_LEGACY_PLANNING_EXCLUSIONS
    }

    private companion object {
        val HARD_PLANNING_EXCLUSIONS = setOf(
            PlanningEligibility.FATIGUE_ONLY.name,
            PlanningEligibility.ANALYSIS_ONLY.name,
            "HIDDEN"
        )
        val HARD_LEGACY_PLANNING_EXCLUSIONS = setOf(
            PlanningEligibility.FATIGUE_ONLY,
            PlanningEligibility.ANALYSIS_ONLY
        )
    }
}
