package com.training.trackplanner.data

import java.util.Locale

internal data class ProgramSelectedExerciseScoreAdjustment(
    val score: Double,
    val selectedMainBoostApplied: Boolean,
    val captainChairPenaltyApplied: Boolean
)

internal class ProgramSelectedExerciseScorePolicy {
    fun adjust(score: Double, candidate: ProgramCandidate): ProgramSelectedExerciseScoreAdjustment {
        val boosted = isSelectedMainExercise(candidate)
        val penalized = isCaptainChairLegRaise(candidate)
        val multiplier =
            (if (boosted) SELECTED_MAIN_EXERCISE_MULTIPLIER else 1.0) *
                (if (penalized) CAPTAIN_CHAIR_LEG_RAISE_MULTIPLIER else 1.0)
        return ProgramSelectedExerciseScoreAdjustment(
            score = score * multiplier,
            selectedMainBoostApplied = boosted,
            captainChairPenaltyApplied = penalized
        )
    }

    private fun isSelectedMainExercise(candidate: ProgramCandidate): Boolean {
        val stableKey = candidate.exercise.stableKey.normalizedIdentity()
        if (stableKey in SELECTED_MAIN_STABLE_KEYS) return true
        val metadataKey = candidate.metadata?.stableKey.orEmpty().normalizedIdentity()
        if (metadataKey in SELECTED_MAIN_STABLE_KEYS) return true
        return candidate.exercise.name.normalizedName() in SELECTED_MAIN_NAMES ||
            candidate.metadata?.exerciseName.orEmpty().normalizedName() in SELECTED_MAIN_NAMES
    }

    private fun isCaptainChairLegRaise(candidate: ProgramCandidate): Boolean {
        val stableKey = candidate.exercise.stableKey.normalizedIdentity()
        if (stableKey in CAPTAIN_CHAIR_STABLE_KEYS) return true
        val metadataKey = candidate.metadata?.stableKey.orEmpty().normalizedIdentity()
        if (metadataKey in CAPTAIN_CHAIR_STABLE_KEYS) return true
        return candidate.exercise.name.normalizedName() in CAPTAIN_CHAIR_NAMES ||
            candidate.metadata?.exerciseName.orEmpty().normalizedName() in CAPTAIN_CHAIR_NAMES
    }

    private fun String.normalizedIdentity(): String =
        trim().lowercase(Locale.US)

    private fun String.normalizedName(): String =
        trim()
            .lowercase(Locale.US)
            .replace(Regex("[\\s_-]+"), " ")

    private companion object {
        const val SELECTED_MAIN_EXERCISE_MULTIPLIER = 1.40
        const val CAPTAIN_CHAIR_LEG_RAISE_MULTIPLIER = 0.70

        val SELECTED_MAIN_STABLE_KEYS = setOf(
            "barbell_back_squat",
            "barbell_deadlift",
            "pull_up",
            "ex_32219f7a",
            "ex_8e1b313e"
        )

        val SELECTED_MAIN_NAMES = setOf(
            "squat",
            "back squat",
            "deadlift",
            "pull up",
            "pull-up",
            "overhead press",
            "half kneeling one arm press",
            "half-kneeling one-arm press"
        )

        val CAPTAIN_CHAIR_STABLE_KEYS = setOf(
            "ex_a345e30b",
            "captain_chair_leg_raise"
        )

        val CAPTAIN_CHAIR_NAMES = setOf(
            "captain chair leg raise"
        )
    }
}
