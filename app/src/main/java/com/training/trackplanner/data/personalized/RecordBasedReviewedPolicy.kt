package com.training.trackplanner.data.personalized

/** Record-Based-owned snapshot of the a53f419 consumed policy. Not a call into another planner. */
internal enum class ReviewedBadmintonCategory {
    STEP, ACCELERATION, DECELERATION, REACTION, ANTI_ROTATION, ROTATION_GENERATION
}

internal object RecordBasedReviewedPolicy {
    // Exact stableKey relations, retaining the former table entry order and membership.
    val badmintonKeys = linkedMapOf(
        ReviewedBadmintonCategory.STEP to setOf("ex_33841b88"),
        ReviewedBadmintonCategory.ACCELERATION to setOf("medicine_ball_three_step_acceleration_throw"),
        ReviewedBadmintonCategory.DECELERATION to setOf("lateral_bound_continuous", "ex_314df428",
            "medicine_ball_three_step_deceleration_throw", "ex_421ba24b", "ex_bc84eb7f"),
        ReviewedBadmintonCategory.REACTION to setOf("ex_c5f4c242", "ex_8e69fc74"),
        ReviewedBadmintonCategory.ANTI_ROTATION to setOf("ex_d5bdffe1", "landmine_anti_rotation",
            "band_pallof_press", "cable_pallof_press", "vipr_rotational_lift", "kettlebell_halo"),
        ReviewedBadmintonCategory.ROTATION_GENERATION to setOf("vipr_chop")
    )

    fun defaultWeekdays(daysPerWeek: Int): List<Int> =
        when (daysPerWeek.coerceIn(3, 7)) {
            3 -> listOf(1, 3, 5)
            4 -> listOf(1, 2, 4, 6)
            5 -> listOf(1, 2, 4, 6, 7)
            6 -> listOf(1, 2, 3, 4, 6, 7)
            else -> (1..7).toList()
        }

    fun defaultSchedule(durationWeeks: Int, daysPerWeek: Int): Map<Int, Set<Int>> {
        val days = defaultWeekdays(daysPerWeek).toSet()
        return (1..durationWeeks.coerceIn(3, 8)).associateWith { days }
    }


    fun badminton(category: ReviewedBadmintonCategory): ReviewedPerformanceGuide =
        when (category) {
            ReviewedBadmintonCategory.STEP,
            ReviewedBadmintonCategory.REACTION -> ReviewedPerformanceGuide(
                setCount = 3,
                reps = 0,
                seconds = 20,
                restSeconds = 60,
                text = "3라운드 x 10-20초"
            )
            ReviewedBadmintonCategory.ACCELERATION,
            ReviewedBadmintonCategory.DECELERATION -> ReviewedPerformanceGuide(
                setCount = 3,
                reps = 5,
                restSeconds = 75,
                text = "3세트 x 5회/side"
            )
            ReviewedBadmintonCategory.ANTI_ROTATION,
            ReviewedBadmintonCategory.ROTATION_GENERATION -> ReviewedPerformanceGuide(
                setCount = 3,
                reps = 10,
                restSeconds = 60,
                text = "3세트 x 8-12회"
            )
        }

}

internal data class ReviewedPerformanceGuide(
    val setCount: Int,
    val reps: Int,
    val seconds: Int = 0,
    val restSeconds: Int,
    val text: String,
    val weightSource: String = "RULE_TABLE"
)
