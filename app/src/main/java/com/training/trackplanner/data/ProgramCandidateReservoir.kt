package com.training.trackplanner.data

internal data class ProgramCandidateReservoir(
    val candidates: List<ProgramCandidate>,
    val classifications: Map<String, ProgramCandidateClassification> =
        candidates.associate { candidate ->
            candidate.exercise.stableKey to ProgramCandidateClassificationPolicy().classify(candidate)
        }
) {
    fun classification(candidate: ProgramCandidate): ProgramCandidateClassification =
        classifications.getValue(candidate.exercise.stableKey)

    fun byTier(tier: ProgramCandidateTier): List<ProgramCandidate> =
        candidates.filter { classification(it).tier == tier }
}
