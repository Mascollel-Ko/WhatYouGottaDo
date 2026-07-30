package com.training.trackplanner.data

internal class ProgramOptimizationPolicy(
    private val evaluationPolicy: ProgramEvaluationPolicy = ProgramEvaluationPolicy(),
    private val repairPolicy: ProgramRepairPolicy = ProgramRepairPolicy(),
    private val maxIterations: Int = 3
) {
    fun optimize(
        initial: GeneratedProgramSkeleton,
        reservoir: ProgramCandidateReservoir? = null
    ): GeneratedProgramSkeleton {
        var current = initial
        var currentEvaluation = evaluationPolicy.evaluate(current)
        val traces = mutableListOf<ProgramOptimizationTrace>()
        val acceptedActions = mutableListOf<String>()

        for (iteration in 0 until maxIterations) {
            val repair = repairPolicy.repair(current, currentEvaluation, reservoir)
            if (repair.actions.isEmpty()) break
            val candidateEvaluation = evaluationPolicy.evaluate(repair.skeleton)
            val accepted = accepts(currentEvaluation, candidateEvaluation)
            traces += ProgramOptimizationTrace(
                iteration = iteration + 1,
                beforeScore = currentEvaluation.overallScore,
                afterScore = candidateEvaluation.overallScore,
                accepted = accepted,
                actions = repair.actions,
                details = repair.details
            )
            if (accepted) {
                current = repair.skeleton
                currentEvaluation = candidateEvaluation
                acceptedActions += repair.actions
            } else {
                break
            }
        }

        val notices = if (acceptedActions.isNotEmpty()) {
            acceptedActions.map(::programNoticeForOptimizationAction).distinct()
        } else {
            emptyList()
        }
        return current.copy(
            evaluation = currentEvaluation,
            optimizationSummary = ProgramOptimizationSummary(notices = notices),
            optimizationTrace = traces
        )
    }

    private fun accepts(before: ProgramEvaluation, after: ProgramEvaluation): Boolean {
        val severeBefore = before.issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE }
        val severeAfter = after.issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE }
        return after.overallScore >= before.overallScore + 2 || severeAfter < severeBefore
    }

}

internal fun programNoticeForOptimizationAction(action: String): ProgramUserNotice =
    ProgramUserNotice(
        code = when (action) {
            "REOPEN_FILLER_SLOT_FOR_SELECTED_MAIN" ->
                ProgramUserNoticeCode.MAIN_EXERCISE_PRIORITY_RESTORED
            "REOPEN_WEAK_SLOT_FOR_FOUNDATION" ->
                ProgramUserNoticeCode.FOUNDATION_BALANCE_RESTORED
            "REOPEN_REPEATED_CORE_SLOT" ->
                ProgramUserNoticeCode.REPEATED_CORE_PATTERN_REPLACED
            "SOFTEN_ADJACENT_HIGH_LOWER_DAY" ->
                ProgramUserNoticeCode.ADJACENT_LOWER_FATIGUE_REDUCED
            else -> ProgramUserNoticeCode.AUTOMATIC_QUALITY_ADJUSTMENT
        },
        level = ProgramUserNoticeLevel.SUCCESS
    )
