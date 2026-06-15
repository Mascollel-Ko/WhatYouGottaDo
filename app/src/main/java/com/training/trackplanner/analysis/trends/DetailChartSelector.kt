package com.training.trackplanner.analysis.trends

object DetailChartSelector {
    fun chartTypeForMode(mode: DetailChartMode): ChartType =
        when (mode) {
            DetailChartMode.TREND -> ChartType.LINE
            DetailChartMode.COMPOSITION -> ChartType.HORIZONTAL_BAR
            DetailChartMode.CONTRIBUTION -> ChartType.BAR
            DetailChartMode.RANKING -> ChartType.HORIZONTAL_BAR
            DetailChartMode.RELATIONSHIP -> ChartType.SCATTER
        }

    fun canShowTogether(first: ChartType, second: ChartType): Boolean =
        when {
            first == ChartType.LINE && second == ChartType.LINE -> true
            first == ChartType.BAR && second == ChartType.BAR -> true
            first == ChartType.HORIZONTAL_BAR && second == ChartType.HORIZONTAL_BAR -> true
            else -> false
        }

    fun sanitizeSelection(
        mode: DetailChartMode,
        selectedMetrics: List<TrendMetricId>,
        defaults: List<TrendMetricId>
    ): List<TrendMetricId> {
        val fallback = selectedMetrics.ifEmpty { defaults }
        return when (mode) {
            DetailChartMode.TREND -> fallback
            DetailChartMode.COMPOSITION,
            DetailChartMode.CONTRIBUTION,
            DetailChartMode.RANKING,
            DetailChartMode.RELATIONSHIP -> fallback.take(1)
        }
    }
}

object PerformanceChartSelector {
    fun dashboardSeries(summary: PerformanceTrendSummary): List<CompositeTrendSeries> =
        listOf(
            summary.strengthPerformanceSeries,
            summary.badmintonTrainingSeries,
            summary.fatigueCompositeSeries
        )
}
