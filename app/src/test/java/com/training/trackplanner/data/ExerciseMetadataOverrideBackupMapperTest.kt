package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetadataOverrideBackupMapperTest {
    @Test
    fun fieldPolicyRegistryHasOneAuthoritativeClassificationPerField() {
        assertEquals(
            mapOf(
                ExerciseMetadataFieldPolicy.IDENTITY_STABLE to 1,
                ExerciseMetadataFieldPolicy.CURRENT_CANONICAL_NAME to 4,
                ExerciseMetadataFieldPolicy.USER_OVERRIDE_ELIGIBLE to 93,
                ExerciseMetadataFieldPolicy.USER_STATE to 3,
                ExerciseMetadataFieldPolicy.CURRENT_CANONICAL_SYSTEM_VALUE to 3,
                ExerciseMetadataFieldPolicy.DERIVED_REBUILD to 4,
                ExerciseMetadataFieldPolicy.FULL_SNAPSHOT_FOR_CUSTOM_OR_CATALOGUE_MISSING to 1
            ),
            ExerciseMetadataFieldPolicyRegistry.policyCounts()
        )
        assertEquals(109, ExerciseMetadataFieldPolicyRegistry.fields.size)
    }

    @Test
    fun backupSnapshotUsesEffectiveDatabaseExerciseMetadata() {
        val exercise = exercise("deadlift", "USER_EDITED")
        val snapshot = ExerciseMetadataFieldPolicyRegistry.snapshot(
            ExerciseMetadataSnapshotSource(
                exercise = exercise,
                runtimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(exercise),
                trainingRoles = emptySet(),
                programSlotCapabilities = emptySet()
            )
        )

        assertEquals("USER_EDITED", snapshot.single { it.fieldKey == "exercise.primaryMuscles" }.value)
    }

    @Test
    fun snapshotTokenSetsAreDeterministic() {
        val exercise = exercise("deadlift", "QUADRICEPS|HAMSTRING|QUADRICEPS")
        val snapshot = ExerciseMetadataFieldPolicyRegistry.snapshot(
            ExerciseMetadataSnapshotSource(
                exercise = exercise,
                runtimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(exercise),
                trainingRoles = setOf("POWER", "STRENGTH"),
                programSlotCapabilities = emptySet()
            )
        )

        assertEquals("HAMSTRING|QUADRICEPS", snapshot.single { it.fieldKey == "exercise.primaryMuscles" }.value)
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
