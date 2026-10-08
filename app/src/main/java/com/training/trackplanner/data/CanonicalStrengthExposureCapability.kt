package com.training.trackplanner.data

/**
 * Canonical capability boundary for Strength exposure. This is deliberately independent of
 * legacy Strength analysis tokens, progression groups, program slots, and exercise families.
 */
object CanonicalStrengthExposureCapability {
    const val policyProvenance = "USER_APPROVED_PROJECT_POLICY"
    const val policyVersion = "C32_STRENGTH_EXPOSURE_V1"

    val approvedStableKeys: Set<String> = setOf(
        "barbell_back_squat",
        "ex_c5043892",
        "barbell_deadlift",
        "ex_e41f4c2b",
        "ex_e41e8dcf",
        "barbell_bench_press",
        "ex_3a7d3eda",
        "ex_32219f7a",
        "ex_79f3bdbe",
        "ex_bb4b4276"
    )

    /** Exact approved variants share one weekly seed budget per movement anchor. */
    enum class MovementAnchor {
        SQUAT,
        DEADLIFT,
        WEIGHTED_VERTICAL_PULL,
        HORIZONTAL_PRESS,
        VERTICAL_PRESS
    }

    private val stableKeysByMovementAnchor: Map<MovementAnchor, Set<String>> = mapOf(
        MovementAnchor.SQUAT to setOf("barbell_back_squat", "ex_c5043892"),
        MovementAnchor.DEADLIFT to setOf("barbell_deadlift"),
        MovementAnchor.WEIGHTED_VERTICAL_PULL to setOf("ex_e41f4c2b", "ex_e41e8dcf"),
        MovementAnchor.HORIZONTAL_PRESS to setOf("barbell_bench_press", "ex_3a7d3eda"),
        MovementAnchor.VERTICAL_PRESS to setOf("ex_32219f7a", "ex_79f3bdbe", "ex_bb4b4276")
    )

    fun movementAnchor(stableKey: String): MovementAnchor? =
        stableKeysByMovementAnchor.entries.firstOrNull { stableKey in it.value }?.key

    fun approvedStableKeys(anchor: MovementAnchor): Set<String> = stableKeysByMovementAnchor.getValue(anchor)

    fun strengthPossible(stableKey: String): Boolean = stableKey in approvedStableKeys

    fun strengthExposureEligible(stableKey: String, reps: Int): Boolean =
        strengthPossible(stableKey) && reps in 1..6
}
