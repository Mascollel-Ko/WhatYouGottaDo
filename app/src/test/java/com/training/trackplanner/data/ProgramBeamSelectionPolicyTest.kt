package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramBeamSelectionPolicyTest {
    @Test
    fun beamPrefersPreferredCandidateInsideWideWindow() {
        val neutral = candidate(1, "neutral_row")
        val preferred = candidate(2, "preferred_squat")
        val scored = listOf(neutral to 100.0) + (3..12).map { candidate(it, "candidate_$it") to 99.0 } + listOf(preferred to 100.0)

        val picked = ProgramBeamSelectionPolicy().choose(
            scored = scored,
            context = context(preferred = setOf("preferred_squat")),
            classification = ProgramCandidateClassificationPolicy()::classify,
            desiredExerciseCount = 4
        )

        assertEquals("preferred_squat", picked?.exercise?.stableKey)
    }

    @Test
    fun beamDoesNotSelectExcludedCandidateEvenWithHigherScore() {
        val excluded = candidate(1, "excluded_squat")
        val allowed = candidate(2, "allowed_row")

        val picked = ProgramBeamSelectionPolicy().choose(
            scored = listOf(excluded to 200.0, allowed to 100.0),
            context = context(excluded = setOf("excluded_squat")),
            classification = ProgramCandidateClassificationPolicy()::classify,
            desiredExerciseCount = 4
        )

        assertEquals("allowed_row", picked?.exercise?.stableKey)
    }

    @Test
    fun beamPenalizesProgramWideStableKeyRepetition() {
        val repeated = candidate(1, "repeated_core")
        val fresh = candidate(2, "fresh_core")

        val picked = ProgramBeamSelectionPolicy().choose(
            scored = listOf(repeated to 100.0, fresh to 100.0),
            context = context(
                generatedItems = listOf(
                    item("repeated_core", weekNumber = 1),
                    item("repeated_core", weekNumber = 2)
                )
            ),
            classification = ProgramCandidateClassificationPolicy()::classify,
            desiredExerciseCount = 4
        )

        assertEquals("fresh_core", picked?.exercise?.stableKey)
    }

    private fun context(
        preferred: Set<String> = emptySet(),
        excluded: Set<String> = emptySet(),
        generatedItems: List<ProgramSkeletonItem> = emptyList()
    ): ProgramCandidateScoreContext =
        ProgramCandidateScoreContext(
            request = ProgramSkeletonRequest(
                name = "beam fixture",
                goal = ProgramGoal.BADMINTON_SUPPORT,
                weeklyTrainingDays = 5,
                sessionMinutes = 45,
                availableEquipment = emptySet(),
                excludedExerciseText = "",
                badmintonTransferRatio = 0.60,
                sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO,
                excludedExerciseStableKeys = excluded,
                preferredExerciseStableKeys = preferred
            ),
            week = ProgramWeekPlan(
                weekIndex = 1,
                weekType = ProgramWeekType.BUILD.name,
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false
            ),
            periodizedWeek = ProgramPeriodizationWeekPlan(
                weekIndex = 1,
                role = ProgramWeekRole.FOUNDATION_LOAD,
                dayProfiles = mapOf(1 to ProgramDayProfile.HARD_FOUNDATION)
            ),
            plannedSlot = PlannedSlot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR),
            selectedInSession = emptyList(),
            generatedItems = generatedItems
        )

    private fun candidate(id: Int, stableKey: String): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = id.toLong(),
                name = stableKey,
                category = "strength",
                stableKey = stableKey,
                equipment = "BARBELL",
                movementPattern = "SQUAT",
                planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
            ),
            metadata = RuntimeExerciseMetadataDefaults.forIdentity(stableKey, stableKey).copy(
                movementFamily = "SQUAT",
                movementSubtype = "SQUAT",
                programSlot = ProgramSlotId.LOWER_SQUAT_PATTERN.name,
                strengthProgressionGroup = stableKey
            ),
            canonical = true,
            slotCapabilities = SlotCapabilityProfile(
                primary = setOf(ProgramSlotId.LOWER_SQUAT_PATTERN),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )

    private fun item(stableKey: String, weekNumber: Int): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = "$weekNumber-$stableKey",
            weekNumber = weekNumber,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseId = stableKey.hashCode().toLong(),
            exerciseName = stableKey,
            category = "strength",
            restSeconds = 60,
            prescription = "fixture",
            setCount = 2,
            reps = 8,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "",
            weightSource = "",
            stableKey = stableKey
        )
}
