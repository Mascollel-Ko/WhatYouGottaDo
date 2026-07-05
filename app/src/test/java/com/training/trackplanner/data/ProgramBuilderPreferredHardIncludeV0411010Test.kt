package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgramBuilderPreferredHardIncludeV0411010Test {
    @Test
    fun preferredExerciseSurvivesEquipmentMismatchAndGenerationFilters() {
        val preferred = exercise(
            id = 1,
            stableKey = "preferred_barbell_squat",
            name = "Preferred Barbell Squat",
            equipment = "BARBELL",
            movementPattern = "SQUAT",
            movementCategory = "LOWER_STRENGTH"
        )
        val exercises = listOf(preferred) + bodyweightCatalog()

        val result = ProgramBuilder().build(
            request = baseRequest().copy(
                availableEquipment = setOf("BODYWEIGHT"),
                preferredExerciseStableKeys = setOf(preferred.stableKey)
            ),
            exercises = exercises,
            history = emptyList(),
            today = LocalDate.of(2026, 1, 5)
        )

        assertTrue(
            "Preferred exercises are hard includes and must not be silently dropped by equipment, template, fatigue, time, or slot filters.",
            result.items.any { it.stableKey == preferred.stableKey }
        )
        assertFalse(result.validationDetails.any { it.code == "PROGRAM_PREFERRED_EXERCISE_MISSING" })
        assertFalse(result.validationDetails.any { it.code == "PROGRAM_PREFERRED_EXERCISE_INACTIVE" })
        assertFalse(result.validationDetails.any { it.code == "PROGRAM_PREFERRED_EXCLUDED_CONFLICT" })
        assertTrue(
            result.optimizationSummary.messages.any {
                it.startsWith("PROGRAM_PREFERRED_EXERCISE_FORCED: ${preferred.stableKey}") ||
                    it == "PROGRAM_PREFERRED_EXERCISE_INCLUDED: ${preferred.stableKey}"
            }
        )
    }

    @Test
    fun preferredExcludedConflictIsHardValidationIssue() {
        val conflictKey = "preferred_bodyweight_row"

        val result = ProgramBuilder().build(
            request = baseRequest().copy(
                preferredExerciseStableKeys = setOf(conflictKey),
                excludedExerciseStableKeys = setOf(conflictKey)
            ),
            exercises = bodyweightCatalog(),
            history = emptyList(),
            today = LocalDate.of(2026, 1, 5)
        )

        val issue = result.validationDetails.single { it.code == "PROGRAM_PREFERRED_EXCLUDED_CONFLICT" }
        assertEquals(ProgramValidationSeverity.HARD, issue.severity)
        assertFalse(result.items.any { it.stableKey == conflictKey })
    }

    @Test
    fun preferredOutcomeMessagesNameMissingInactiveAndConflictKeys() {
        val inactive = exercise(
            id = 99,
            stableKey = "inactive_preferred",
            name = "Inactive Preferred",
            equipment = "BODYWEIGHT",
            movementPattern = "SQUAT",
            movementCategory = "LOWER_STRENGTH"
        ).copy(isActive = false)
        val conflictKey = "preferred_bodyweight_row"

        val result = ProgramBuilder().build(
            request = baseRequest().copy(
                preferredExerciseStableKeys = setOf("missing_preferred", inactive.stableKey, conflictKey),
                excludedExerciseStableKeys = setOf(conflictKey)
            ),
            exercises = bodyweightCatalog() + inactive,
            history = emptyList(),
            today = LocalDate.of(2026, 1, 5)
        )

        val messages = result.optimizationSummary.messages.toSet()
        assertTrue("PROGRAM_PREFERRED_EXERCISE_MISSING: missing_preferred" in messages)
        assertTrue("PROGRAM_PREFERRED_EXERCISE_INACTIVE: inactive_preferred" in messages)
        assertTrue("PROGRAM_PREFERRED_EXCLUDED_CONFLICT: preferred_bodyweight_row" in messages)
    }

    private fun baseRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "preferred hard include fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = setOf("BODYWEIGHT"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            durationWeeks = 4
        )

    private fun bodyweightCatalog(): List<Exercise> =
        listOf(
            exercise(10, "preferred_bodyweight_row", "Preferred Bodyweight Row", "BODYWEIGHT", "PULL_HORIZONTAL", "UPPER_STRENGTH"),
            exercise(11, "bodyweight_split_squat", "Bodyweight Split Squat", "BODYWEIGHT", "SPLIT_SQUAT", "LOWER_STRENGTH"),
            exercise(12, "push_up", "Push Up", "BODYWEIGHT", "PUSH_HORIZONTAL", "UPPER_STRENGTH"),
            exercise(13, "dead_bug", "Dead Bug", "BODYWEIGHT", "CORE_ANTI_EXTENSION", "CORE"),
            exercise(14, "side_plank", "Side Plank", "BODYWEIGHT", "CORE_LATERAL_STABILITY", "CORE"),
            exercise(15, "six_corner_shadow_footwork", "Six Corner Shadow Footwork", "BODYWEIGHT", "FOOTWORK", "BADMINTON_TRANSFER"),
            exercise(16, "bodyweight_lunge", "Bodyweight Lunge", "BODYWEIGHT", "LUNGE", "LOWER_STRENGTH"),
            exercise(17, "pallof_press_bodyweight", "Pallof Press Bodyweight", "BODYWEIGHT", "ANTI_ROTATION", "CORE"),
            exercise(18, "glute_bridge", "Glute Bridge", "BODYWEIGHT", "HINGE", "LOWER_STRENGTH")
        )

    private fun exercise(
        id: Long,
        stableKey: String,
        name: String,
        equipment: String,
        movementPattern: String,
        movementCategory: String
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "strength",
            detail1 = movementCategory,
            stableKey = stableKey,
            equipment = equipment,
            movementPattern = movementPattern,
            movementCategory = movementCategory,
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        )
}
