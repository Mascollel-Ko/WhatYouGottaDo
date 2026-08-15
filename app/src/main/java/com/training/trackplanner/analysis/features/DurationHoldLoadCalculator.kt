package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutSet

object DurationHoldLoadCalculator {
    fun holdLoad(exercise: Exercise, set: WorkoutSet, rpe: Double?): Double? =
        holdLoadOrNull(
            stableKey = exercise.stableKey,
            seconds = set.seconds,
            rpe = set.rpe ?: rpe
        )

    fun holdLoadOrNull(
        stableKey: String,
        seconds: Int,
        rpe: Double?
    ): Double? {
        if (seconds <= 0) return null
        val policy = policyFor(stableKey) ?: return null
        return seconds * rpeMultiplier(rpe) * policy.coefficient
    }

    fun policyFor(stableKey: String): DurationHoldPolicy? = DurationHoldProfileAuthority.resolve(stableKey)

    private fun rpeMultiplier(rpe: Double?): Double = when {
        rpe == null -> 1.0
        rpe <= 6.0 -> 0.85
        rpe < 8.0 -> 1.0
        rpe < 9.0 -> 1.15
        rpe < 10.0 -> 1.30
        else -> 1.45
    }
}

enum class DurationHoldPolicy(val coefficient: Double) {
    PLANK(1.0),
    SIDE_PLANK(1.0)
}
