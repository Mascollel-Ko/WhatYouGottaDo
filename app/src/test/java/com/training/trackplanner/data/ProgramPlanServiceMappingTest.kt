package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramPlanServiceMappingTest {
    @Test
    fun generatedItemsPersistStructuredRestoreMetadata() {
        val stored = ProgramSkeletonItem(
            localId = "item",
            weekNumber = 2,
            dayOfWeek = 4,
            orderIndex = 1,
            exerciseStableKey = "test_exercise_7",
            exerciseName = "Row",
            category = "Strength",
            restSeconds = 90,
            prescription = "display text may change",
            setCount = 3,
            reps = 8,
            weightKg = 60.0,
            seconds = 0,
            selectionReason = "test",
            trainingSlot = "UPPER_STRENGTH",
            dayIntensity = "HARD",
            weightSource = "DIRECT_HISTORY_HIGH"
        ).toTrainingProgramItem(programId = 11)

        assertEquals("UPPER_STRENGTH", stored.trainingSlot)
        assertEquals("HARD", stored.dayIntensity)
        assertEquals("DIRECT_HISTORY_HIGH", stored.weightSource)

        val restored = ProgramItemRestoreMetadataParser.resolve(
            stored.copy(prescription = "changed display text")
        )
        assertEquals("UPPER_STRENGTH", restored.metadata.trainingSlot)
        assertEquals("HARD", restored.metadata.dayIntensity)
        assertEquals("DIRECT_HISTORY_HIGH", restored.metadata.weightSource)
    }

    @Test
    fun generatedItemsNeverPersistBlankRestoreMetadata() {
        val stored = ProgramSkeletonItem(
            localId = "item",
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = "test_exercise_7",
            exerciseName = "Manual",
            category = "Strength",
            restSeconds = 60,
            prescription = "",
            setCount = 1,
            reps = 0,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "",
            trainingSlot = "",
            dayIntensity = "",
            weightSource = ""
        ).toTrainingProgramItem(programId = 11)

        assertEquals(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name, stored.trainingSlot)
        assertEquals(ProgramDayIntensity.MODERATE.name, stored.dayIntensity)
        assertEquals("MANUAL_OR_EXISTING", stored.weightSource)
    }

    @Test
    fun approvedTaskProtocolSemanticsPersistAsTypedJsonRatherThanDisplayText() {
        val semantics = C24TaskProtocolTestFixture.sixCornerSemanticsJson()
        val stored = ProgramSkeletonItem(
            localId = "task", weekNumber = 1, dayOfWeek = 2, orderIndex = 1,
            exerciseStableKey = "ex_33841b88", exerciseName = "6코너 풋워크", category = "스포츠",
            restSeconds = 60, prescription = "3 라운드 × 10–20초 · 휴식 60초", setCount = 3,
            reps = 0, weightKg = 0.0, seconds = 20, selectionReason = "approved task protocol",
            weightSource = "USER_APPROVED_PROJECT_POLICY", stableKey = "ex_33841b88",
            selectionRole = "CANONICAL_STIMULUS_TASK_ACCELERATION", taskProtocolSemanticsJson = semantics
        ).toTrainingProgramItem(programId = 11)

        assertEquals(semantics, stored.taskProtocolSemanticsJson)
        assertTrue(stored.taskProtocolSemanticsJson!!.contains("DURATION_RANGE_SECONDS"))
        assertTrue(stored.taskProtocolSemanticsJson!!.contains("USER_APPROVED_PROJECT_POLICY"))
    }

    @Test
    fun heterogeneousSetRowsProduceOnlyCompatibilityScalars() {
        val stored = ProgramSkeletonItem(
            localId = "mixed",
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = "squat",
            exerciseName = "Squat",
            category = "Strength",
            restSeconds = 120,
            prescription = "",
            setCount = 9,
            reps = 99,
            weightKg = 999.0,
            seconds = 0,
            selectionReason = "",
            weightSource = "",
            setPrescriptions = listOf(
                ProgramSetPrescription(1, 3, 110.0, 0),
                ProgramSetPrescription(2, 8, 80.0, 0)
            )
        ).toTrainingProgramItem(programId = 11)

        assertEquals(2, stored.setCount)
        assertEquals(0, stored.reps)
        assertEquals(0.0, stored.weightKg, 0.0)
        assertEquals(0, stored.seconds)
    }
}
