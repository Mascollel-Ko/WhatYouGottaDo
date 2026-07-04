package com.training.trackplanner.data

internal data class ProgramCandidateReservoir(
    val candidates: List<ProgramCandidate>,
    val classifications: Map<Long, ProgramCandidateClassification> =
        candidates.associate { candidate ->
            candidate.exercise.id to ProgramCandidateClassificationPolicy().classify(candidate)
        }
) {
    fun classification(candidate: ProgramCandidate): ProgramCandidateClassification =
        classifications.getValue(candidate.exercise.id)

    fun byTier(tier: ProgramCandidateTier): List<ProgramCandidate> =
        candidates.filter { classification(it).tier == tier }
}
