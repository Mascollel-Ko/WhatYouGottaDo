package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.lab.CheckInMetricSeriesBuilder
import com.training.trackplanner.analysis.lab.SmashSpeedMetricSeriesBuilder
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.proxyperformance.ProxyPerformanceSummaryBuilder
import com.training.trackplanner.analysis.trends.PerformanceTrendEngine
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import java.time.format.DateTimeFormatter

internal class PerformanceTrendSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val smashSpeedDao: SmashSpeedDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog
) {
    suspend fun build(): PerformanceTrendSummary {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        val entries = workoutDao.entriesWithSetsUntil(todayString)
        val runtimeMetadataCatalog = resolvedRuntimeMetadataCatalog(exercises)
        val base = PerformanceTrendEngine(runtimeMetadataCatalog).analyze(
            today = today,
            exercises = exercises,
            entriesWithSets = entries,
            dailyMetrics = dailyMetrics
        )
        val checkInSeries = CheckInMetricSeriesBuilder.build(
            checkIns = dailyCheckInDao.between(MIN_DATE, todayString),
            dailyMetrics = dailyMetrics
        )
        val smashSpeedSeries = SmashSpeedMetricSeriesBuilder.build(
            records = smashSpeedDao.between(MIN_DATE, todayString)
        )
        val strengthAndMuscleSeries = StrengthAndMuscleMetricSeriesBuilder.build(
            entriesWithSets = entries,
            exercises = exercises,
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            dailyMetrics = dailyMetrics
        )
        val proxyPerformanceSummary = ProxyPerformanceSummaryBuilder.build(
            today = today,
            exercises = exercises,
            entriesWithSets = entries,
            dailyMetrics = dailyMetrics,
            initialProfile = initialUserProfileDao.profile(),
            runtimeMetadataCatalog = runtimeMetadataCatalog
        )
        return base.copy(
            metricSeries = base.metricSeries + checkInSeries + smashSpeedSeries + strengthAndMuscleSeries,
            proxyPerformanceSummary = proxyPerformanceSummary
        )
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

    private companion object {
        const val MIN_DATE = "0001-01-01"
    }
}
