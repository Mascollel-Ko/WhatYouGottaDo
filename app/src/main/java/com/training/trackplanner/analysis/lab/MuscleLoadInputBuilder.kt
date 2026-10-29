package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.data.CanonicalMuscleLookup
import com.training.trackplanner.data.CanonicalMuscleLookupKind
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MuscleGroupKeyNormalizer

/**
 * MUS-08: canonical muscle-role coefficients drive live graph attribution.
 * The no-lookup path is temporary legacy-import compatibility, not the live pipeline.
 */
internal object MuscleLoadInputBuilder {
    fun contributions(exercise: Exercise?, canonical: CanonicalMuscleLookup? = null): Map<MuscleBucket, Double> {
        exercise ?: return emptyMap()
        if (canonical == null) return legacyCompatibility(exercise)
        return when (canonical.kind) {
            CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS ->
                canonical.relations
                    .asSequence()
                    .filter { row ->
                        row.role in setOf(CanonicalMuscleRole.PRIMARY, CanonicalMuscleRole.SECONDARY) &&
                            (row.coefficient ?: 0.0) > 0.0
                    }
                    .mapNotNull { row ->
                        canonicalBucket(row.muscleCode)?.let { bucket -> bucket to requireNotNull(row.coefficient) }
                    }
                    .fold(mutableMapOf<MuscleBucket, Double>()) { result, (bucket, coefficient) ->
                        result.apply { merge(bucket, coefficient, ::maxOf) }
                    }
            CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED -> legacyCompatibility(exercise)
            CanonicalMuscleLookupKind.SELECTABLE_WITHOUT_RELATIONS,
            CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED,
            CanonicalMuscleLookupKind.UNKNOWN_UNMAPPED -> emptyMap()
        }
    }

    /** Only canonical MUSCLE_GROUP codes; core is tracked by the separate core series. */
    private fun canonicalBucket(code: String): MuscleBucket? = when (code) {
        "QUADS" -> MuscleBucket.QUADS
        "HAMSTRINGS" -> MuscleBucket.HAMSTRINGS
        "GLUTES", "GLUTE_MEDIUS" -> MuscleBucket.GLUTES
        "CALVES" -> MuscleBucket.CALVES
        "HIP_ADDUCTOR", "ADDUCTOR_ABDUCTOR" -> MuscleBucket.ADDUCTOR_ABDUCTOR
        "POSTERIOR_CHAIN_ERECTORS" -> MuscleBucket.POSTERIOR_CHAIN_ERECTORS
        "CHEST" -> MuscleBucket.CHEST
        "BACK_LATS" -> MuscleBucket.BACK_LATS
        "SHOULDERS" -> MuscleBucket.SHOULDERS
        "BICEPS" -> MuscleBucket.BICEPS
        "TRICEPS" -> MuscleBucket.TRICEPS
        "FOREARM_GRIP" -> MuscleBucket.FOREARM_GRIP
        "ANTERIOR_CORE", "LATERAL_CORE" -> null
        else -> null
    }

    private fun legacyCompatibility(exercise: Exercise): Map<MuscleBucket, Double> {
        val result = mutableMapOf<MuscleBucket, Double>()
        MuscleGroupKeyNormalizer.canonicalKeys(exercise.primaryMuscles).forEach { token ->
            legacyBucket(token)?.let { result.merge(it, 1.0, ::maxOf) }
        }
        MuscleGroupKeyNormalizer.canonicalKeys(exercise.secondaryMuscles).forEach { token ->
            legacyBucket(token)?.let { result.merge(it, 0.5, ::maxOf) }
        }
        return result
    }

    private fun legacyBucket(token: String): MuscleBucket? = when (token) {
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
