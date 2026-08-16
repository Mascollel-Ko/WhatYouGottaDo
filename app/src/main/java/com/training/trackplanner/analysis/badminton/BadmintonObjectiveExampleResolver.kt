package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets

class BadmintonObjectiveExampleResolver(
    private val catalog: CanonicalBadmintonObjectiveCatalog
) {
    fun resolve(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>,
        displayNamesByStableKey: Map<String, String>
    ): Map<String, List<String>> {
        val examples = linkedMapOf<String, MutableList<String>>()
        entriesWithSets
            .filter { record -> record.sets.any { set -> set.confirmed } }
            .forEach { record ->
                val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@forEach
                if (exercise.activityKind == "SPORT_SESSION") return@forEach
                val name = listOf(
                    displayNamesByStableKey[exercise.stableKey],
                    record.entry.exerciseName,
                    exercise.name
                ).filterNotNull().firstOrNull { candidate -> !candidate.matches(Regex("""운동\s*\d+""")) }.orEmpty()
                if (name.isBlank()) return@forEach
                catalog.relations(record.entry.exerciseStableKey).forEach { relation ->
                    val values = examples.getOrPut(relation.objective.name) { mutableListOf() }
                    if (name !in values && values.size < 2) values += name
                }
            }
        return examples
    }
}
