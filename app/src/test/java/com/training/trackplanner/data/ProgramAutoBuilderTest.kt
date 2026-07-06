package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramAutoBuilderTest {
    @Test
    fun plausibilityScenariosGenerateNonEmptyWeeksWithinSlotCaps() {
        listOf(
            request(days = 3, minutes = 45, weeks = 3, ratio = 0.30),
            request(days = 4, minutes = 60, weeks = 4, ratio = 0.50),
            request(days = 5, minutes = 45, weeks = 6, ratio = 0.70),
            request(days = 4, minutes = 45, weeks = 4, ratio = 0.0)
        ).forEach { request ->
            val skeleton = build(request)

            assertEquals(request.durationWeeks, skeleton.weekPlans.size)
            assertTrue(skeleton.weekPlans.all { week -> skeleton.items.any { it.weekNumber == week.weekIndex } })
            skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.forEach { (_, items) ->
                assertTrue(items.size <= ProgramRuleTables.slotCaps(request.sessionMinutes).totalSlots)
                assertTrue(items.count { it.selectionRole == ProgramAutoSlotType.MAIN.name } <= 2)
            }
        }
    }

    @Test
    fun ratioControlsBadmintonAccessoryCountAndOrder() {
        val pureStrength = build(request(ratio = 0.0))
        val thirty = build(request(ratio = 0.30))
        val fifty = build(request(ratio = 0.50))
        val seventy = build(request(minutes = 60, ratio = 0.70))

        assertEquals(0, pureStrength.items.count { it.selectionRole == ProgramAutoSlotType.BADMINTON_ACCESSORY.name })
        assertTrue(thirty.badmintonCountsPerDay().all { it == 1 })
        assertTrue(fifty.badmintonCountsPerDay().all { it in 1..2 })
        assertTrue(seventy.badmintonCountsPerDay().all { it in 2..3 })
        assertTrue(
            seventy.items
                .groupBy { it.weekNumber to it.dayOfWeek }
                .values
                .all { dayItems ->
                    val firstStrengthAccessory = dayItems.indexOfFirst {
                        it.selectionRole == ProgramAutoSlotType.STRENGTH_ACCESSORY.name
                    }
                    val lastBadminton = dayItems.indexOfLast {
                        it.selectionRole == ProgramAutoSlotType.BADMINTON_ACCESSORY.name
                    }
                    firstStrengthAccessory == -1 || lastBadminton == -1 || lastBadminton < firstStrengthAccessory
                }
        )
    }

    @Test
    fun pairedMainAccessoriesAreUsedWhenSlotsAllow() {
        val skeleton = build(request(days = 4, minutes = 45, weeks = 4, ratio = 0.0))
        val reasons = skeleton.items.map { it.selectionReason }

        assertTrue(reasons.any { it.contains("Paired Lower anterior") })
        assertTrue(reasons.any { it.contains("Paired Lower posterior") })
        assertTrue(reasons.any { it.contains("Paired Chest") })
        assertTrue(reasons.any { it.contains("Paired Back") })
    }

    @Test
    fun threeDaySplitUsesBackAccessoryOnLowerPosteriorDay() {
        val skeleton = build(request(days = 3, minutes = 45, weeks = 3, ratio = 0.30))
        val weekOneLowerPosterior = skeleton.items.filter { it.weekNumber == 1 && it.dayOfWeek == 5 }

        assertTrue(weekOneLowerPosterior.any { it.trainingSlot == "MAIN_LOWER_POSTERIOR" })
        assertTrue(weekOneLowerPosterior.any { it.trainingSlot == "PAIRED_BACK" })
    }

    @Test
    fun weakChestShoulderWorkIsAccessoryNotSecondMain() {
        val skeleton = build(request(days = 3, minutes = 45, weeks = 3, ratio = 0.0))
        val weekOneMiddleDay = skeleton.items.filter { it.weekNumber == 1 && it.dayOfWeek == 3 }
        val weekTwoMiddleDay = skeleton.items.filter { it.weekNumber == 2 && it.dayOfWeek == 3 }

        assertEquals(1, weekOneMiddleDay.count { it.selectionRole == ProgramAutoSlotType.MAIN.name })
        assertEquals("MAIN_CHEST", weekOneMiddleDay.first { it.selectionRole == ProgramAutoSlotType.MAIN.name }.trainingSlot)
        assertTrue(weekOneMiddleDay.any { it.trainingSlot == "PAIRED_SHOULDER" })
        assertEquals(1, weekTwoMiddleDay.count { it.selectionRole == ProgramAutoSlotType.MAIN.name })
        assertEquals("MAIN_SHOULDER", weekTwoMiddleDay.first { it.selectionRole == ProgramAutoSlotType.MAIN.name }.trainingSlot)
        assertTrue(weekTwoMiddleDay.any { it.trainingSlot == "PAIRED_CHEST" })
    }

    @Test
    fun fivePlusDaySplitCapsHardMainDaysAndKeepsShoulderVisible() {
        val skeleton = build(request(days = 5, minutes = 45, weeks = 4, ratio = 0.50))
        val weekOne = skeleton.items.filter { it.weekNumber == 1 }

        assertEquals(4, weekOne.count { it.selectionRole == ProgramAutoSlotType.MAIN.name })
        assertTrue(weekOne.any { it.trainingSlot == "PAIRED_SHOULDER" })
        assertTrue(weekOne.groupBy { it.dayOfWeek }.values.any { day -> day.none { it.selectionRole == ProgramAutoSlotType.MAIN.name } })
    }

    @Test
    fun weekdayReplacementPreservesOrderedSlotContents() {
        val skeleton = build(request(days = 4, minutes = 45, weeks = 4, ratio = 0.30))
        val before = skeleton.items
            .filter { it.weekNumber == 1 }
            .groupBy { it.dayOfWeek }
            .toSortedMap()
            .values
            .map { it.sortedBy(ProgramSkeletonItem::orderIndex).map(ProgramSkeletonItem::exerciseName) }

        val changed = ProgramDaySelector.replaceWeekdays(skeleton, 1, setOf(2, 3, 5, 6))
        val after = changed.items
            .filter { it.weekNumber == 1 }
            .groupBy { it.dayOfWeek }
            .toSortedMap()
            .values
            .map { it.sortedBy(ProgramSkeletonItem::orderIndex).map(ProgramSkeletonItem::exerciseName) }

        assertEquals(setOf(2, 3, 5, 6), changed.resolvedWeekDaySchedule().getValue(1))
        assertEquals(before, after)
        assertNotEquals(skeleton.resolvedWeekDaySchedule().getValue(1), changed.resolvedWeekDaySchedule().getValue(1))
    }

    @Test
    fun avoidsAdjacentSmallPartAndBadmintonCategoryRepeatsWhenAlternativesExist() {
        val skeleton = build(request(days = 5, minutes = 60, weeks = 4, ratio = 0.70))
        val orderedDays = skeleton.items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .values
            .toList()

        orderedDays.zipWithNext().forEach { (left, right) ->
            val leftSmall = left.lastOrNull { it.trainingSlot.startsWith("SMALL_") }?.trainingSlot
            val rightSmall = right.lastOrNull { it.trainingSlot.startsWith("SMALL_") }?.trainingSlot
            if (leftSmall != null && rightSmall != null) assertNotEquals(leftSmall, rightSmall)
            val leftBadminton = left.lastOrNull { it.trainingSlot.startsWith("BADMINTON_") }?.trainingSlot
            val rightBadminton = right.firstOrNull { it.trainingSlot.startsWith("BADMINTON_") }?.trainingSlot
            if (leftBadminton != null && rightBadminton != null) assertNotEquals(leftBadminton, rightBadminton)
        }
    }

    private fun build(request: ProgramSkeletonRequest): GeneratedProgramSkeleton =
        ProgramAutoBuilder().build(request, exercises = emptyList())

    private fun GeneratedProgramSkeleton.badmintonCountsPerDay(): List<Int> =
        items.groupBy { it.weekNumber to it.dayOfWeek }
            .values
            .map { dayItems -> dayItems.count { it.selectionRole == ProgramAutoSlotType.BADMINTON_ACCESSORY.name } }

    private fun request(
        days: Int = 4,
        minutes: Int = 45,
        weeks: Int = 4,
        ratio: Double = 0.30
    ): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "배드민턴 지원 웨이트",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = days,
            sessionMinutes = minutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = ratio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = weeks
        )
}
