package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId

enum class AnalysisMetricCategory {
    STRENGTH,
    BADMINTON,
    FATIGUE,
    TRANSFER,
    RECOVERY,
    VOLUME,
    DERIVED
}

enum class AnalysisTimeGrain {
    DAILY,
    WEEKLY
}

data class AnalysisMetricDescriptor(
    val id: TrendMetricId,
    val displayName: String,
    val description: String,
    val category: AnalysisMetricCategory,
    val unit: String?,
    val timeGrain: AnalysisTimeGrain,
    val supportsScatter: Boolean,
    val supportsTimeSeries: Boolean,
    val supportsMultivariate: Boolean,
    val higherIsBetter: Boolean?
)
