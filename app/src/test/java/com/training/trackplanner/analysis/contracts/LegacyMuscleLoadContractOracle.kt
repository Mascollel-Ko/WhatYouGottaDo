package com.training.trackplanner.analysis.contracts

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MuscleGroupKeyNormalizer
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry

/** Frozen pre-CoreStimulus oracle used only to verify the historical contract asset. */
internal object LegacyMuscleLoadContractOracle {
    fun contributions(
        exercise: Exercise?,
        entry: WorkoutEntry,
        runtimeMetadata: RuntimeExerciseMetadata? = null
    ): Map<String, Double> {
        if (exercise != null) {
            val fromMetadata = mutableMapOf<String, Double>()
            MuscleGroupKeyNormalizer.canonicalKeys(exercise.primaryMuscles).forEach { token ->
                bucketForToken(token)?.let { bucket -> fromMetadata.merge(bucket, 1.0, ::maxOf) }
            }
            MuscleGroupKeyNormalizer.canonicalKeys(exercise.secondaryMuscles).forEach { token ->
                bucketForToken(token)?.let { bucket -> fromMetadata.merge(bucket, 0.5, ::maxOf) }
            }
            if (fromMetadata.isNotEmpty()) return fromMetadata
        }
        return fallbackContributions(exercise, entry, runtimeMetadata)
    }

    private fun bucketForToken(token: String): String? = when (token) {
        "QUADRICEPS", "RECTUS_FEMORIS" -> "QUADS"
        "HAMSTRING" -> "HAMSTRINGS"
        "GLUTE", "GLUTE_MEDIUS" -> "GLUTES"
        "CALF", "TIBIALIS" -> "CALVES"
        "HIP_ADDUCTOR" -> "ADDUCTOR_ABDUCTOR"
        "ERECTOR_SPINAE" -> "POSTERIOR_CHAIN_ERECTORS"
        "CHEST", "UPPER_CHEST" -> "CHEST"
        "BACK", "LAT", "RHOMBOID", "TRAPEZIUS", "LOWER_TRAP" -> "BACK_LATS"
        "SHOULDER", "ANTERIOR_DELTOID", "LATERAL_DELTOID", "REAR_DELT", "ROTATOR_CUFF", "SCAPULAR_STABILIZERS" ->
            "SHOULDERS"
        "BICEPS" -> "BICEPS"
        "TRICEPS" -> "TRICEPS"
        "FOREARM", "GRIP" -> "FOREARM_GRIP"
        "CORE", "DEEP_CORE" -> "ANTERIOR_CORE"
        "OBLIQUE" -> "LATERAL_CORE"
        "ROTATION_CORE" -> "ROTATION_CORE"
        else -> null
    }

    private fun fallbackContributions(
        exercise: Exercise?,
        entry: WorkoutEntry,
        runtimeMetadata: RuntimeExerciseMetadata?
    ): Map<String, Double> {
        val text = listOfNotNull(
            exercise?.stableKey,
            exercise?.movementPattern,
            exercise?.strengthProgressionGroup,
            exercise?.mainLiftGroup,
            runtimeMetadata?.movementFamily,
            runtimeMetadata?.movementSubtype,
            runtimeMetadata?.strengthProgressionGroup,
            runtimeMetadata?.programSlot,
            runtimeMetadata?.redundancyGroup,
            entry.exerciseName
        ).joinToString(" ").lowercase()
        return when {
            isMainSquat(exercise, entry) -> mapOf(
                "QUADS" to 1.0,
                "GLUTES" to 0.5,
                "HAMSTRINGS" to 0.25,
                "POSTERIOR_CHAIN_ERECTORS" to 0.25
            )
            isMainDeadlift(exercise, entry) -> mapOf(
                "POSTERIOR_CHAIN_ERECTORS" to 1.0,
                "GLUTES" to 0.75,
                "HAMSTRINGS" to 0.75,
                "FOREARM_GRIP" to 0.25
            )
            "bench" in text || "벤치" in text -> mapOf(
                "CHEST" to 1.0,
                "TRICEPS" to 0.5,
                "SHOULDERS" to 0.25
            )
            "pull_up" in text || "pull-up" in text || "풀업" in text || "턱걸이" in text -> mapOf(
                "BACK_LATS" to 1.0,
                "BICEPS" to 0.5,
                "FOREARM_GRIP" to 0.5
            )
            "row" in text || "로우" in text -> mapOf(
                "BACK_LATS" to 1.0,
                "BICEPS" to 0.5,
                "FOREARM_GRIP" to 0.25
            )
            (("overhead" in text || "shoulder_press" in text || "숄더" in text) && "press" in text) ||
                "오버헤드프레스" in text -> mapOf(
                    "SHOULDERS" to 1.0,
                    "TRICEPS" to 0.5,
                    "ANTERIOR_CORE" to 0.25
                )
            "pallof" in text || "팔로프" in text || "anti_rotation" in text -> mapOf(
                "ROTATION_CORE" to 1.0,
                "LATERAL_CORE" to 0.5
            )
            "russian" in text || "트위스트" in text || "rotation_core" in text -> mapOf(
                "ROTATION_CORE" to 1.0,
                "ANTERIOR_CORE" to 0.5,
                "LATERAL_CORE" to 0.5
            )
            else -> emptyMap()
        }
    }

    private fun isMainSquat(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            return key in setOf("squat", "back_squat", "barbell_squat", "barbell_back_squat") ||
                "back_squat" in key || "barbell_squat" in key
        }
        val name = entry.exerciseName.lowercase()
        return ("squat" in name) &&
            listOf("lunge", "leg press", "split", "bulgarian", "jump", "goblet", "front").none { it in name }
    }

    private fun isMainDeadlift(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            return key in setOf("deadlift", "barbell_deadlift", "conventional_deadlift") ||
                ("deadlift" in key && listOf("rdl", "romanian", "stiff", "good_morning", "swing").none { it in key })
        }
        val name = entry.exerciseName.lowercase()
        return "deadlift" in name &&
            listOf("rdl", "romanian", "stiff", "good morning", "swing").none { it in name }
    }
}
