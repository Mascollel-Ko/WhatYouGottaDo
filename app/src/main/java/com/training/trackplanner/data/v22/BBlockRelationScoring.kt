package com.training.trackplanner.data.v22

/** Candidate ranking only; not a model of physiological adaptation. */
internal object BBlockRelationScoring {
    fun score(
        primaryGoal: Double,
        deficits: Double,
        preparesNext: Double,
        inPreviousBlock: Boolean,
        isKnownEquipment: Boolean,
        relation: BBlockRelation,
        muscleGap: Double = 0.0
    ): Double {
        val deficiencyWeight = when (relation) {
            BBlockRelation.COMPLEMENT_PLANNED_GAPS -> 0.24
            BBlockRelation.DEVELOP_PREVIOUS -> 0.05
            BBlockRelation.PREPARE_NEXT -> 0.05
            BBlockRelation.INDEPENDENT -> 0.08
        }
        val muscleWeight = if (relation == BBlockRelation.COMPLEMENT_PLANNED_GAPS) 0.45 else 0.10
        return primaryGoal * 4.0 + deficits * deficiencyWeight +
            muscleGap.coerceIn(0.0, 1.0) * muscleWeight +
            // Development tends to retain the preceding block's main movement.
            (if (relation == BBlockRelation.DEVELOP_PREVIOUS && inPreviousBlock) 0.4 else 0.0) +
            // Only a PREPARE_NEXT block receives lookahead credit.
            (if (relation == BBlockRelation.PREPARE_NEXT) preparesNext * 0.25 else 0.0) +
            (if (isKnownEquipment) 0.08 else 0.0)
    }
}
