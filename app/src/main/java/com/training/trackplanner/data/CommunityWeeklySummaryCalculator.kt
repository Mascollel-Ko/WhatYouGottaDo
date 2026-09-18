package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Builds the small public weekly projection from the app's canonical identities/calculators. */
internal object CommunityWeeklySummaryCalculator {
    fun payload(
        entries: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        start: LocalDate,
        end: LocalDate,
        runtimeMetadata: RuntimeExerciseMetadataCatalog
    ): JSONObject {
        val exerciseMap = exercises.associateBy(Exercise::stableKey)
        val inRange = entries.filter { row ->
            runCatching { LocalDate.parse(row.entry.date) }.getOrNull()?.let { it in start..end } == true
        }
        val confirmed = inRange.filter { row -> row.sets.any(WorkoutSet::confirmed) }
        val badmintonCalculator = BadmintonPracticeLoadCalculator(runtimeMetadata)
        val badmintonRows = confirmed.filter { row ->
            val exercise = exerciseMap[row.entry.exerciseStableKey] ?: return@filter false
            val activityKind = runtimeMetadata.resolve(exercise)?.activityKind ?: exercise.activityKind
            com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog.admits(
                exercise.stableKey,
                activityKind
            )
        }
        val sessionGroups = confirmed.groupBy { row -> row.entry.sessionStableKey.ifBlank { "date:${row.entry.date}" } }
        val strengthRows = confirmed.filterNot { row -> row in badmintonRows }
        val strengthSessionKeys = strengthRows.map { it.entry.sessionStableKey.ifBlank { "date:${it.entry.date}" } }.toSet()
        val badmintonSessionKeys = badmintonRows.map { it.entry.sessionStableKey.ifBlank { "date:${it.entry.date}" } }.toSet()
        val trainingDays = confirmed.map { it.entry.date }.toSet().size
        val badmintonMinutes = badmintonCalculator
            .dailyLoads(badmintonRows, exerciseMap)
            .sumOf { point -> point.durationMinutes }
            .roundToInt()
        // Keep the grouping explicit so multiple rows in one session cannot inflate session counts.
        check(sessionGroups.keys.containsAll(strengthSessionKeys + badmintonSessionKeys))
        return JSONObject()
            .put("weekStart", start.toString())
            .put("weekEnd", end.toString())
            .put("trainingDays", trainingDays)
            .put("strengthSessionCount", strengthSessionKeys.size)
            .put("confirmedStrengthSetCount", strengthRows.sumOf { row -> row.sets.count(WorkoutSet::confirmed) })
            .put("badmintonSessionCount", badmintonSessionKeys.size)
            .put("badmintonMinutes", badmintonMinutes)
    }
}
