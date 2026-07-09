package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramPlanServiceMappingTest {
    @Test
    fun generatedItemsPersistStructuredRestoreMetadata() {
        val stored = ProgramSkeletonItem(
            localId = "item",
            weekNumber = 2,
            dayOfWeek = 4,
            orderIndex = 1,
            exerciseId = 7,
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
            exerciseId = 7,
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
}
