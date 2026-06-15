package com.training.trackplanner.data

import java.util.Locale

internal fun Exercise.withInferredPlanningMetadata(): Exercise {
    val kind = resolvedActivityKind()
    val eligibility = resolvedPlanningEligibility(kind)
    return copy(
        activityKind = kind.name,
        planningEligibility = eligibility.name
    )
}

internal fun Exercise.withFatigueOnlyPlanningMetadata(): Exercise {
    val kind = resolvedActivityKind()
    return copy(
        activityKind = kind.name,
        planningEligibility = PlanningEligibility.FATIGUE_ONLY.name
    )
}

internal fun Exercise.isProgramSelectableExercise(): Boolean =
    isActive &&
        resolvedActivityKind() == ActivityKind.TRAINING_EXERCISE &&
        resolvedPlanningEligibility() == PlanningEligibility.PROGRAM_SELECTABLE

internal fun Exercise.resolvedActivityKind(): ActivityKind {
    activityKind.enumValueOrNull<ActivityKind>()?.let { return it }

    val tokens = planningTokens()
    return when {
        ActivityKind.DAILY_METRIC_ONLY.name in tokens -> ActivityKind.DAILY_METRIC_ONLY
        ActivityKind.MATCH_RECORD.name in tokens -> ActivityKind.MATCH_RECORD
        ActivityKind.SPORT_SESSION.name in tokens -> ActivityKind.SPORT_SESSION
        category == "스포츠" -> ActivityKind.SPORT_SESSION
        hasSportSessionNameFallback() -> ActivityKind.SPORT_SESSION
        else -> ActivityKind.TRAINING_EXERCISE
    }
}

internal fun Exercise.resolvedPlanningEligibility(
    resolvedKind: ActivityKind = resolvedActivityKind()
): PlanningEligibility {
    planningEligibility.enumValueOrNull<PlanningEligibility>()?.let { return it }

    return when (resolvedKind) {
        ActivityKind.TRAINING_EXERCISE -> {
            if (hasImportedAggregateName()) {
                PlanningEligibility.FATIGUE_ONLY
            } else {
                PlanningEligibility.PROGRAM_SELECTABLE
            }
        }
        ActivityKind.SPORT_SESSION,
        ActivityKind.MATCH_RECORD -> PlanningEligibility.FATIGUE_ONLY
        ActivityKind.DAILY_METRIC_ONLY -> PlanningEligibility.ANALYSIS_ONLY
    }
}

private fun Exercise.planningTokens(): Set<String> =
    listOf(
        activityKind,
        planningEligibility,
        movementPattern,
        movementCategory,
        forceType,
        trainingRole,
        analysisEligibility
    )
        .flatMap { value -> value.split(',', '|', '/', ';', ' ') }
        .map { value -> value.trim().uppercase(Locale.US) }
        .filter { value -> value.isNotBlank() }
        .toSet()

// One-time defensive fallback for legacy seed/import rows that predate activityKind.
// Program generation still uses the structured activityKind/planningEligibility result.
private fun Exercise.hasSportSessionNameFallback(): Boolean {
    val normalized = name.lowercase(Locale.ROOT)
    return listOf(
        "badminton match",
        "badminton game",
        "badminton session",
        "sport session",
        "match record",
        "배드민턴 경기 기록",
        "배드민턴 경기",
        "배드민턴 게임",
        "배드민턴 세션",
        "축구 경기",
        "농구 경기",
        "러닝 경기",
        "러닝 세션",
        "테니스 경기"
    ).any { marker -> normalized.contains(marker.lowercase(Locale.ROOT)) }
}

private fun Exercise.hasImportedAggregateName(): Boolean =
    name.startsWith("CSV 복원")

private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
    trim()
        .uppercase(Locale.US)
        .takeIf { value -> value.isNotBlank() }
        ?.let { value -> enumValues<T>().firstOrNull { enumValue -> enumValue.name == value } }
