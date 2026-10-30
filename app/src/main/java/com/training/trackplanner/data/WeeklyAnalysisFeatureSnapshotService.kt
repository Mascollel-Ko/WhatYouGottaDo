package com.training.trackplanner.data

import android.content.Context
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshotBuilder
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

internal class WeeklyAnalysisFeatureSnapshotService(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val canonicalMetadataRepository: CanonicalExerciseMetadataRepository,
    private val userOverrideDao: ExerciseMetadataUserOverrideDao? = null
) {
    suspend fun build(
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        sourceRevision: Long
    ): WeeklyAnalysisFeatureSnapshot {
        val today = SystemAnalysisDateProvider().today()
        val metadataRevision = ExerciseMetadataRevisionPolicy.project(context, canonicalMetadataRepository)
            .semanticCanonicalMetadataRevision
        val exercises = exerciseDao.allExercises()
        val effectiveMuscles = UserMuscleRoleCodec.effectiveRelations(
            canonicalMetadataRepository.muscleRelations(), userOverrideDao?.all().orEmpty(), exercises
        )
        return WeeklyAnalysisFeatureSnapshotBuilder.build(
            today = today,
            metricSeries = metricSeries,
            entriesWithSets = workoutDao.entriesWithSetsUntil(today.toString()),
            exercises = exercises,
            dailyMetrics = dailyMetricDao.metricsUntil(today.toString()),
            initialProfile = initialUserProfileDao.profile(),
            muscleRelations = effectiveMuscles,
            sourceRevision = sourceRevision,
            metadataRevision = metadataRevision
        )
    }
}
