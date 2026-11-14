package com.training.trackplanner.data

/**
 * B-Coach progression authority is independent of session membership.
 * An automatically inferred link is not permission to increase its load.
 *
 * This policy selects a calculation route; it does not estimate a load.
 * A route must never be treated as evidence that a compatible model exists.
 */
internal object BCoachProgressionPolicy {
    enum class Route { CANONICAL_FOUR, GENERAL_MAIN, FIXED_LOAD }

    private val canonicalFour = setOf(
        "barbell_back_squat",
        "barbell_deadlift",
        "barbell_bench_press",
        "ex_e41f4c2b"
    )

    fun route(
        exerciseStableKey: String,
        plannerRole: ProgressionRole,
        userRoleOverride: ProgressionRole,
        linkMode: ProgressionLinkMode
    ): Route {
        if (exerciseStableKey in canonicalFour) return Route.CANONICAL_FOUR
        if (userRoleOverride == ProgressionRole.ASSISTANCE) return Route.FIXED_LOAD
        if (userRoleOverride == ProgressionRole.MAIN ||
            plannerRole == ProgressionRole.MAIN ||
            linkMode == ProgressionLinkMode.EXISTING
        ) return Route.GENERAL_MAIN
        // SEPARATE creates an independent chain; it does not request progression.
        return Route.FIXED_LOAD
    }
}
