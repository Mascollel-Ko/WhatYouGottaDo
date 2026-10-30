package com.training.trackplanner

import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAdvancedSelectionTest {
    private val dumbbellRow = Exercise(
        stableKey = "test.row", name = "원암 덤벨 로우", category = "근력운동",
        equipment = "덤벨", movementPattern = "HORIZONTAL_PULL",
        movementCategory = "STRENGTH", forceType = "PULL"
    )
    private val barbellPress = Exercise(
        stableKey = "test.press", name = "바벨 벤치프레스", category = "근력운동",
        equipment = "바벨", movementPattern = "HORIZONTAL_PUSH",
        movementCategory = "STRENGTH", forceType = "PUSH"
    )

    @Test fun differentFacetsUseAndAndSameFacetUsesOr() {
        val selected = ExerciseAdvancedSelection()
            .toggle(ExerciseFilterFacet.EQUIPMENT, "덤벨")
            .toggle(ExerciseFilterFacet.EQUIPMENT, "바벨")
            .toggle(ExerciseFilterFacet.FORCE, "PUSH")
        assertTrue(selected.matches(barbellPress, null, null))
        assertFalse(selected.matches(dumbbellRow, null, null))
        assertTrue(selected.toggle(ExerciseFilterFacet.FORCE, "PUSH")
            .matches(dumbbellRow, null, null))
    }

    @Test fun changingSelectionDoesNotRewriteExerciseData() {
        val selected = ExerciseAdvancedSelection()
            .toggle(ExerciseFilterFacet.CATEGORY, "근력운동")
            .toggle(ExerciseFilterFacet.PATTERN, "HORIZONTAL_PULL")
        assertTrue(selected.matches(dumbbellRow, null, null))
        assertFalse(selected.matches(barbellPress, null, null))
        assertTrue(selected.toggle(ExerciseFilterFacet.PATTERN, "HORIZONTAL_PULL")
            .matches(barbellPress, null, null))
    }
}
