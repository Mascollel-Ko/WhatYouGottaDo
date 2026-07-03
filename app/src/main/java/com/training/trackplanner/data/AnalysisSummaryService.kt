package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonTransferAnalysisEngine
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.coach.BadmintonTransferCoverageAnalyzer
import com.training.trackplanner.analysis.coach.BadmintonTransferCoverageSummary
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import java.time.format.DateTimeFormatter

internal class AnalysisSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
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
            initialProfile = initialUserProfileDao.profile()
        )
    }

    suspend fun badmintonTransferSummary(
        readinessSummary: TodayReadinessSummary? = null
    ): BadmintonTransferSummary {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        return BadmintonTransferAnalysisEngine(runtimeMetadataCatalog = resolvedRuntimeMetadataCatalog(exercises)).analyze(
            today = today,
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            readinessSummary = readinessSummary
        )
    }

    suspend fun badmintonTransferCoverageSummary(
        latestFatigueState: DailyFatigueState?
    ): BadmintonTransferCoverageSummary {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val entries = workoutDao.entriesWithSetsUntil(todayString)
        return BadmintonTransferCoverageAnalyzer(resolvedRuntimeMetadataCatalog(exercises)).analyze(
            today = today,
            exercises = exercises,
            entriesWithSets = entries,
            latestFatigueState = latestFatigueState
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
