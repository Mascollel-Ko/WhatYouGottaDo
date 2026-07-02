package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetadataOverrideBackupMapperTest {
    @Test
    fun builtInWithoutOverrideExportsSeedMetadata() {
        val seed = exercise("deadlift", "HAMSTRING|GLUTE|ERECTOR_SPINAE")
        val staleDbRow = seed.copy(primaryMuscles = "BROKEN")

        val exported = ExerciseMetadataOverrideBackupMapper.exportExercises(
            exercises = listOf(staleDbRow),
            seedByStableKey = mapOf("deadlift" to seed),
            runtimeMetadata = emptyList()
        ).single()

        assertEquals("HAMSTRING|GLUTE|ERECTOR_SPINAE", exported.primaryMuscles)
    }

    @Test
    fun builtInWithOverrideExportsDbMetadataAndRuntimeRow() {
        val seed = exercise("deadlift", "HAMSTRING|GLUTE|ERECTOR_SPINAE")
        val override = seed.copy(primaryMuscles = "HAMSTRING|GLUTE|ERECTOR_SPINAE|quadriceps")
        val runtimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(override)

        val exported = ExerciseMetadataOverrideBackupMapper.exportExercises(
            exercises = listOf(override),
            seedByStableKey = mapOf("deadlift" to seed),
            runtimeMetadata = listOf(runtimeMetadata)
        ).single()

        assertEquals("HAMSTRING|GLUTE|ERECTOR_SPINAE|quadriceps", exported.primaryMuscles)
    }

    @Test
    fun overrideKeysNormalizeWhitespaceAndCase() {
        val keys = ExerciseMetadataOverrideBackupMapper.overrideKeys(
            listOf(RuntimeExerciseMetadataDefaults.forIdentity(" DeadLift ", "Deadlift"))
        )

        assertTrue(ExerciseMetadataOverrideBackupMapper.hasOverride("deadlift", keys))
    }

    private fun exercise(stableKey: String, primaryMuscles: String): Exercise =
        Exercise(
            name = "Deadlift",
            category = "Strength",
            stableKey = stableKey,
            primaryMuscles = primaryMuscles
        )
}
