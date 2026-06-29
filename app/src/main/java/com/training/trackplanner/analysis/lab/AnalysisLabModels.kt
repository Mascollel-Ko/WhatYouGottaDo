package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId

enum class AnalysisMetricCategory {
    PERFORMANCE,
    STRENGTH,
    BADMINTON,
    FATIGUE,
    TRANSFER,
    RECOVERY,
    VOLUME,
    MUSCLE_LOAD,
    SMASH_SPEED,
    DERIVED
}

fun AnalysisMetricCategory.displayLabelKo(): String = when (this) {
    AnalysisMetricCategory.PERFORMANCE -> "성과"
    AnalysisMetricCategory.STRENGTH -> "근력"
    AnalysisMetricCategory.BADMINTON -> "배드민턴"
    AnalysisMetricCategory.FATIGUE -> "피로/회복"
    AnalysisMetricCategory.TRANSFER -> "배드민턴 전이"
    AnalysisMetricCategory.RECOVERY -> "회복/컨디션"
    AnalysisMetricCategory.VOLUME -> "전체 운동량"
    AnalysisMetricCategory.MUSCLE_LOAD -> "근육군별 운동량"
    AnalysisMetricCategory.SMASH_SPEED -> "스매시 속도"
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
