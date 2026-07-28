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

    fun isSelectedMainExercise(candidate: ProgramCandidate): Boolean {
        val stableKey = candidate.exercise.stableKey.normalizedIdentity()
        if (stableKey in SELECTED_MAIN_STABLE_KEYS) return true
        val metadataKey = candidate.metadata?.stableKey.orEmpty().normalizedIdentity()
        return metadataKey in SELECTED_MAIN_STABLE_KEYS
    }

    fun isCaptainChairLegRaise(candidate: ProgramCandidate): Boolean {
        val stableKey = candidate.exercise.stableKey.normalizedIdentity()
        if (stableKey in CAPTAIN_CHAIR_STABLE_KEYS) return true
        val metadataKey = candidate.metadata?.stableKey.orEmpty().normalizedIdentity()
        return metadataKey in CAPTAIN_CHAIR_STABLE_KEYS
    }

    fun matchesSelectedMainStableKey(candidate: ProgramCandidate, stableKey: String): Boolean {
        val normalized = stableKey.normalizedIdentity()
        return candidate.exercise.stableKey.normalizedIdentity() == normalized ||
            candidate.metadata?.stableKey.orEmpty().normalizedIdentity() == normalized
    }

    fun isSelectedMainStableKey(stableKey: String): Boolean =
        stableKey.normalizedIdentity() in SELECTED_MAIN_STABLE_KEYS

    fun isCaptainChairStableKey(stableKey: String): Boolean =
        stableKey.normalizedIdentity() in CAPTAIN_CHAIR_STABLE_KEYS

    fun selectedMainStableKeys(): List<String> = SELECTED_MAIN_STABLE_KEYS.toList()

    private fun String.normalizedIdentity(): String =
        trim().lowercase(Locale.US)

    private companion object {
        const val SELECTED_MAIN_EXERCISE_MULTIPLIER = 1.40
        const val CAPTAIN_CHAIR_LEG_RAISE_MULTIPLIER = 0.70

        val SELECTED_MAIN_STABLE_KEYS = setOf(
            "barbell_back_squat",
            "barbell_deadlift",
            "pull_up",
            "ex_32219f7a",
            "half_kneeling_single_arm_dumbbell_press",
            "half_kneeling_single_arm_kettlebell_press"
        )

        val CAPTAIN_CHAIR_STABLE_KEYS = setOf(
            "ex_a345e30b",
            "captain_chair_leg_raise"
        )
    }
}
