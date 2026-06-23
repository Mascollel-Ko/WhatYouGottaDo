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

fun AnalysisMetricCategory.displayLabelKo(): String = when (this) {
    AnalysisMetricCategory.STRENGTH -> "근력"
    AnalysisMetricCategory.BADMINTON -> "배드민턴"
    AnalysisMetricCategory.FATIGUE -> "피로"
    AnalysisMetricCategory.TRANSFER -> "전이"
    AnalysisMetricCategory.RECOVERY -> "회복/컨디션"
    AnalysisMetricCategory.VOLUME -> "훈련량"
    AnalysisMetricCategory.DERIVED -> "파생 지표"
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
