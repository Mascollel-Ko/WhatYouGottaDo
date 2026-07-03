package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import java.time.format.DateTimeFormatter

internal class ProgramGenerationService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeMetadataCatalogResolver: suspend (List<Exercise>) -> RuntimeExerciseMetadataCatalog
) {
    suspend fun generateProgramSkeleton(request: ProgramSkeletonRequest): GeneratedProgramSkeleton {
        val exercises = exerciseDao.allExercises()
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val history = workoutDao.entriesWithSetsUntil(todayString)
        val metadataCatalog = runtimeMetadataCatalogResolver(exercises)
        val fatigueState = runCatching {
            DailyFatigueCalculator(metadataCatalog).calculate(
                targetDate = today,
                exercises = exercises,
                entriesWithSets = history,
                initialProfile = initialUserProfileDao.profile()
            ).state
        }.getOrNull()
        return ProgramSkeletonGenerator().generate(
            request = request,
            exercises = exercises,
            history = history,
            today = today,
            runtimeMetadataCatalog = metadataCatalog,
            fatigueState = fatigueState
        )
    }
}
