package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MuscleGroupKeyNormalizer

internal object MuscleLoadInputBuilder {
    fun contributions(exercise: Exercise?): Map<MuscleBucket, Double> {
        exercise ?: return emptyMap()
        val result = mutableMapOf<MuscleBucket, Double>()
        MuscleGroupKeyNormalizer.canonicalKeys(exercise.primaryMuscles).forEach { token ->
            bucketForToken(token)?.let { bucket -> result.merge(bucket, 1.0, ::maxOf) }
        }
        MuscleGroupKeyNormalizer.canonicalKeys(exercise.secondaryMuscles).forEach { token ->
            bucketForToken(token)?.let { bucket -> result.merge(bucket, 0.5, ::maxOf) }
        }
        return result
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
        else -> null
    }
}
