package com.training.trackplanner.data

internal class ProgramGenerationService(
    private val exerciseDao: ExerciseDao
) {
    suspend fun generateProgramSkeleton(request: ProgramSkeletonRequest): GeneratedProgramSkeleton {
        val exercises = exerciseDao.allExercises()
        return ProgramSkeletonGenerator().generate(
            request = request,
            exercises = exercises,
            history = emptyList()
        )
    }
}
