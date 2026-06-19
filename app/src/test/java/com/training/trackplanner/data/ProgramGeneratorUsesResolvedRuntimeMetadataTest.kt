package com.training.trackplanner.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramGeneratorUsesResolvedRuntimeMetadataTest {
    @Test
    fun roomPlanningOverrideControlsProgramSelection() {
        val exercise = Exercise(
            id = 31,
            name = "Custom candidate",
            category = "Strength",
            stableKey = "user_ex_123e4567-e89b-12d3-a456-426614174000",
            isCustom = true
        )
        val canonical = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            planningEligibility = "PROGRAM_SELECTABLE",
            programSlot = "MAIN_LOWER_STRENGTH",
            redundancyGroup = "SQUAT_PATTERN_HEAVY_LOWER"
        )
        val persisted = canonical.copy(
            planningEligibility = "FATIGUE_ONLY",
            programSlot = "NOT_APPLICABLE",
            redundancyGroup = "NOT_APPLICABLE"
        )
        val catalog = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = listOf(persisted)
        ).catalog(listOf(exercise))

        val result = ProgramSkeletonGenerator().generate(
            request = ProgramSkeletonRequest(
                name = "Test",
                goal = ProgramGoal.STRENGTH,
                weeklyTrainingDays = 2,
                sessionMinutes = 45,
                availableEquipment = emptySet(),
                excludedExerciseText = "",
                badmintonTransferRatio = 0.0,
                sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO
            ),
            exercises = listOf(exercise),
            history = emptyList(),
            runtimeMetadataCatalog = catalog
        )

        assertTrue(result.items.isEmpty())
    }
}
