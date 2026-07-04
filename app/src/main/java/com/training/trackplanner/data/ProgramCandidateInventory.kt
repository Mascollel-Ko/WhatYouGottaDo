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
        excludedTerms: List<String>
    ): ProgramCandidateInventoryResult {
        val active = exercises.filter(Exercise::isActive)
        val selectable = active.map { exercise ->
            val metadata = runtimeMetadataCatalog.resolve(exercise)
            ProgramCandidate(
                exercise = exercise,
                metadata = metadata,
                canonical = metadata != null,
                slotCapabilities = slotCapabilityResolver.resolve(exercise, metadata)
            )
        }.filter(ProgramCandidate::isProgramSelectable)
        val equipmentMatched = selectable.filter { it.matchesEquipment(availableEquipment) }
        val notExcluded = equipmentMatched.filter { candidate ->
            excludedTerms.none { candidate.exercise.name.contains(it, ignoreCase = true) }
        }
        return ProgramCandidateInventoryResult(
            allActive = active.size,
            programSelectable = selectable.size,
            equipmentMatched = equipmentMatched.size,
            notExcludedByUser = notExcluded.size,
            candidates = notExcluded,
            reservoir = ProgramCandidateReservoir(notExcluded)
        )
    }
}
