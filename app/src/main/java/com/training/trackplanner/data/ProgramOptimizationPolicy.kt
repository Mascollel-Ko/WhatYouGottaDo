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
                actions = repair.actions
            )
            if (accepted) {
                current = repair.skeleton
                currentEvaluation = candidateEvaluation
                acceptedActions += repair.actions
            } else {
                break
            }
        }

        val messages = if (acceptedActions.isNotEmpty()) {
            acceptedActions.map(::messageForAction).distinct()
        } else {
            currentEvaluation.suggestions.take(3)
        }
        return current.copy(
            evaluation = currentEvaluation,
            optimizationSummary = ProgramOptimizationSummary(messages = messages),
            optimizationTrace = traces
        )
    }

    private fun accepts(before: ProgramEvaluation, after: ProgramEvaluation): Boolean {
        val severeBefore = before.issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE }
        val severeAfter = after.issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE }
        return after.overallScore >= before.overallScore + 2 || severeAfter < severeBefore
    }

    private fun messageForAction(action: String): String = when (action) {
        "REOPEN_WEAK_SLOT_FOR_FOUNDATION" -> "근력운동과 배드민턴 전이훈련의 균형을 보정했습니다."
        "REOPEN_REPEATED_CORE_SLOT" -> "반복되는 코어 패턴을 다른 안정성 운동으로 조정했습니다."
        "SOFTEN_ADJACENT_HIGH_LOWER_DAY" -> "연속 하체 피로가 몰리지 않도록 일부 운동을 재배치했습니다."
        else -> "자동 골자 품질을 보정했습니다."
    }
}
