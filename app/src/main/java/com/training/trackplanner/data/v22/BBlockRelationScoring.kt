package com.training.trackplanner.data.v22

/** Candidate ranking only; not a model of physiological adaptation. */
internal object BBlockRelationScoring {
    fun score(
        primaryGoal: Double,
        deficits: Double,
        inPreviousBlock: Boolean,
        isKnownEquipment: Boolean,
        relation: BBlockRelation,
        muscleGap: Double = 0.0
    ): Double {
        val active = relation.active()
        // Deficits are exclusively opt-in; independent/develop keep only history
        // and the current block purpose. Retired PREPARE_NEXT behaves independently.
        val gapBonus = if (active == BBlockRelation.COMPLEMENT_PLANNED_GAPS)
            deficits * 0.24 + muscleGap.coerceIn(0.0, 1.0) * 0.45
            else 0.0
        val continuityBonus = if (active == BBlockRelation.DEVELOP_PREVIOUS && inPreviousBlock)
            0.4 else 0.0
        return primaryGoal * 4.0 + gapBonus + continuityBonus +
            (if (isKnownEquipment) 0.08 else 0.0)
    }
}
