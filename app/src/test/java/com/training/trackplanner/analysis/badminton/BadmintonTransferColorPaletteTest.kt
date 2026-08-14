package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.trends.BadmintonDailyLoadPoint
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodSeries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BadmintonTransferColorPaletteTest {
    @Test
    fun canonicalObjectivesUseDistinctColors() {
        val colors = BadmintonTrainingMethodSeries.objectiveKeys
            .map(BadmintonTransferColorPalette::colorForKey)

        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun canonicalObjectiveColorMappingIsStableAndCaseInsensitive() {
        val keys = BadmintonTrainingMethodSeries.objectiveKeys
        val forward = keys.associateWith(BadmintonTransferColorPalette::colorForKey)
        val reversed = keys.asReversed().associateWith(BadmintonTransferColorPalette::colorForKey)

        assertEquals(forward, reversed)
        keys.forEach { key ->
            assertEquals(forward.getValue(key), BadmintonTransferColorPalette.colorForKey(key.lowercase()))
        }
    }

    @Test
    fun canonicalObjectiveLegendUsesTheChartColorKey() {
        val points = listOf(
            BadmintonDailyLoadPoint(
                date = LocalDate.parse("2026-07-01"),
                courtRaw = 0.0,
                footworkReactiveRaw = 0.0,
                supportRaw = 0.0,
                objectiveStimulus = mapOf("FOOTWORK" to 10.0, "REACTION" to 6.0)
            )
        )
        val recent = BadmintonTrainingMethodSeries.recentComparisonGroups(points)
            .flatMap { group -> group.segments }
        val weekly = BadmintonTrainingMethodSeries.weeklyStackedGroups(points)
            .flatMap { group -> group.segments }

        recent.forEach { segment ->
            val weeklySegment = weekly.single { it.label == segment.label }
            assertEquals(segment.colorKey, weeklySegment.colorKey)
            assertEquals(
                BadmintonTransferColorPalette.colorForKey(segment.colorKey.orEmpty()),
                BadmintonTransferColorPalette.colorForKey(weeklySegment.colorKey.orEmpty())
            )
        }
    }
}
