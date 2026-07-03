package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramDraftEditorModelTest {
    @Test
    fun weekDaySchedule_isIndependentPerWeek() {
        val draft = emptyProgramSkeleton(request(), defaultProgramWeekDaySchedule(4, 3))
            .withWeekDays(1, setOf(1, 3, 5))
            .withWeekDays(2, setOf(2, 4, 6))

        assertEquals(setOf(1, 3, 5), draft.resolvedWeekDaySchedule().getValue(1))
        assertEquals(setOf(2, 4, 6), draft.resolvedWeekDaySchedule().getValue(2))

        val changed = draft.withWeekDays(2, setOf(4))
        assertEquals(setOf(1, 3, 5), changed.resolvedWeekDaySchedule().getValue(1))
        assertEquals(setOf(4), changed.resolvedWeekDaySchedule().getValue(2))
    }

    @Test
    fun addExercise_targetsSelectedWeekAndDayOnly() {
        val draft = emptyProgramSkeleton(request(), defaultProgramWeekDaySchedule(4, 3))
            .upsertDraftItem(item(localId = "row-1", weekNumber = 2, dayOfWeek = 4))

        assertTrue(draft.items.any { it.weekNumber == 2 && it.dayOfWeek == 4 })
        assertFalse(draft.items.any { it.weekNumber == 1 && it.dayOfWeek == 4 })
        assertEquals(setOf(1, 3, 5), draft.resolvedWeekDaySchedule().getValue(1))
        assertTrue(4 in draft.resolvedWeekDaySchedule().getValue(2))
    }

    @Test
    fun removingDay_removesOnlyThatWeekDayItems() {
        val draft = emptyProgramSkeleton(request(), defaultProgramWeekDaySchedule(4, 3))
            .upsertDraftItem(item(localId = "w1-thu", weekNumber = 1, dayOfWeek = 4))
            .upsertDraftItem(item(localId = "w2-thu", weekNumber = 2, dayOfWeek = 4))

        val changed = draft.withWeekDays(2, setOf(1, 3, 5))

        assertTrue(changed.items.any { it.localId == "w1-thu" })
        assertFalse(changed.items.any { it.localId == "w2-thu" })
    }

    @Test
    fun evaluation_changesWhenLoadChanges() {
        val light = emptyProgramSkeleton(request(), defaultProgramWeekDaySchedule(4, 3))
            .upsertDraftItem(item(localId = "light", weekNumber = 1, dayOfWeek = 1, setCount = 2, reps = 5, weightKg = 20.0))
        val heavy = light.upsertDraftItem(
            light.items.single().copy(setCount = 6, reps = 12, weightKg = 140.0, restSeconds = 30)
        )

        val lightScore = ProgramDraftEvaluator.evaluate(light, emptyMap()).fatigue.score
        val heavyScore = ProgramDraftEvaluator.evaluate(heavy, emptyMap()).fatigue.score

        assertTrue(heavyScore < lightScore)
    }

    @Test
    fun evaluation_detectsStrengthAndTransferConcentration() {
        val draft = emptyProgramSkeleton(request(), defaultProgramWeekDaySchedule(4, 3))
            .upsertDraftItem(item(localId = "a", exerciseId = 1, weekNumber = 1, dayOfWeek = 1))
            .upsertDraftItem(item(localId = "b", exerciseId = 2, weekNumber = 1, dayOfWeek = 3))
            .upsertDraftItem(item(localId = "c", exerciseId = 3, weekNumber = 1, dayOfWeek = 5))
        val metadata = listOf(1L, 2L, 3L).associateWith { id ->
            RuntimeExerciseMetadataDefaults.forIdentity("exercise_$id", "Exercise $id").copy(
                strengthProgressionGroup = "CHEST",
                badmintonTransferType = MetadataTokenField.parse("ACCELERATION")
            )
        }

        val evaluation = ProgramDraftEvaluator.evaluate(draft, metadata)

        assertTrue(evaluation.strengthDistribution.score < 100)
        assertTrue(evaluation.badmintonTransferDistribution.score < 100)
        assertTrue(evaluation.strengthDistribution.issues.any { it.severity != ProgramDraftIssueSeverity.GOOD })
        assertTrue(evaluation.badmintonTransferDistribution.issues.any { it.severity != ProgramDraftIssueSeverity.GOOD })
    }

    private fun request(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "Draft",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.7,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.STEP_DELOAD,
            durationWeeks = 4
        )

    private fun item(
        localId: String,
        exerciseId: Long = 1,
        weekNumber: Int,
        dayOfWeek: Int,
        setCount: Int = 3,
        reps: Int = 10,
        weightKg: Double = 50.0,
        restSeconds: Int = 90
    ): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = localId,
            weekNumber = weekNumber,
            dayOfWeek = dayOfWeek,
            orderIndex = 1,
            exerciseId = exerciseId,
            exerciseName = "Exercise $exerciseId",
            category = "Strength",
            restSeconds = restSeconds,
            prescription = "",
            setCount = setCount,
            reps = reps,
            weightKg = weightKg,
            seconds = 0,
            selectionReason = "manual",
            weightSource = "manual",
            neuromuscularStressLevel = "HIGH",
            systemicMuscularStressLevel = "HIGH"
        )
}
