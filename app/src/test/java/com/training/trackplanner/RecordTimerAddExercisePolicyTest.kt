package com.training.trackplanner

import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordTimerAddExercisePolicyTest {
    @Test
    fun `available only after every exercise has sets and every set is complete`() {
        assertFalse(RecordTimerAddExercisePolicy.available(emptyList()))
        assertFalse(RecordTimerAddExercisePolicy.available(listOf(record(1, emptyList()))))
        assertFalse(RecordTimerAddExercisePolicy.available(listOf(record(1, listOf(true, false)))))
        assertTrue(
            RecordTimerAddExercisePolicy.available(
                listOf(record(1, listOf(true)), record(2, listOf(true, true)))
            )
        )
    }

    private fun record(id: Long, confirmed: List<Boolean>) = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            id = id,
            date = "2026-11-24",
            exerciseStableKey = "exercise_$id",
            exerciseName = "Exercise $id",
            category = "test"
        ),
        sets = confirmed.mapIndexed { index, complete ->
            WorkoutSet(entryId = id, setIndex = index + 1, confirmed = complete)
        }
    )
}
