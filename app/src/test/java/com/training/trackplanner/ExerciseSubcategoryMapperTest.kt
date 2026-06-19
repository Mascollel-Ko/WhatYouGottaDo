package com.training.trackplanner

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSubcategoryMapperTest {
    @Test
    fun groupingUsesStructuredMetadataInsteadOfExerciseName() {
        val misleadingName = exercise(1, "squat badminton core", "근력운동")
        val rowMetadata = RuntimeExerciseMetadataDefaults.forExercise(misleadingName).copy(
            movementFamily = "ROW_VARIANTS",
            programSlot = "HORIZONTAL_PULL_STRENGTH",
            redundancyGroup = "HORIZONTAL_PULL_COMPOUND"
        )

        val categories = ExerciseSubcategoryMapper.categoriesFor(misleadingName, rowMetadata)

        assertTrue(ExerciseSubcategory.STRENGTH_BACK_PULL in categories)
        assertFalse(ExerciseSubcategory.STRENGTH_SQUAT_QUAD in categories)
        assertFalse(ExerciseSubcategory.STRENGTH_CORE in categories)
    }

    @Test
    fun searchQueryReducesMetadataSubcategoryCounts() {
        val row = exercise(1, "Cable row", "근력운동")
        val press = exercise(2, "Dumbbell press", "근력운동")
        val metadata = mapOf(
            1L to RuntimeExerciseMetadataDefaults.forExercise(row).copy(movementFamily = "ROW_VARIANTS"),
            2L to RuntimeExerciseMetadataDefaults.forExercise(press).copy(movementFamily = "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS")
        )

        val allCounts = ExerciseSubcategoryMapper.countsFor(listOf(row, press), "근력운동", "", metadata)
        val rowCounts = ExerciseSubcategoryMapper.countsFor(listOf(row, press), "근력운동", "row", metadata)

        assertEquals(1, allCounts[ExerciseSubcategory.STRENGTH_CHEST_PUSH])
        assertEquals(1, allCounts[ExerciseSubcategory.STRENGTH_BACK_PULL])
        assertEquals(0, rowCounts[ExerciseSubcategory.STRENGTH_CHEST_PUSH])
        assertEquals(1, rowCounts[ExerciseSubcategory.STRENGTH_BACK_PULL])
    }

    @Test
    fun insufficientStructuredMetadataFallsBackToOther() {
        val exercise = exercise(3, "Custom", "기능성운동")

        assertEquals(
            setOf(ExerciseSubcategory.FUNCTIONAL_OTHER),
            ExerciseSubcategoryMapper.categoriesFor(exercise, metadata = null)
        )
    }

    private fun exercise(id: Long, name: String, category: String) = Exercise(
        id = id,
        name = name,
        category = category,
        stableKey = "test_$id"
    )
}
