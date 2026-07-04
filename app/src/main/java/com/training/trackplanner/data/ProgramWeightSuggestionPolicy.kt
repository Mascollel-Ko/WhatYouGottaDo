package com.training.trackplanner.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ProgramWeightSuggestion(val weightKg: Double, val source: String)

internal class ProgramWeightSuggestionPolicy(
    history: List<WorkoutEntryWithSets>,
    exercises: List<Exercise>
) {
    private val exerciseById = exercises.associateBy(Exercise::id)
    private val confirmed = history.flatMap { entry ->
        entry.sets.filter { it.confirmed && it.weightKg > 0.0 && it.reps > 0 }.map { set ->
            HistoricalSet(entry.entry.exerciseId, entry.entry.date, set.reps, set.weightKg)
        }
    }

    fun suggest(
        exercise: Exercise,
        targetReps: Int,
        intensityMultiplier: Double,
        today: LocalDate
    ): ProgramWeightSuggestion {
        if (targetReps <= 0) return ProgramWeightSuggestion(0.0, "TIMED_OR_QUALITY")
        val recentCutoff = today.minusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val direct = confirmed.filter { it.exerciseId == exercise.id }
        val recent = direct.filter { it.date >= recentCutoff }.maxByOrNull(HistoricalSet::date)
        val source = recent
            ?: direct.maxByOrNull { it.weightKg * it.reps }
            ?: return ProgramWeightSuggestion(0.0, "MANUAL_INPUT")
        val e1rm = source.weightKg * (1.0 + source.reps / 30.0)
        val target = e1rm / (1.0 + targetReps / 30.0) * intensityMultiplier * 0.90
        val step = if (exerciseById[source.exerciseId]?.equipment?.uppercase(Locale.US)?.contains("DUMBBELL") == true) 2.0 else 2.5
        return ProgramWeightSuggestion(
            weightKg = (target / step).toInt().coerceAtLeast(0) * step,
            source = if (recent != null) "DIRECT_HISTORY_HIGH" else "DIRECT_HISTORY_MEDIUM"
        )
    }

    private data class HistoricalSet(
        val exerciseId: Long,
        val date: String,
        val reps: Int,
        val weightKg: Double
    )
}
