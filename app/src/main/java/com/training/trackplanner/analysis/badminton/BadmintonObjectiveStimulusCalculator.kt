package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.core.AnalysisStimulusRpePolicy
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

object BadmintonObjectiveStimulusContract {
    const val VERSION = "BADMINTON_OBJECTIVE_STIMULUS_V2"
}

data class BadmintonObjectiveDailyPoint(
    val date: LocalDate,
    val objectiveStimulus: Map<String, Double>
)

class BadmintonObjectiveStimulusCalculator(
    private val catalog: CanonicalBadmintonObjectiveCatalog
) {
    fun daily(
        records: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): List<BadmintonObjectiveDailyPoint> = records
        .filter { record -> record.sets.any { set -> set.confirmed } }
        .groupBy { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
        .mapNotNull { (date, sameDateRecords) ->
            date ?: return@mapNotNull null
            val stimulus = calculate(sameDateRecords, exerciseMap)
            if (stimulus.values.none { value -> value > 0.0 }) return@mapNotNull null
            BadmintonObjectiveDailyPoint(date, stimulus)
        }
        .sortedBy(BadmintonObjectiveDailyPoint::date)

    fun calculate(
        records: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): Map<String, Double> {
        val totals = linkedMapOf<String, Double>()
        records.forEach { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@forEach
            if (exercise.activityKind == "SPORT_SESSION") return@forEach
            val relations = catalog.relations(record.entry.exerciseStableKey)
            if (relations.isEmpty()) return@forEach
            record.sets.filter { set -> set.confirmed }.forEach { set ->
                val rpeModifier = AnalysisStimulusRpePolicy.modifier(set.rpe ?: record.entry.rpe)
                relations.forEach { relation ->
                    val value = rpeModifier * relation.transferLevel.coefficient
                    if (value > 0.0) {
                        val key = relation.objective.name
                        totals[key] = (totals[key] ?: 0.0) + value
                    }
                }
            }
        }
        return BadmintonObjective.entries.associate { objective ->
            objective.name to (totals[objective.name] ?: 0.0)
        }
    }
}
