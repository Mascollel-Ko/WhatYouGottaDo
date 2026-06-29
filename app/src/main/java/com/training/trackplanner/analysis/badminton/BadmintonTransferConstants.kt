package com.training.trackplanner.analysis.badminton

internal object BadmintonTransferConstants {
    const val RECENT_WINDOW_DAYS = 7L
    const val BASELINE_WINDOW_DAYS = 28L
    const val EPSILON = 0.0001

    const val DEFAULT_TIMED_DRILL_UNIT_SECONDS = 30.0
    const val MAX_INTENSITY_FACTOR = 2.0
    const val LOW_AXIS_THRESHOLD = 0.12
    const val TOP_EXERCISE_LIMIT = 5
    const val CANDIDATE_LIMIT = 5

    val recommendationPriority = listOf(
        BadmintonTransferAxis.DECELERATION_LANDING,
        BadmintonTransferAxis.UNILATERAL_STABILITY,
        BadmintonTransferAxis.LATERAL_MOVEMENT,
        BadmintonTransferAxis.ROTATION_CONTROL,
        BadmintonTransferAxis.RACKET_SUPPORT,
        BadmintonTransferAxis.AEROBIC_FOOTWORK,
        BadmintonTransferAxis.LOW_FATIGUE_CONTROL
    )

    fun transferWeight(type: BadmintonTransferType): Double =
        when (type) {
            BadmintonTransferType.DIRECT -> 1.0
            BadmintonTransferType.SUPPORTIVE -> 0.6
            BadmintonTransferType.GENERAL_STRENGTH -> 0.25
            BadmintonTransferType.LOW -> 0.1
            BadmintonTransferType.NONE -> 0.0
        }

    fun rpeFactor(rpe: Double?): Double =
        when {
            rpe == null -> 1.0
            rpe <= 6.0 -> 0.90
            rpe < 8.0 -> 1.00
            rpe < 9.0 -> 1.05
            rpe < 10.0 -> 1.10
            else -> 1.15
        }

    fun intensityFactor(averageWeightKg: Double): Double =
        (1.0 + averageWeightKg / 100.0).coerceIn(1.0, MAX_INTENSITY_FACTOR)

    fun shareThreshold(axis: BadmintonTransferAxis): Double = LOW_AXIS_THRESHOLD
}
