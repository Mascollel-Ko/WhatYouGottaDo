package com.training.trackplanner

import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramExerciseSelectionOptionsTest {
    @Test
    fun selectionOptionsFilterBySearchAndSkipSelectedOrInactiveExercises() {
        val options = programExerciseSelectionOptions(
            exercises = listOf(
                exercise("barbell_squat", "Barbell Squat"),
                exercise("dumbbell_row", "Dumbbell Row"),
                exercise("captain_chair", "Captain Chair", active = false),
                exercise("cable_row", "Cable Row")
            ),
            query = "row",
            selectedStableKeys = setOf("dumbbell_row")
        )

        assertEquals(listOf("Cable Row"), options.map(Exercise::name))
        assertTrue(options.none { it.stableKey == "dumbbell_row" })
    }

    private fun exercise(stableKey: String, name: String, active: Boolean = true): Exercise =
        Exercise(
            id = stableKey.hashCode().toLong(),
            name = name,
            category = "strength",
            stableKey = stableKey,
            isActive = active
        )
}
