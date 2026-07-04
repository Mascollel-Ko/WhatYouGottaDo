package com.training.trackplanner.data

sealed interface ProgramBuildProgressState {
    data object Idle : ProgramBuildProgressState

    data class Running(
        val progressPercent: Int,
        val message: String
    ) : ProgramBuildProgressState

    data class Completed(
        val skeleton: GeneratedProgramSkeleton,
        val summary: ProgramOptimizationSummary
    ) : ProgramBuildProgressState

    data class Failed(
        val message: String
    ) : ProgramBuildProgressState
}
