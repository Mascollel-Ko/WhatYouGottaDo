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

    @Test
    fun resolverUsesPersistedCustomMetadataByStableKey() {
        val exercise = Exercise(
            name = "내 커스텀 회전운동",
            category = "근력",
            stableKey = "user_ex_123e4567-e89b-12d3-a456-426614174000",
            isCustom = true
        )
        val saved = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = "ROTATIONAL_KINETIC_CHAIN",
            badmintonTransferLevel = "SUPPORTIVE",
            localMuscularStressLevel = "HIGH"
        )

        val resolved = RuntimeExerciseMetadataResolver(
            RuntimeExerciseMetadataCatalog.EMPTY,
            listOf(saved.toEntity().toRuntimeMetadata())
        ).resolve(exercise)

        assertEquals("ROTATIONAL_KINETIC_CHAIN", resolved.movementFamily)
        assertEquals("SUPPORTIVE", resolved.badmintonTransferLevel)
        assertEquals("HIGH", resolved.localMuscularStressLevel)
    }
}
