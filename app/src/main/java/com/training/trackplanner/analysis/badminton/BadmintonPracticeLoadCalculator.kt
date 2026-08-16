package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendMath
import com.training.trackplanner.analysis.trends.WeeklyTrainingData
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

data class BadmintonPracticeDailyPoint(
    val date: LocalDate,
    val practiceLoad: Double,
    val durationMinutes: Double
)

data class BadmintonPracticeWeekPoint(
    val weekStart: LocalDate,
    val practiceLoad: Double,
    val durationMinutes: Double,
    val confidence: AnalysisConfidence
)

class BadmintonPracticeLoadCalculator(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun weeklyLoads(
        weeks: List<WeeklyTrainingData>,
        exerciseMap: Map<String, Exercise>
    ): List<BadmintonPracticeWeekPoint> = weeks.mapIndexed { index, week ->
        val contribution = contribution(week.entries, exerciseMap)
        BadmintonPracticeWeekPoint(
            weekStart = week.weekStart,
            practiceLoad = contribution.load,
            durationMinutes = contribution.durationMinutes,
            confidence = TrendMath.confidenceForDays(
                weeks.take(index).count { prior -> contribution(prior.entries, exerciseMap).load > 0.0 } * 7
            )
        )
    }

    fun dailyLoads(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): List<BadmintonPracticeDailyPoint> = entriesWithSets
        .groupBy { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
        .mapNotNull { (date, records) ->
            date ?: return@mapNotNull null
            val contribution = contribution(records, exerciseMap)
            if (contribution.load <= 0.0) return@mapNotNull null
            BadmintonPracticeDailyPoint(
                date = date,
                practiceLoad = contribution.load,
                durationMinutes = contribution.durationMinutes
            )
        }
        .sortedBy(BadmintonPracticeDailyPoint::date)

    fun calculateRaw(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): Double = contribution(entriesWithSets, exerciseMap).load

    private fun contribution(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): PracticeContribution {
        var load = 0.0
        var durationMinutes = 0.0
        entriesWithSets.forEach { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@forEach
            val activityKind = runtimeMetadataCatalog.resolve(exercise)?.activityKind
                ?.takeIf(String::isNotBlank)
                ?: exercise.activityKind
            if (!BadmintonPracticeCatalog.admits(record.entry.exerciseStableKey, activityKind)) return@forEach

            val confirmedSets = record.sets.filter { set -> set.confirmed }
            val recordMinutes = confirmedSets.sumOf { set -> set.seconds.coerceAtLeast(0) } / 60.0
            if (recordMinutes <= 0.0) return@forEach
            val setRpe = confirmedSets.mapNotNull { set -> set.rpe }
            val effectiveRpe = setRpe.takeIf(List<Double>::isNotEmpty)?.average() ?: record.entry.rpe
            durationMinutes += recordMinutes
            load += recordMinutes * badmintonIntensityFactor(effectiveRpe)
        }
        return PracticeContribution(load, durationMinutes)
    }

    private fun badmintonIntensityFactor(rpe: Double?): Double = when {
        rpe == null -> 1.00
        rpe <= 6.0 -> 0.90
        rpe < 8.0 -> 1.00
        rpe < 9.0 -> 1.05
        rpe < 10.0 -> 1.10
        else -> 1.15
    }

    private data class PracticeContribution(
        val load: Double,
        val durationMinutes: Double
    )
}
