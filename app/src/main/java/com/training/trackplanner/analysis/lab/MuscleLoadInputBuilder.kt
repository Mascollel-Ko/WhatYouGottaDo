package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MuscleGroupKeyNormalizer
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry

internal object MuscleLoadInputBuilder {
    fun isMainSquat(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("squat", "back_squat", "barbell_squat", "barbell_back_squat")) return true
            if ("back_squat" in key || "barbell_squat" in key) return true
            return false
        }
        val name = entry.exerciseName.lowercase()
        return ("스쿼트" in name || "squat" in name) &&
            listOf("런지", "lunge", "레그 프레스", "leg press", "스플릿", "split", "불가리안", "bulgarian", "점프", "jump", "고블릿", "goblet", "프론트", "front")
                .none { token -> token in name }
    }

    fun isMainBenchPress(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("bench_press", "barbell_bench_press", "flat_barbell_bench_press")) return true
            if ("bench_press" in key && listOf("dumbbell", "incline", "decline", "close_grip", "floor").none { token -> token in key }) return true
            return false
        }
        val name = entry.exerciseName.lowercase()
        return ("벤치프레스" in name || "벤치 프레스" in name || "bench press" in name) &&
            listOf("덤벨", "dumbbell", "인클라인", "incline", "디클라인", "decline", "클로즈", "close", "플로어", "floor", "플라이", "fly")
                .none { token -> token in name }
    }

    fun isMainDeadlift(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("deadlift", "barbell_deadlift", "conventional_deadlift")) return true
            return ("deadlift" in key) && listOf("rdl", "romanian", "stiff", "good_morning", "swing")
                .none { token -> token in key }
        }
        val name = entry.exerciseName.lowercase()
        return ("데드리프트" in name || "deadlift" in name) &&
            listOf("루마니안", "rdl", "romanian", "스티프", "stiff", "굿모닝", "good morning", "스윙", "swing")
                .none { token -> token in name }
    }

    fun contributions(
        exercise: Exercise?,
        entry: WorkoutEntry,
        runtimeMetadata: RuntimeExerciseMetadata? = null
    ): Map<MuscleBucket, Double> {
        if (exercise != null) {
            val fromMetadata = mutableMapOf<MuscleBucket, Double>()
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

    private fun bucketForToken(token: String): MuscleBucket? = when (token) {
        "QUADRICEPS", "RECTUS_FEMORIS" -> MuscleBucket.QUADS
        "HAMSTRING" -> MuscleBucket.HAMSTRINGS
        "GLUTE", "GLUTE_MEDIUS" -> MuscleBucket.GLUTES
        "CALF", "TIBIALIS" -> MuscleBucket.CALVES
        "HIP_ADDUCTOR" -> MuscleBucket.ADDUCTOR_ABDUCTOR
        "ERECTOR_SPINAE" -> MuscleBucket.POSTERIOR_CHAIN_ERECTORS
        "CHEST", "UPPER_CHEST" -> MuscleBucket.CHEST
        "BACK", "LAT", "RHOMBOID", "TRAPEZIUS", "LOWER_TRAP" -> MuscleBucket.BACK_LATS
        "SHOULDER", "ANTERIOR_DELTOID", "LATERAL_DELTOID", "REAR_DELT", "ROTATOR_CUFF", "SCAPULAR_STABILIZERS" ->
            MuscleBucket.SHOULDERS
        "BICEPS" -> MuscleBucket.BICEPS
        "TRICEPS" -> MuscleBucket.TRICEPS
        "FOREARM", "GRIP" -> MuscleBucket.FOREARM_GRIP
        "CORE", "DEEP_CORE" -> MuscleBucket.ANTERIOR_CORE
        "OBLIQUE" -> MuscleBucket.LATERAL_CORE
        "ROTATION_CORE" -> MuscleBucket.ROTATION_CORE
        else -> null
    }

    private fun fallbackContributions(
        exercise: Exercise?,
        entry: WorkoutEntry,
        runtimeMetadata: RuntimeExerciseMetadata?
    ): Map<MuscleBucket, Double> {
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
                MuscleBucket.QUADS to 1.0,
                MuscleBucket.GLUTES to 0.5,
                MuscleBucket.HAMSTRINGS to 0.25,
                MuscleBucket.POSTERIOR_CHAIN_ERECTORS to 0.25
            )
            isMainDeadlift(exercise, entry) -> mapOf(
                MuscleBucket.POSTERIOR_CHAIN_ERECTORS to 1.0,
                MuscleBucket.GLUTES to 0.75,
                MuscleBucket.HAMSTRINGS to 0.75,
                MuscleBucket.FOREARM_GRIP to 0.25
            )
            "bench" in text || "벤치" in text -> mapOf(
                MuscleBucket.CHEST to 1.0,
                MuscleBucket.TRICEPS to 0.5,
                MuscleBucket.SHOULDERS to 0.25
            )
            "pull_up" in text || "pull-up" in text || "풀업" in text || "턱걸이" in text -> mapOf(
                MuscleBucket.BACK_LATS to 1.0,
                MuscleBucket.BICEPS to 0.5,
                MuscleBucket.FOREARM_GRIP to 0.5
            )
            "row" in text || "로우" in text -> mapOf(
                MuscleBucket.BACK_LATS to 1.0,
                MuscleBucket.BICEPS to 0.5,
                MuscleBucket.FOREARM_GRIP to 0.25
            )
            ("overhead" in text || "shoulder_press" in text || "숄더" in text) && "press" in text ||
                "오버헤드프레스" in text -> mapOf(
                    MuscleBucket.SHOULDERS to 1.0,
                    MuscleBucket.TRICEPS to 0.5,
                    MuscleBucket.ANTERIOR_CORE to 0.25
                )
            "pallof" in text || "팔로프" in text || "anti_rotation" in text -> mapOf(
                MuscleBucket.ROTATION_CORE to 1.0,
                MuscleBucket.LATERAL_CORE to 0.5
            )
            "russian" in text || "트위스트" in text || "rotation_core" in text -> mapOf(
                MuscleBucket.ROTATION_CORE to 1.0,
                MuscleBucket.ANTERIOR_CORE to 0.5,
                MuscleBucket.LATERAL_CORE to 0.5
            )
            else -> emptyMap()
        }
    }
}
