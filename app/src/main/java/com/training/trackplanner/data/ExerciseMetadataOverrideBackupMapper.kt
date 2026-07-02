package com.training.trackplanner.data

import java.util.Locale

internal object ExerciseMetadataOverrideBackupMapper {
    fun exportExercises(
        exercises: List<Exercise>,
        seedByStableKey: Map<String, Exercise>,
        runtimeMetadata: List<RuntimeExerciseMetadata>
    ): List<Exercise> {
        val runtimeOverrideKeys = overrideKeys(runtimeMetadata)
        return exercises.map { exercise ->
            if (hasOverride(exercise.stableKey, runtimeOverrideKeys)) {
                exercise
            } else {
                ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(exercise, seedByStableKey)
            }
        }
    }

    fun overrideKeys(metadata: List<RuntimeExerciseMetadata>): Set<String> =
        metadata.map { row -> overrideKey(row.stableKey) }.toSet()

    fun hasOverride(stableKey: String, overrideKeys: Set<String>): Boolean =
        overrideKey(stableKey) in overrideKeys

    fun overrideKey(stableKey: String): String =
        stableKey.trim().lowercase(Locale.ROOT)
}
