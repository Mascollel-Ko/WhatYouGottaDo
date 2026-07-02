package com.training.trackplanner.data

import com.training.trackplanner.analysis.coach.CoachingSignalsBuilder
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

internal class CoachingSignalsSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val dailyStatusService: DailyStatusService
) {
    suspend fun build(history: List<DailyFatigueResult>): CoachingSignalsSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val startDate = today.minusDays(56).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        val checkIns = dailyStatusService.canonicalizeCheckIns(
            dailyCheckInDao.between(startDate, todayString),
            dailyMetrics.filter { metric -> metric.date >= startDate }
        )
        CoachingSignalsBuilder().build(
            today = today,
            dailyMetrics = dailyMetrics,
            checkIns = checkIns,
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            exercises = exercises,
            runtimeMetadataCatalog = resolvedRuntimeMetadataCatalog(exercises),
            history = history
        )
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)
}

