package com.training.trackplanner.data.personalized

internal const val QUESTION_INTERRUPTION_CAUSE = "INTERRUPTION_CAUSE"
internal const val QUESTION_INTERRUPTION_FREQUENCY = "INTERRUPTION_FREQUENCY"

/** Explicit profile choices mapped only through canonical movement/stress metadata, never names. */
internal fun PlanningHistorySnapshot.explicitlyRestricted(key: String): Boolean {
    val movement = movementCoverage(key)
    val modes = hardRestrictedModes
    val highImpact = metadata[key]?.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
    return (modes.any { it in setOf("LOW_BACK", "HEAVY_DEADLIFT") } && movement == MovementCoverage.POSTERIOR_CHAIN) ||
        (modes.any { it in setOf("KNEE", "HEAVY_SQUAT") } && movement == MovementCoverage.LOWER_KNEE) ||
        (modes.any { it in setOf("SHOULDER", "BENCH_OR_PUSH") } && movement in setOf(MovementCoverage.HORIZONTAL_PUSH,MovementCoverage.VERTICAL_PUSH)) ||
        ("OVERHEAD_PRESS" in modes && movement == MovementCoverage.VERTICAL_PUSH) ||
        (modes.any { it in setOf("JUMP_LANDING", "LUNGE_DECELERATION") } && highImpact) ||
        ("LONG_BADMINTON" in modes && activityKind(key) == PlannedActivityKind.GENERIC_COURT_SESSION)
}

internal fun PlanningHistorySnapshot.trainingStateInput(answers: PersonalizedPlanningAnswers) = TrainingStateInput(
    cutoff,allConfirmedSets,dailyStrain,exercises.keys.associateWith(::activityKind),exercises.keys.associateWith(::movementCoverage),
    metadata.filterValues { it.progressMetricType in setOf("LOAD_REPS","VOLUME_LOAD","ESTIMATED_1RM") }.keys,
    canonicalStrengthSignals,recoverySignals,exercises.mapValues { it.value.defaultRestSeconds },weeklyCourtLoad,hardRestrictedModes,
    answers.values[QUESTION_INTERRUPTION_CAUSE]?.let { runCatching { InterruptionCause.valueOf(it) }.getOrNull() }
        ?:preferences.interruptionCause?:InterruptionCause.UNSURE,
    answers.values[QUESTION_INTERRUPTION_FREQUENCY]?.let { runCatching { InterruptionFrequency.valueOf(it) }.getOrNull() }
        ?:preferences.interruptionFrequency?:InterruptionFrequency.UNSURE)

internal fun TrainingStateAssessment.explanation(): String = when(state) {
    TrainingState.HARD_RESTRICTION -> "명시적 회복·조직 제한은 유지합니다. 제한 범위 밖의 훈련량은 장기 수행과 소화 기록을 별도로 반영합니다."
    TrainingState.PRODUCTIVE_HIGH_LOAD -> "최근 부담은 높지만 여러 움직임의 수행과 훈련 소화가 유지·개선돼 일률적으로 감량하지 않습니다."
    TrainingState.TOLERATED_HIGH_LOAD -> "부담이 높은 상태를 소화한 기록이 있어 한 번의 전신 조정만 적용합니다."
    TrainingState.PRODUCTIVE_NORMAL -> "최근 수행 개선과 훈련 소화 기록을 반영합니다."
    TrainingState.ACCUMULATING_STRAIN -> "개인 기준보다 부담이 높고 수행·소화 저하가 함께 보여 전신 용량을 한 번 조정합니다."
    TrainingState.MALADAPTATION_PATTERN -> "여러 움직임의 수행 저하가 함께 관찰돼 짧은 블록과 용량 조정을 사용합니다."
    TrainingState.UNCERTAIN -> "장기 비교 근거가 부족해 악화로 단정하지 않고 유지·관찰합니다."
    TrainingState.STABLE -> "현재 기록에서 뚜렷한 광범위 악화가 확인되지 않아 유지·관찰합니다."
}
