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

    @Test
    fun selectionOptionsSearchAcrossCatalogFieldsAndStableKey() {
        val exercises = listOf(
            exercise(
                stableKey = "barbell_back_squat",
                name = "Squat",
                detail1 = "quadriceps",
                equipment = "barbell",
                movementPattern = "SQUAT"
            ),
            exercise(
                stableKey = "pull_up",
                name = "Pull Up",
                detail1 = "latissimus",
                equipment = "bodyweight",
                movementPattern = "PULL_VERTICAL"
            ),
            exercise(
                stableKey = "ex_a345e30b",
                name = "Captain Chair Leg Raise",
                detail1 = "rectus abdominis",
                equipment = "captain chair",
                movementPattern = "CORE_FLEXION"
            )
        )

        assertEquals(listOf("barbell_back_squat"), programExerciseSelectionOptions(exercises, "barbell_back_squat", emptySet()).map(Exercise::stableKey))
        assertEquals(listOf("barbell_back_squat"), programExerciseSelectionOptions(exercises, "quadriceps", emptySet()).map(Exercise::stableKey))
        assertEquals(listOf("pull_up"), programExerciseSelectionOptions(exercises, "PULL_VERTICAL", emptySet()).map(Exercise::stableKey))
        assertEquals(listOf("ex_a345e30b"), programExerciseSelectionOptions(exercises, "captain", emptySet()).map(Exercise::stableKey))
    }

    @Test
    fun selectionOptionsDoNotLimitSearchQueryResultsToSix() {
        val exercises = (1..10).map { index ->
            exercise("candidate_$index", "Candidate $index", category = "same")
        }

        val options = programExerciseSelectionOptions(
            exercises = exercises,
            query = "same",
            selectedStableKeys = emptySet()
        )

        assertEquals(10, options.size)
    }

    private fun exercise(
        stableKey: String,
        name: String,
        category: String = "strength",
        detail1: String = "",
        equipment: String = "",
        movementPattern: String = "",
        active: Boolean = true
    ): Exercise =
        Exercise(
            id = stableKey.hashCode().toLong(),
            name = name,
            category = category,
            detail1 = detail1,
            stableKey = stableKey,
            equipment = equipment,
            movementPattern = movementPattern,
            isActive = active
        )
}
