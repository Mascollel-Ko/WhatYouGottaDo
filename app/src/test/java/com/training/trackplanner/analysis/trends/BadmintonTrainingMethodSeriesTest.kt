package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BadmintonTrainingMethodSeriesTest {
    @Test
    fun weeklyStackedGroupsAggregateByWeekNotMonth() {
        val monday = LocalDate.parse("2026-06-01")
        val nextMonday = monday.plusWeeks(1)
        val groups = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            listOf(
                BadmintonDailyLoadPoint(monday, 0.0, 10.0, 0.0, mapOf("FOOTWORK" to 10.0)),
                BadmintonDailyLoadPoint(monday.plusDays(2), 0.0, 5.0, 0.0, mapOf("FOOTWORK" to 5.0)),
                BadmintonDailyLoadPoint(nextMonday, 0.0, 7.0, 0.0, mapOf("REACTION" to 7.0))
            )
        )

        assertEquals(listOf("2026-06-01", "2026-06-08"), groups.map { it.label })
        assertEquals(15.0, groups.first().segments.single().value, 0.001)
        assertEquals("리액션", groups.last().segments.single().label)
    }

    @Test
    fun totalsPreserveDuplicatedMultiLabelStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-01"), 0.0, 10.0, 0.0, mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0))
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
    }

    @Test
    fun totalsDuplicateReactionAccelerationFootworkStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-01"),
                    0.0,
                    10.0,
                    0.0,
                    mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0, "ACCELERATION" to 10.0)
                )
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
        assertEquals(10.0, totals.getValue("ACCELERATION"), 0.001)
    }
}
