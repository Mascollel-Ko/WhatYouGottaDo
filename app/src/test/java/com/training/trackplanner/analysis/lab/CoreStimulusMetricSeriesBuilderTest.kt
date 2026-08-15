package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.core.CoreStimulusSummary
import com.training.trackplanner.analysis.core.CoreStimulusWeeklySeries
import com.training.trackplanner.analysis.core.DailyCoreStimulus
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreStimulusMetricSeriesBuilderTest {
    @Test
    fun labMetricsExactlyMatchStrengthUiWeeklyProjection() {
        val summary = CoreStimulusSummary(
            calculationVersion = CoreStimulusSummary.VERSION,
            daily = listOf(
                DailyCoreStimulus(LocalDate.parse("2026-08-04"), 1.0, 2.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-06"), 2.0, 3.0, emptyMap()),
                DailyCoreStimulus(LocalDate.parse("2026-08-18"), 4.0, 1.0, emptyMap())
            )
        )
        val uiWeeks = CoreStimulusWeeklySeries.aggregate(summary.daily)
        val lab = CoreStimulusMetricSeriesBuilder.build(summary)

        assertEquals(uiWeeks.map { it.weekStart }, lab.getValue(TrendMetricId.CORE_TOTAL_STIMULUS_WEEKLY).map { it.weekStart })
        assertEquals(uiWeeks.map { it.total }, lab.getValue(TrendMetricId.CORE_TOTAL_STIMULUS_WEEKLY).map { it.value })
        assertEquals(uiWeeks.map { it.direct }, lab.getValue(TrendMetricId.CORE_DIRECT_STIMULUS_WEEKLY).map { it.value })
        assertEquals(uiWeeks.map { it.indirect }, lab.getValue(TrendMetricId.CORE_INDIRECT_STIMULUS_WEEKLY).map { it.value })
    }
}
