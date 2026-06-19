package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseStableKeyPreservationTest {
    @Test
    fun editingNamePreservesExistingStableKeyAndIdentityFlags() {
        val existing = exercise(
            id = 7,
            name = "Before",
            stableKey = "user_ex_123e4567-e89b-12d3-a456-426614174000",
            isCustom = true
        ).copy(isActive = false, archivedAt = 55L)
        val edited = exercise(
            id = 0,
            name = "After",
            stableKey = "replacement_should_not_win",
            isCustom = false
        )

        val merged = ExerciseStableKeyPolicy.preserveOnEdit(
            existing = existing,
            edited = edited,
            repairedKey = "unused_repair"
        )

        assertEquals(existing.id, merged.id)
        assertEquals(existing.stableKey, merged.stableKey)
        assertEquals("After", merged.name)
        assertEquals(existing.isCustom, merged.isCustom)
        assertEquals(existing.isActive, merged.isActive)
        assertEquals(existing.archivedAt, merged.archivedAt)
    }

    @Test
    fun builtInSeedMergeKeepsInstalledStableKeyAndUserState() {
        val existing = exercise(9, "Installed", "immutable_builtin_key", false)
            .copy(isActive = false, archivedAt = 99L)
        val seed = exercise(0, "Updated seed name", "changed_seed_key", false)

        val merged = ExerciseStableKeyPolicy.mergeSeed(existing, seed)!!

        assertEquals(existing.id, merged.id)
        assertEquals("immutable_builtin_key", merged.stableKey)
        assertEquals("Updated seed name", merged.name)
        assertEquals(false, merged.isActive)
        assertEquals(99L, merged.archivedAt)
    }

    @Test
    fun customExerciseIsNeverOverwrittenBySeedMerge() {
        val existing = exercise(9, "Custom", "user_ex_key", true)
        val seed = exercise(0, "Seed", "seed_key", false)

        assertNull(ExerciseStableKeyPolicy.mergeSeed(existing, seed))
    }

    private fun exercise(
        id: Long,
        name: String,
        stableKey: String,
        isCustom: Boolean
    ) = Exercise(
        id = id,
        name = name,
        category = "Strength",
        stableKey = stableKey,
        isCustom = isCustom
    )
}
