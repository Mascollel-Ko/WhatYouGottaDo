package com.training.trackplanner.analysis.fatigue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeMiniChartSeriesBuilderTest {
    @Test
    fun projectedSeriesKeepsHistoryAndReplacesOnlyToday() {
        val today = LocalDate.of(2026, 6, 20)
        val current = listOf(
            MiniTrendPoint(today.minusDays(2), 20.0),
            MiniTrendPoint(today.minusDays(1), 30.0),
            MiniTrendPoint(today, 12.0)
        )

        val projected = HomeMiniChartSeriesBuilder.projected(current, 48.0)!!

        assertEquals(current.size, projected.size)
        assertEquals(current.take(2), projected.take(2))
        assertEquals(today, projected.last().date)
        assertEquals(48.0, projected.last().value, 0.0)
    }

    @Test
    fun noMeaningfulProjectionReturnsNoSecondSeries() {
        val point = MiniTrendPoint(LocalDate.of(2026, 6, 20), 42.0)

        assertNull(HomeMiniChartSeriesBuilder.projected(emptyList(), 61.0))
        assertNull(HomeMiniChartSeriesBuilder.projected(listOf(point), null))
        assertNull(HomeMiniChartSeriesBuilder.projected(listOf(point), 42.0))
    }
}
