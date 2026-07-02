package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.readiness.TodayReadinessEngineInput
import java.time.format.DateTimeFormatter

internal class DailyReadinessInputService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val dailyStatusService: DailyStatusService
) {
    suspend fun build(): TodayReadinessEngineInput {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        return TodayReadinessEngineInput(
            today = today,
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            dailyMetrics = dailyMetrics,
            dailyCheckIns = dailyStatusService.canonicalizeCheckIns(
                dailyCheckInDao.between(today.minusDays(13).toString(), todayString),
                dailyMetrics
            ),
            initialProfile = initialUserProfileDao.profile(),
            runtimeMetadataCatalog = RuntimeExerciseMetadataResolver(
                canonicalRuntimeMetadataCatalog,
                runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            ).catalog(exercises)
        )
    }
}

