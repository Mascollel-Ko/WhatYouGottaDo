package com.training.trackplanner.data

internal class ProgramCandidateReservoir(candidates: List<ProgramCandidate>) {
    val candidates: List<ProgramCandidate> =
        candidates.filter { candidate -> ProgramCandidateAuthority.allows(candidate.exercise.stableKey) }

    val classifications: Map<String, ProgramCandidateClassification> =
        this.candidates.associate { candidate ->
            candidate.exercise.stableKey to ProgramCandidateClassificationPolicy().classify(candidate)
        }

    fun classification(candidate: ProgramCandidate): ProgramCandidateClassification =
        classifications.getValue(candidate.exercise.stableKey)

    fun byTier(tier: ProgramCandidateTier): List<ProgramCandidate> =
        candidates.filter { classification(it).tier == tier }
}
