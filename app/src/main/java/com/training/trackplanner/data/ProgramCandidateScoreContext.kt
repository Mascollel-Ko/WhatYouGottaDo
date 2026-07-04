package com.training.trackplanner.data

internal data class ProgramCandidateScoreContext(
    val request: ProgramSkeletonRequest,
    val week: ProgramWeekPlan,
    val periodizedWeek: ProgramPeriodizationWeekPlan,
    val plannedSlot: PlannedSlot,
    val templateSlot: TemplateExerciseSlot,
    val selectedInSession: List<ProgramCandidate>,
    val generatedItems: List<ProgramSkeletonItem>
)
