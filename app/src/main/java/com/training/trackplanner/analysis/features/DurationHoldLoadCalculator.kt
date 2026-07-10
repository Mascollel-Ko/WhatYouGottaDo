package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutSet

object DurationHoldLoadCalculator {
    fun holdLoad(exercise: Exercise, set: WorkoutSet, rpe: Double?): Double? =
        holdLoadOrNull(
            stableKey = exercise.stableKey,
            displayName = exercise.name,
            movementPattern = exercise.movementPattern,
            movementCategory = exercise.movementCategory,
            equipment = exercise.equipment.ifBlank { exercise.equipmentTags },
            mode = exercise.mode,
            category = exercise.category,
            seconds = set.seconds,
            rpe = set.rpe ?: rpe
        )

    fun holdLoadOrNull(
        stableKey: String,
        displayName: String,
        movementPattern: String = "",
        movementCategory: String = "",
        equipment: String = "",
        mode: String = "",
        category: String = "",
        seconds: Int,
        rpe: Double?
    ): Double? {
        if (seconds <= 0) return null
        val policy = policyFor(stableKey, displayName, movementPattern, movementCategory, equipment, mode, category)
            ?: return null
        return seconds * rpeMultiplier(rpe) * policy.coefficient
    }

    fun policyFor(
        stableKey: String,
        displayName: String,
        movementPattern: String = "",
        movementCategory: String = "",
        equipment: String = "",
        mode: String = "",
        category: String = ""
    ): DurationHoldPolicy? {
        val text = listOf(stableKey, displayName, movementPattern, movementCategory, equipment, mode, category)
            .joinToString(" ")
            .lowercase()
        return when {
            text.hasAny("side_plank", "side plank", "copenhagen_plank", "copenhagen plank", "\uC0AC\uC774\uB4DC \uD50C\uB7AD\uD06C") ->
                DurationHoldPolicy.SIDE_PLANK
            text.hasAny("plank", "\uD50C\uB7AD\uD06C") ->
                DurationHoldPolicy.PLANK
            else -> null
        }
    }

    private fun rpeMultiplier(rpe: Double?): Double = when {
        rpe == null -> 1.0
        rpe <= 6.0 -> 0.85
        rpe < 8.0 -> 1.0
        rpe < 9.0 -> 1.15
        rpe < 10.0 -> 1.30
        else -> 1.45
    }

    private fun String.hasAny(vararg tokens: String): Boolean =
        tokens.any { token -> contains(token, ignoreCase = true) }
}

enum class DurationHoldPolicy(val coefficient: Double) {
    PLANK(1.0),
    SIDE_PLANK(1.0)
}
