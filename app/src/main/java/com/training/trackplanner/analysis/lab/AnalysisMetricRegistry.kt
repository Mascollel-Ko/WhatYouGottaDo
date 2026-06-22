package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

object AnalysisMetricRegistry {
    val descriptors: List<AnalysisMetricDescriptor> = listOf(
        metric(TrendMetricId.STRENGTH_PERFORMANCE, "근력운동 퍼포먼스", "강도, 수행량, 효율을 합친 주간 근력 지수", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.STRENGTH_INTENSITY, "강도", "주간 근력운동 강도 지수", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.STRENGTH_VOLUME, "수행량", "주간 유효 근력운동 수행량", AnalysisMetricCategory.VOLUME, true),
        metric(TrendMetricId.STRENGTH_EFFICIENCY, "RPE 대비 운동량", "자각 강도 대비 수행 효율", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.BADMINTON_TRAINING, "배드민턴 훈련량", "코트, 풋워크, 보조훈련을 합친 주간 지수", AnalysisMetricCategory.BADMINTON, true),
        metric(TrendMetricId.COURT_VOLUME, "셔틀 플레이 시간", "주간 코트 훈련량", AnalysisMetricCategory.BADMINTON, true),
        metric(TrendMetricId.FOOTWORK_REACTIVE, "풋워크/반응", "주간 풋워크 및 반응 훈련량", AnalysisMetricCategory.TRANSFER, true),
        metric(TrendMetricId.BADMINTON_SUPPORT, "보조훈련량", "배드민턴 지원 목적의 주간 훈련량", AnalysisMetricCategory.TRANSFER, true),
        metric(TrendMetricId.FATIGUE_COMPOSITE, "피로도 종합지수", "주간 피로 부담의 종합 지수", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.SYSTEMIC_FATIGUE, "전신 부담", "주간 전신성 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.STRENGTH_FATIGUE, "근력운동 부담", "근력운동에서 발생한 주간 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.BADMINTON_FATIGUE, "배드민턴 부담", "배드민턴 훈련에서 발생한 주간 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.LOCAL_BODY_PART_FATIGUE, "국소/부위 부담", "가장 높은 국소 부위의 주간 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.RECOVERY_PERFORMANCE_PENALTY, "회복/수행 보정", "회복 상태와 수행 저하를 반영한 주간 지수", AnalysisMetricCategory.RECOVERY, false),
        metric(TrendMetricId.STRENGTH_DELTA_NEXT, "다음 근력 변화", "다음 주 근력 지수의 변화량", AnalysisMetricCategory.DERIVED, null),
        metric(TrendMetricId.FATIGUE_DELTA_NEXT, "다음 피로 변화", "다음 주 피로 지수의 변화량", AnalysisMetricCategory.DERIVED, null)
    )

    fun descriptor(id: TrendMetricId): AnalysisMetricDescriptor? =
        descriptors.firstOrNull { descriptor -> descriptor.id == id }

    fun scatterMetrics(
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        requireAvailableData: Boolean = true
    ): List<AnalysisMetricDescriptor> = descriptors.filter { descriptor ->
        descriptor.supportsScatter && (!requireAvailableData || metricSeries[descriptor.id].orEmpty().any { it.value != null })
    }

    private fun metric(
        id: TrendMetricId,
        displayName: String,
        description: String,
        category: AnalysisMetricCategory,
        higherIsBetter: Boolean?
    ) = AnalysisMetricDescriptor(
        id = id,
        displayName = displayName,
        description = description,
        category = category,
        unit = "지수",
        timeGrain = AnalysisTimeGrain.WEEKLY,
        supportsScatter = true,
        supportsTimeSeries = true,
        supportsMultivariate = true,
        higherIsBetter = higherIsBetter
    )
}
