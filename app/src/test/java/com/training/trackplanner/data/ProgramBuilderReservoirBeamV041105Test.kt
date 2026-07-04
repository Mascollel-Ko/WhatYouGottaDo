package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBuilderReservoirBeamV041105Test {
    @Test
    fun badSkeletonWithMissingAnchorsAndRepeatedCoreCannotScorePerfect() {
        val skeleton = skeleton(
            days = listOf(1, 2, 4, 6, 7),
            itemFactory = { week, day, order ->
                repeatedCaptainChairItem(week, day, order)
            }
        )

        val evaluation = ProgramEvaluationPolicy().evaluate(skeleton)

        assertTrue("bad skeleton must not score 100", evaluation.overallScore < 100)
        assertTrue(
            "missing foundation anchors should be detected",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR }
        )
        assertTrue(
            "loaded strength underuse should be detected when all equipment is available",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED }
        )
    }

    @Test
    fun sixCornerShadowFootworkIsAllowedAsTransferWhenItIsNotDirectSportSession() {
        val item = transferItem(week = 1, day = 1, order = 1, name = "6-corner shadow footwork")

        assertFalse("6-corner footwork should not be treated as direct sport session here", item.directSportSession)
        assertTrue("6-corner footwork can remain a badminton transfer item",
            item.selectionRole == ProgramExerciseRole.TRANSFER.name)
    }

    @Test
    fun repeatedCaptainChairFixtureRepresentsCoreAccessoryNotFoundationAnchor() {
        val item = repeatedCaptainChairItem(week = 1, day = 1, order = 1)

        assertTrue(item.selectionRole == ProgramExerciseRole.CORE.name)
        assertTrue(item.requestedTemplateSlot == ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name)
        assertFalse("captain chair must not masquerade as a foundation anchor",
            item.primarySlotCapabilities.any(FOUNDATION_SLOT_NAMES::contains))
    }

    private fun skeleton(
        days: List<Int>,
        itemFactory: (week: Int, day: Int, order: Int) -> ProgramSkeletonItem
    ): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "reservoir selection gap fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = days.size,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "DUMBBELL", "MACHINE", "CABLE", "BODYWEIGHT"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            durationWeeks = 4
        )
        val weeks = (1..4).map { week ->
            ProgramWeekPlan(
                weekIndex = week,
                weekType = ProgramWeekType.BUILD.name,
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false
            )
        }
        return GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = 28,
            request = request,
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            weekPlans = weeks,
            items = weeks.flatMap { week ->
                days.flatMap { day -> (1..4).map { order -> itemFactory(week.weekIndex, day, order) } }
            }
        )
    }

    private fun repeatedCaptainChairItem(week: Int, day: Int, order: Int): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = "$week-$day-$order",
            weekNumber = week,
            dayOfWeek = day,
            orderIndex = order,
            exerciseId = 10_000L + week * 100 + day * 10 + order,
            exerciseName = "Captain chair leg raise",
            category = "strength",
            restSeconds = 60,
            prescription = "2x12",
            setCount = 2,
            reps = 12,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "",
            weightSource = "",
            trainingSlot = ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
            dayIntensity = ProgramDayIntensity.MODERATE.name,
            stableKey = "captain_chair_leg_raise",
            selectionRole = ProgramExerciseRole.CORE.name,
            movementFamily = "CORE_FLEXION_ANTERIOR_CORE",
            movementSubtype = "CAPTAINS_CHAIR_LEG_RAISE",
            redundancyGroup = "CORE_FLEXION",
            badmintonTransferLevel = "GENERAL",
            primarySlotCapabilities = listOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name),
            requestedTemplateSlot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
        )

    private fun transferItem(week: Int, day: Int, order: Int, name: String): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = "$week-$day-$order",
            weekNumber = week,
            dayOfWeek = day,
            orderIndex = order,
            exerciseId = 20_000L + week * 100 + day * 10 + order,
            exerciseName = name,
            category = "functional",
            restSeconds = 60,
            prescription = "3x20s",
            setCount = 3,
            reps = 0,
            weightKg = 0.0,
            seconds = 20,
            selectionReason = "",
            weightSource = "",
            trainingSlot = ProgramTrainingSlot.BADMINTON_TRANSFER.name,
            dayIntensity = ProgramDayIntensity.MODERATE.name,
            stableKey = "six_corner_shadow_footwork",
            selectionRole = ProgramExerciseRole.TRANSFER.name,
            movementFamily = "COURT_FOOTWORK",
            redundancyGroup = "FOOTWORK",
            badmintonTransferLevel = "DIRECT",
            primarySlotCapabilities = listOf(ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name),
            requestedTemplateSlot = ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            directSportSession = false
        )

    private companion object {
        val FOUNDATION_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name,
            ProgramSlotId.UPPER_PUSH_SUPPORT.name
        )
    }
}
