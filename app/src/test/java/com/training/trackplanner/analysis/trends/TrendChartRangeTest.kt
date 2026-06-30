package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartRangeTest {
    @Test
    fun percentReturnsNullForEmptyValues() {
        assertNull(TrendChartRange.percent(emptyList()))
    }

    @Test
    fun percentZoomsToDataInsteadOfAlwaysUsingZeroToHundred() {
        val range = TrendChartRange.percent(listOf(32.0, 36.0, 41.0)) ?: error("range missing")

        assertTrue("lower should stay near the data instead of 0: $range", range.first > 20.0)
        assertTrue("upper should stay near the data instead of 100: $range", range.second < 55.0)
    }

    @Test
    fun percentKeepsBoundariesWithinPercentScale() {
        assertEquals(0.0, TrendChartRange.percent(listOf(0.0))!!.first, 0.001)
        assertEquals(100.0, TrendChartRange.percent(listOf(100.0))!!.second, 0.001)
    }
}
