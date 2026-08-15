package com.training.trackplanner.data

import java.util.Locale

internal fun Exercise.isProgramSelectableExercise(): Boolean =
    isActive &&
        resolvedActivityKind() == ActivityKind.TRAINING_EXERCISE &&
        resolvedPlanningEligibility() == PlanningEligibility.PROGRAM_SELECTABLE

internal fun Exercise.resolvedActivityKind(): ActivityKind =
    activityKind.enumValueOrNull<ActivityKind>() ?: ActivityKind.UNKNOWN

internal fun Exercise.resolvedPlanningEligibility(): PlanningEligibility =
    planningEligibility.enumValueOrNull<PlanningEligibility>() ?: PlanningEligibility.UNKNOWN

private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
    trim()
        .uppercase(Locale.US)
        .takeIf(String::isNotBlank)
        ?.let { value -> enumValues<T>().firstOrNull { enumValue -> enumValue.name == value } }
