package com.training.trackplanner.data.v22

/** Require approval for partly missing exercise evidence, not merely an empty account. */
internal object BBlockConfidenceReview {
    data class Decision(
        val requiresApproval: Boolean,
        val unreliableExerciseKeys: Set<String>,
        val notes: List<String>
    )

    fun evaluate(plan: BBalanced12WeekPlan, layout: BBlockProgramDefinition): Decision {
        if (layout.blocks.all { it.pattern == BVolumePattern.STEADY })
            return Decision(false, emptySet(), emptyList())
        val uncertain = plan.weeks.asSequence()
            .filter { layout.blocks[it.block - 1].pattern != BVolumePattern.STEADY }
            .flatMap { it.sessions.asSequence().flatMap { s -> s.items.asSequence() } }
            .filter { item ->
                item.historicalSets56d < 6 ||
                    item.source.contains("COLD_START") ||
                    item.source.contains("NEW_EXERCISE")
            }
            .mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val weakGlobalHistory = plan.goalTargets.all { it.confidence.history < 0.2 }
        val notes = buildList {
            if (uncertain.isNotEmpty()) add("INSUFFICIENT_EXERCISE_HISTORY")
            if (weakGlobalHistory) add("LOW_GLOBAL_HISTORY_CONFIDENCE")
        }
        return Decision(uncertain.isNotEmpty() || weakGlobalHistory, uncertain, notes)
    }
}
