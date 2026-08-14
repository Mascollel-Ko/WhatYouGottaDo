package com.training.trackplanner.analysis.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreStimulusWeeklySeriesTest {
    @Test
    fun aggregatesDailyAuthorityByCanonicalWeekAndFillsMissingWeeksWithZero() {
        val points = CoreStimulusWeeklySeries.aggregate(
            listOf(
                DailyCoreStimulus(LocalDate.parse("2026-08-04"), 1.0, 2.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-06"), 1.0, 3.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-18"), 4.0, 1.0, emptyMap())
            )
        )

        assertEquals(
            listOf("2026-08-03", "2026-08-10", "2026-08-17"),
            points.map { it.weekStart.toString() }
        )
        assertEquals(listOf(2.0, 0.0, 4.0), points.map { it.direct })
        assertEquals(listOf(5.0, 0.0, 1.0), points.map { it.indirect })
        assertEquals(listOf(7.0, 0.0, 5.0), points.map { it.total })
        points.forEach { point -> assertEquals(point.direct + point.indirect, point.total, 0.0) }
    }

    @Test
    fun missingWeekDoesNotCarryPriorStimulusForward() {
        val points = CoreStimulusWeeklySeries.aggregate(
            listOf(
                DailyCoreStimulus(LocalDate.parse("2026-08-03"), 2.0, 5.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-17"), 4.0, 1.0, emptyMap())
            )
        )

        assertEquals(0.0, points[1].direct, 0.0)
        assertEquals(0.0, points[1].indirect, 0.0)
        assertEquals(0.0, points[1].total, 0.0)
    }
}
