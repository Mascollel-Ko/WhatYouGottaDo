package com.training.trackplanner.data

data class ProgramOptimizationSummary(
    val messages: List<String> = emptyList()
)

data class ProgramOptimizationTrace(
    val iteration: Int,
    val beforeScore: Int,
    val afterScore: Int,
    val accepted: Boolean,
    val actions: List<String>
)

internal data class ProgramRepairResult(
    val skeleton: GeneratedProgramSkeleton,
    val actions: List<String>
)
