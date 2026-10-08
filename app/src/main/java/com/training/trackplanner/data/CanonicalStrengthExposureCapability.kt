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

    fun strengthPossible(stableKey: String): Boolean = stableKey in approvedStableKeys

    fun strengthExposureEligible(stableKey: String, reps: Int): Boolean =
        strengthPossible(stableKey) && reps in 1..6
}
