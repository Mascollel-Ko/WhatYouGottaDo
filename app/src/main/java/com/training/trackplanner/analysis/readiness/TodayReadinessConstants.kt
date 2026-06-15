package com.training.trackplanner.analysis.readiness

internal object TodayReadinessConstants {
    const val BODYWEIGHT_PROXY_LOAD = 20.0
    const val DRILL_INTENSITY_PROXY_LOAD_PER_MINUTE = 30.0
    const val LOCAL_PRIMARY_SHARE = 1.0
    const val LOCAL_SECONDARY_SHARE = 0.45
    const val AXIAL_BACK_SHARE = 0.18
    const val DECELERATION_LOWER_SHARE = 0.18
    const val OVERHEAD_SHOULDER_SHARE = 0.22
    const val GRIP_FOREARM_SHARE = 1.0

    const val EWMA_ALPHA = 0.15
    const val LOW_STD_FLOOR = 0.001
    const val CONSERVATIVE_TOLERANCE = 1.0
    const val MAX_SINGLE_RUN_UPWARD_ADJUSTMENT = 0.05
    const val MAX_SINGLE_RUN_DOWNWARD_ADJUSTMENT = 0.05
    const val SUCCESSFUL_EXPOSURE_BONUS = 0.02
    const val FAILED_EXPOSURE_PENALTY = 0.025
    const val LONG_LOW_LOAD_DECAY = 0.02

    val majorCategories = setOf(
        FatigueCategoryKey.SYSTEMIC,
        FatigueCategoryKey.NEURAL_HEAVY,
        FatigueCategoryKey.NEURAL_SPEED,
        FatigueCategoryKey.BADMINTON_COURT,
        FatigueCategoryKey.DECELERATION
    )

    val decayCurves: Map<String, List<Double>> = mapOf(
        "MINIMAL" to listOf(1.00, 0.15),
        "SHORT" to listOf(1.00, 0.50, 0.20),
        "MEDIUM" to listOf(1.00, 0.65, 0.40, 0.20),
        "LONG" to listOf(1.00, 0.80, 0.55, 0.35, 0.20),
        "VERY_LONG" to listOf(1.00, 0.90, 0.70, 0.50, 0.35, 0.20)
    )

    fun rpeModifier(rpe: Double?): Double =
        when {
            rpe == null -> 1.0
            rpe <= 6.0 -> 0.85
            rpe < 8.0 -> 0.95
            rpe < 9.0 -> 1.0
            rpe < 10.0 -> 1.10
            else -> 1.20
        }

    fun localRpeModifier(rpeModifier: Double): Double =
        1.0 + ((rpeModifier - 1.0) * 0.60)

    fun speedRpeModifier(rpeModifier: Double): Double =
        1.0 + ((rpeModifier - 1.0) * 0.35)

    fun badmintonTransferBonus(strength: String, hasCourtMovement: Boolean): Double {
        val base = when (strength) {
            "DIRECT" -> 0.25
            "SUPPORTIVE" -> 0.15
            "GENERAL" -> 0.08
            else -> 0.0
        }
        return if (hasCourtMovement) base else base * 0.5
    }
}
