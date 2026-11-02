package com.training.trackplanner

import com.training.trackplanner.data.ProgramProgressionItem
import com.training.trackplanner.data.ProgramProgressionTrack
import com.training.trackplanner.data.ProgressionSignature
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgramItemSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramProgressionDisplayIndexTest {
    private fun item(id: Long, week: Int, day: Int, key: String = "squat") =
        TrainingProgramItem(
            id = id,
            programId = 7L,
            weekNumber = week,
            dayOfWeek = day,
            orderIndex = id.toInt(),
            exerciseStableKey = key,
            exerciseName = key,
            category = "근력운동"
        )

    private fun binding(itemId: Long, trackId: String, key: String = "squat") =
        ProgramProgressionItem(
            programItemId = itemId,
            trackId = trackId,
            signature = ProgressionSignature(
                exerciseStableKey = key,
                setCount = 1,
                repsPattern = "5",
                baseKg = 60.0,
                oneRmSnapshotKg = null,
                relativeIntensity = null
            )
        )

    @Test fun indexesPreserveProgramAndExerciseBoundariesAndSourceOrder() {
        val items = listOf(item(3, 2, 1), item(1, 1, 2), item(2, 1, 1, "deadlift"))
        val sets = listOf(
            TrainingProgramItemSet(id = 2, programItemId = 1, setIndex = 1, reps = 5),
            TrainingProgramItemSet(id = 3, programItemId = 3, setIndex = 1, reps = 5)
        ).groupBy { it.programItemId }
        val tracks = listOf(
            ProgramProgressionTrack(id = "t2", programStableKey = "prog", exerciseStableKey = "deadlift", label = "D"),
            ProgramProgressionTrack(id = "t1", programStableKey = "prog", exerciseStableKey = "squat", label = "S"),
            ProgramProgressionTrack(id = "other", programStableKey = "different", exerciseStableKey = "squat", label = "X")
        )
        val result = ProgramProgressionDisplayIndex.build(
            items, sets, listOf(binding(3, "t1"), binding(1, "t1"), binding(2, "t2", "deadlift")), tracks
        )

        assertEquals(listOf(3L, 1L), result.membersByTrackId["t1"]?.map { it.id })
        assertEquals(listOf(2L), result.membersByTrackId["t2"]?.map { it.id })
        assertEquals(listOf("t1"), result.tracksByProgramAndExercise["prog" to "squat"]?.map { it.id })
        assertEquals(listOf("other"), result.tracksByProgramAndExercise["different" to "squat"]?.map { it.id })
        assertEquals(listOf(2L), result.setsByItemId[1L]?.map { it.id })
        assertEquals("t1", result.bindingByItemId[3L]?.trackId)
        assertNull(result.bindingByItemId[90L])
    }

    @Test fun unlinkedItemsDoNotInheritAnUnrelatedTrack() {
        val item = item(4, 1, 1)
        val index = ProgramProgressionDisplayIndex.build(listOf(item), emptyMap(), emptyList(), emptyList())
        assertNull(index.bindingByItemId[item.id])
        assertEquals(listOf(item.id), index.membersByTrackId[null]?.map { it.id })
    }
}
