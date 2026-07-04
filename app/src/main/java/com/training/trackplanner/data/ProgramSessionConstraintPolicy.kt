package com.training.trackplanner.data

internal class ProgramSessionConstraintPolicy {
    fun sessionAllows(
        selected: List<ProgramCandidate>,
        next: ProgramCandidate,
        slot: ProgramTrainingSlot,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate
    ): Boolean {
        if (next.isRehabLikeActivation) {
            val recoveryContext = slot in EXPANDED_RECOVERY_SLOTS || week.deloadFlag ||
                gate.band == ProgramFatigueBand.RED
            val cap = if (recoveryContext) 3 else 1
            if (selected.count(ProgramCandidate::isRehabLikeActivation) >= cap) return false
        }
        if (selected.count(ProgramCandidate::isIsolation) >= 2 && next.isIsolation) return false
        if (selected.any(ProgramCandidate::isHeavyLower) && next.isHeavyLower) return false
        val hasHeavyLower = selected.any(ProgramCandidate::isHeavyLower) || next.isHeavyLower
        val hasImpact = selected.any(ProgramCandidate::isHighImpact) || next.isHighImpact
        val hasCod = selected.any(ProgramCandidate::isHighIntensityCod) || next.isHighIntensityCod
        return !(hasHeavyLower && hasImpact && hasCod)
    }

    private companion object {
        val EXPANDED_RECOVERY_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB,
            ProgramTrainingSlot.MICRO_RECOVERY
        )
    }
}
