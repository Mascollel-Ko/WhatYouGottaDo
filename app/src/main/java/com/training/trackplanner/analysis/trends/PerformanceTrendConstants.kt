package com.training.trackplanner.analysis.trends

internal object PerformanceTrendConstants {
    const val DEFAULT_WEEK_COUNT = 8
    const val EXTENDED_WEEK_COUNT = 12
    const val FORECAST_WEEKS = 4
    const val FORECAST_MIN_POINTS = 4
    const val SCATTER_MIN_POINTS = 8
    const val TREND_STABLE_DELTA = 4.0
    const val EPSILON = 0.0001

    const val STANDARD_MIN = 50.0
    const val STANDARD_MAX = 160.0
    const val FATIGUE_MIN = 50.0
    const val FATIGUE_MAX = 170.0
    const val PERCENTILE_MAX = 150.0
    const val Z_SCORE_WEIGHT = 15.0

    const val BODYWEIGHT_LOAD_FACTOR = 0.65
    const val DEFAULT_BODYWEIGHT_PROXY = 45.0
    const val DEFAULT_BODYWEIGHT_SET_PROXY = 20.0
    const val DRILL_DENSITY_FACTOR = 1.0
    const val TIMED_DRILL_PROXY_PER_MINUTE = 30.0
    const val RANGE_MULTIPLIER = 1.28

    const val STRENGTH_PERFORMANCE_INTENSITY_WEIGHT = 0.50
    const val STRENGTH_PERFORMANCE_VOLUME_WEIGHT = 0.40
    const val STRENGTH_PERFORMANCE_EFFICIENCY_WEIGHT = 0.10

    const val STRENGTH_VOLUME_VOLUME_SCORE_WEIGHT = 0.75
    const val STRENGTH_VOLUME_EFFECTIVE_SET_WEIGHT = 0.25
    const val STRENGTH_EFFICIENCY_SCORE_WEIGHT = 0.70
    const val STRENGTH_EFFICIENCY_SAME_LOAD_WEIGHT = 0.30

    const val BADMINTON_COURT_WEIGHT = 0.60
    const val BADMINTON_FOOTWORK_WEIGHT = 0.25
    const val BADMINTON_SUPPORT_WEIGHT = 0.15

    const val FATIGUE_AVERAGE_WEIGHT = 0.60
    const val FATIGUE_MAX_WEIGHT = 0.25
    const val FATIGUE_RECOVERY_WEIGHT = 0.15

    fun exerciseStrengthWeight(trainingRole: String, movementCategory: String): Double =
        when (trainingRole) {
            "MAIN_STRENGTH" -> 1.00
            "SECONDARY_STRENGTH" -> 0.70
            "ACCESSORY" -> 0.35
            "POWER" -> 0.40
            "PREHAB", "MOBILITY", "RECOVERY" -> 0.0
            else -> if (movementCategory == "HYPERTROPHY") 0.45 else 0.25
        }

    fun volumeEligibilityWeight(trainingRole: String, movementCategory: String): Double =
        when {
            trainingRole == "MAIN_STRENGTH" -> 1.00
            trainingRole == "SECONDARY_STRENGTH" -> 0.85
            movementCategory == "HYPERTROPHY" -> 0.85
            trainingRole == "ACCESSORY" -> 0.60
            trainingRole == "POWER" -> 0.40
            trainingRole in setOf("PREHAB", "MOBILITY", "RECOVERY") -> 0.0
            else -> 0.20
        }

    fun badmintonSupportWeight(transferStrength: String): Double =
        when (transferStrength) {
            "DIRECT" -> 1.00
            "SUPPORTIVE" -> 0.60
            "GENERAL" -> 0.25
            else -> 0.0
        }

    fun badmintonIntensityFactor(rpe: Double?): Double =
        when {
            rpe == null -> 1.0
            rpe <= 6.0 -> 0.90
            rpe < 8.0 -> 1.00
            rpe < 9.0 -> 1.05
            rpe < 10.0 -> 1.10
            else -> 1.15
        }
}
