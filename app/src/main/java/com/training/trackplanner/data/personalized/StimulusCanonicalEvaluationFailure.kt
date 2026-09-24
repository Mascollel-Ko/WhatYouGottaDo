package com.training.trackplanner.data.personalized

/**
 * Machine-readable failures at the optional canonical evaluation boundary.
 *
 * These are deliberately narrower than ordinary planner invariants: only a
 * bounded canonical evaluation that cannot be materialized may be recovered
 * by production orchestration after CONTROL already exists.
 */
internal enum class StimulusCanonicalEvaluationFailureReason {
    NO_EXECUTABLE_PLANNING_DEMAND,
    FINAL_CANONICAL_VALIDATION,
    B6_AUTHORIZATION_FAILURE,
    REGIONAL_AUTHORIZATION_FAILURE,
    MATERIAL_AUTHORIZATION_FAILURE
}

internal class StimulusCanonicalEvaluationFailure(
    val reason: StimulusCanonicalEvaluationFailureReason,
    val detailCode: String? = null,
    cause: Throwable? = null
) : RuntimeException(detailCode ?: reason.name, cause)
