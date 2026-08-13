package com.training.trackplanner

import com.training.trackplanner.analysis.core.CoreStimulusSummary
import com.training.trackplanner.analysis.core.CumulativeCoreStimulusPoint
import com.training.trackplanner.analysis.trends.ChartType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreStimulusChartSpecTest {
    @Test
    fun stackedAreaUsesIndirectBottomDirectTopAndTotalBoundary() {
        val summary = CoreStimulusSummary(
            calculationVersion = CoreStimulusSummary.VERSION,
            daily = emptyList(),
            cumulative = listOf(
                CumulativeCoreStimulusPoint(LocalDate.parse("2026-08-01"), 2.0, 3.0),
                CumulativeCoreStimulusPoint(LocalDate.parse("2026-08-02"), 2.0, 3.0)
            )
        )
        val spec = coreStimulusChartSpec(summary)

        assertEquals(ChartType.STACKED_AREA, spec.type)
        assertEquals(listOf("CORE_INDIRECT", "CORE_DIRECT"), spec.stackedAreaLayers.map { it.seriesKey })
        assertEquals(listOf(3.0, 3.0), spec.stackedAreaLayers[0].points.map { it.value })
        assertEquals(listOf(2.0, 2.0), spec.stackedAreaLayers[1].points.map { it.value })
        assertEquals(listOf(5.0, 5.0), spec.lineSeries.single().points.map { it.value })
        val accessibility = analysisChartContentDescription(spec)
        assertTrue(accessibility.contains("간접 코어 자극"))
        assertTrue(accessibility.contains("직접 코어 자극"))
    }
}
