package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal class AnalysisSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog
) {
    suspend fun fatigueAnalysisHistory(days: Int = 28 * 7): List<DailyFatigueResult> {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        return DailyFatigueCalculator(resolvedRuntimeMetadataCatalog(exercises)).calculateSeries(
            endDate = today,
            days = days.coerceIn(1, 28 * 7),
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            initialProfile = initialUserProfileDao.profile(),
            dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        )
    }

    suspend fun calendarOfiByDate(
        startDate: String,
        endDate: String
    ): Map<String, Int> {
        val start = LocalDate.parse(startDate)
        val requestedEnd = LocalDate.parse(endDate)
        if (requestedEnd < start) return emptyMap()

        val today = SystemAnalysisDateProvider().today()
        if (start > today) return emptyMap()

        val effectiveEnd = minOf(requestedEnd, today)
        val effectiveEndString = effectiveEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val days = ChronoUnit.DAYS.between(start, effectiveEnd).toInt() + 1
        return DailyFatigueCalculator(resolvedRuntimeMetadataCatalog(exercises)).calculateSeries(
            endDate = effectiveEnd,
            days = days,
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(effectiveEndString),
            initialProfile = initialUserProfileDao.profile(),
            dailyMetrics = dailyMetricDao.metricsUntil(effectiveEndString)
        ).associate { result ->
            result.state.date.format(DateTimeFormatter.ISO_LOCAL_DATE) to
                result.state.overallFatigueIndex
        }
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)
}
