package com.training.trackplanner.data

import java.util.Locale

internal object ExerciseMetadataOverrideBackupMapper {
    fun overrideKeys(metadata: List<RuntimeExerciseMetadata>): Set<String> =
        metadata.map { row -> overrideKey(row.stableKey) }.toSet()

    fun hasOverride(stableKey: String, overrideKeys: Set<String>): Boolean =
        overrideKey(stableKey) in overrideKeys

    fun overrideKey(stableKey: String): String =
        stableKey.trim().lowercase(Locale.ROOT)
}
