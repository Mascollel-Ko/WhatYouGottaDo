package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.core.CoreStimulusSummary
import com.training.trackplanner.analysis.core.CoreStimulusWeeklySeries
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

object CoreStimulusMetricSeriesBuilder {
    fun build(summary: CoreStimulusSummary): Map<TrendMetricId, List<TrendDataPoint>> {
        val weekly = CoreStimulusWeeklySeries.aggregate(summary.daily)
        return mapOf(
            TrendMetricId.CORE_TOTAL_STIMULUS_WEEKLY to weekly.map { point ->
                TrendDataPoint(point.weekStart, point.total)
            },
            TrendMetricId.CORE_DIRECT_STIMULUS_WEEKLY to weekly.map { point ->
                TrendDataPoint(point.weekStart, point.direct)
            },
            TrendMetricId.CORE_INDIRECT_STIMULUS_WEEKLY to weekly.map { point ->
                TrendDataPoint(point.weekStart, point.indirect)
            }
        )
    }
}
