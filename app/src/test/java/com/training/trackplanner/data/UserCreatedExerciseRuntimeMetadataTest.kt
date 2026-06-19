package com.training.trackplanner.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserCreatedExerciseRuntimeMetadataTest {
    @Test
    fun savedMetadataCanBeRestoredForTheGeneratedUserKey() {
        val stableKey = UserExerciseStableKeyGenerator.generate(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        )
        val saved = RuntimeExerciseMetadataDefaults.forIdentity(stableKey, "Custom exercise").copy(
            programSlot = "HORIZONTAL_PULL_ACCESSORY",
            redundancyGroup = "HORIZONTAL_PULL_COMPOUND",
            analysisEligibility = MetadataTokenField.parse("FATIGUE | STRENGTH_PROGRESS")
        )
        val reopened = saved.toEntity().toRuntimeMetadata()

        assertEquals(stableKey, reopened.stableKey)
        assertEquals(saved, reopened)
        assertFalse(reopened.safeForSeedMutation)
    }
}
