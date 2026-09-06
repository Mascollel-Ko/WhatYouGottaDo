package com.training.trackplanner.data.program.legacy

// Mechanically isolated from f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9.
// Frozen product rules: do not generalize or route through another planner.
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramSetPrescription

internal class LegacyAutoGenerationService(
    private val exerciseDao: ExerciseDao
) {
    suspend fun generateProgramSkeleton(request: LegacyAutoRequest): LegacyAutoSkeleton {
        val exercises = exerciseDao.allExercises()
        return LegacyAutoProgramBuilder().build(request = request, exercises = exercises)
    }
}
