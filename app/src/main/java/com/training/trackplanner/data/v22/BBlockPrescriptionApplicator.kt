package com.training.trackplanner.data.v22

/** Applies relative volume steps without inventing initial kilograms. */
internal object BBlockPrescriptionApplicator {
    fun apply(
        session: V22Session,
        stage: Int,
        focus: BBalancedGoal,
        block: BBlockDefinition,
        mainKeys: Set<String>
    ): V22Session {
        val preferred = when (block.adjustment) {
            BVolumeAdjustment.LOAD_FIRST -> BProgressionField.LOAD
            BVolumeAdjustment.REPS_FIRST -> BProgressionField.REPS
            BVolumeAdjustment.SETS_FIRST -> BProgressionField.SETS
            BVolumeAdjustment.AUTO, BVolumeAdjustment.CUSTOM -> when (focus) {
                BBalancedGoal.STRENGTH -> BProgressionField.LOAD
                BBalancedGoal.HYPERTROPHY -> BProgressionField.REPS
                BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> BProgressionField.REPS
            }
        }
        val adjusted = session.items.map { item ->
            val isMain = item.stableKey in mainKeys
            val updatedSets = when {
                stage < 0 && isMain && item.sets.size > 1 ->
                    item.sets.dropLast((-stage).coerceAtMost(item.sets.size - 1))
                stage < 0 && isMain ->
                    item.sets.map { it.copy(reps = it.reps?.let { r -> (r + stage).coerceAtLeast(1) }) }
                stage > 0 && preferred == BProgressionField.REPS ->
                    item.sets.map { it.copy(reps = it.reps?.takeIf { r -> r > 0 }?.plus(stage)) }
                stage > 0 && preferred == BProgressionField.SETS && isMain && item.sets.isNotEmpty() ->
                    item.sets + List(stage) { item.sets.last().copy() }
                else -> item.sets.map(V22ObservedSet::copy)
            }
            item.copy(
                setCount = updatedSets.size,
                sets = updatedSets,
                // Load-first stays uncalibrated until the user enters the first week's kg.
                plannedProgression = "B_BLOCK_VOLUME_STEP=" + stage +
                    ";PREFERRED=" + preferred.name +
                    ";START_LOAD_USER_REQUIRED;RPE_USER_REVIEW"
            )
        }
        return session.copy(items = adjusted, nominalSets = adjusted.sumOf(V22ExercisePlan::setCount))
    }
}
