package com.training.trackplanner.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun newCustomExercisePickerOptionsUseFullValidSetsNotOnlyDefaults() {
        val options = RuntimeMetadataEditorOptions.from(emptyList())

        assertEquals(setOf("EXERCISE", "SPORT_SESSION"), options.values("activityKind").toSet())
        assertTrue("PROGRAM_SELECTABLE" in options.values("planningEligibility"))
        assertTrue("FATIGUE_ONLY" in options.values("planningEligibility"))
        assertTrue("ROTATIONAL_KINETIC_CHAIN" in options.values("programSlot"))
        assertTrue("LOAD_REPS" in options.values("progressMetricType"))
        assertTrue("ESTIMATED_1RM" in options.values("progressMetricType"))
        assertEquals(setOf("DIRECT", "GENERAL", "NONE", "SUPPORTIVE"), options.values("badmintonTransferLevel").toSet())
        assertEquals(setOf("HIGH", "LOW", "MODERATE", "VERY_HIGH"), options.values("neuromuscularStressLevel").toSet())
        assertEquals(setOf("LONG", "MEDIUM", "SHORT", "VERY_LONG"), options.values("recoveryDurationClass").toSet())
        assertTrue(options.values("primaryStressProfile").size > 1)
        assertTrue(options.values("secondaryStressTags").size > 1)
    }

    @Test
    fun pickerOptionsKeepPersistedCustomValuesThatAreNotInDefaults() {
        val custom = RuntimeExerciseMetadataDefaults.forIdentity("user_ex_custom", "Custom").copy(
            programSlot = "MY_EXPERIMENTAL_SLOT",
            badmintonTransferLevel = "SUPPORTIVE"
        )

        val options = RuntimeMetadataEditorOptions.from(listOf(custom))

        assertTrue("MY_EXPERIMENTAL_SLOT" in options.values("programSlot"))
        assertTrue("SUPPORTIVE" in options.values("badmintonTransferLevel"))
    }
}
