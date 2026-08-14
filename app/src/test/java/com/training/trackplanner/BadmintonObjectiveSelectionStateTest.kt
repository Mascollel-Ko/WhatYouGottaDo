package com.training.trackplanner

import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodSeries
import com.training.trackplanner.analysis.trends.StackedBarGroup
import com.training.trackplanner.analysis.trends.StackedBarSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonObjectiveSelectionStateTest {
    private val allObjectives = BadmintonTrainingMethodSeries.objectiveKeys

    @Test
    fun defaultSelectionUsesAllNineCanonicalObjectivesInOrder() {
        assertEquals(9, allObjectives.size)
        assertEquals(allObjectives, defaultBadmintonMethodKeys(allObjectives))
    }

    @Test
    fun legacyTopFourSavedStateResetsOnceToAllNine() {
        val legacyState = listOf("FOOTWORK", "ACCELERATION", "REACTION", "DECELERATION").joinToString("|")

        assertEquals(allObjectives, decodeBadmintonMethodSelection(legacyState, allObjectives))
    }

    @Test
    fun currentSchemaPreservesAUserSelectedSubsetInCanonicalOrder() {
        val selected = listOf("REACTION", "FOOTWORK")

        assertEquals(
            allObjectives.filter { it in selected },
            decodeBadmintonMethodSelection(encodeBadmintonMethodSelection(selected), allObjectives)
        )
    }

    @Test
    fun badmintonObjectiveChartsPreserveZeroCategoriesAndWrapTheirLegend() {
        val groups = listOf(
            StackedBarGroup(
                label = "week",
                segments = allObjectives.map { key -> StackedBarSegment(key, 0.0, colorKey = key) }
            )
        )

        val spec = badmintonObjectiveStackedChartSpec("objectives", groups)

        assertTrue(spec.preserveZeroStackedBarCategories)
        assertTrue(spec.wrapStackedBarLegend)
        assertEquals(allObjectives, spec.stackedBars.single().segments.mapNotNull { it.colorKey })
    }
}
