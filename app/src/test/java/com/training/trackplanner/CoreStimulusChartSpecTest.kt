package com.training.trackplanner

import com.training.trackplanner.analysis.core.CoreStimulusSummary
import com.training.trackplanner.analysis.core.DailyCoreStimulus
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreStimulusChartSpecTest {
    @Test
    fun weeklyStackedAreaUsesIndirectBottomDirectTopAndTotalBoundary() {
        val summary = CoreStimulusSummary(
            calculationVersion = CoreStimulusSummary.VERSION,
            daily = listOf(
                DailyCoreStimulus(LocalDate.parse("2026-08-03"), 2.0, 5.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-10"), 0.0, 3.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-17"), 4.0, 1.0, emptyMap())
            )
        )
        val spec = coreStimulusChartSpec(summary)

        assertEquals(ChartType.STACKED_AREA, spec.type)
        assertEquals(ChartTimeGranularity.WEEKLY, spec.timeGranularity)
        assertEquals(listOf("CORE_INDIRECT", "CORE_DIRECT"), spec.stackedAreaLayers.map { it.seriesKey })
        assertEquals(listOf(5.0, 3.0, 1.0), spec.stackedAreaLayers[0].points.map { it.value })
        assertEquals(listOf(2.0, 0.0, 4.0), spec.stackedAreaLayers[1].points.map { it.value })
        assertEquals(listOf(7.0, 3.0, 5.0), spec.lineSeries.single().points.map { it.value })
        assertEquals(
            listOf("2026-08-03", "2026-08-10", "2026-08-17"),
            spec.xDomain.map(LocalDate::toString)
        )
        val accessibility = analysisChartContentDescription(spec)
        assertTrue(accessibility.contains("간접 코어 자극"))
        assertTrue(accessibility.contains("직접 코어 자극"))
        assertTrue(accessibility.contains("주별 코어 훈련 자극"))
    }
}
