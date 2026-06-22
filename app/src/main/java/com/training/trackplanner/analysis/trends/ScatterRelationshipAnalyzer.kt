package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import kotlin.math.abs

class ScatterRelationshipAnalyzer {
    fun analyze(
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): ScatterAnalysisResult {
        if (xMetric == yMetric) {
            return ScatterAnalysisResult(
                xMetric = xMetric,
                yMetric = yMetric,
                dataPoints = emptyList(),
                correlation = null,
                interpretation = "X축과 Y축은 서로 다른 지표를 선택하세요.",
                confidence = AnalysisConfidence.LOW,
                dataSufficiency = "동일 지표"
            )
        }
        val xByWeek = metricSeries[xMetric].orEmpty().associateBy { point -> point.weekStart }
        val yByWeek = metricSeries[yMetric].orEmpty().associateBy { point -> point.weekStart }
        val commonWeeks = xByWeek.keys.intersect(yByWeek.keys).sorted()
        val paired = commonWeeks.mapNotNull { week ->
            val xValue = xByWeek[week]?.value ?: return@mapNotNull null
            val yValue = yByWeek[week]?.value ?: return@mapNotNull null
            ScatterPoint(
                x = xValue,
                y = yValue,
                label = week.toString()
            )
        }
        val correlation = TrendMath.pearson(
            xs = paired.map { point -> point.x },
            ys = paired.map { point -> point.y }
        )
        val confidence = when {
            paired.size >= 20 && correlation != null -> AnalysisConfidence.MEDIUM
            paired.size >= PerformanceTrendConstants.SCATTER_MIN_POINTS && correlation != null ->
                AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }
        return ScatterAnalysisResult(
            xMetric = xMetric,
            yMetric = yMetric,
            dataPoints = paired,
            correlation = correlation,
            interpretation = interpretation(paired.size, correlation, xMetric, yMetric),
            confidence = confidence,
            dataSufficiency = when {
                paired.size < PerformanceTrendConstants.SCATTER_MIN_POINTS -> "기록 부족"
                paired.size < 12 -> "참고용 · 소표본"
                paired.size < 20 -> "낮은 신뢰도 · 우연 상관 주의"
                paired.size < 30 -> "보통 신뢰도 · 패턴 탐색"
                else -> "상대적으로 안정적 · 인과관계 아님"
            }
        )
    }

    private fun interpretation(
        pointCount: Int,
        correlation: Double?,
        xMetric: TrendMetricId,
        yMetric: TrendMetricId
    ): String {
        if (pointCount < PerformanceTrendConstants.SCATTER_MIN_POINTS || correlation == null) {
            return "아직 기록이 부족해 이 관계를 판단하기 어렵습니다."
        }
        val strength = when {
            abs(correlation) < 0.20 -> "뚜렷한 관계는 약합니다."
            abs(correlation) < 0.40 -> "약한 경향이 있습니다."
            abs(correlation) < 0.60 -> "중간 정도의 경향이 있습니다."
            else -> "비교적 뚜렷한 경향이 있습니다."
        }
        val sampleWarning = when {
            pointCount < 12 -> "표본이 작아 참고용으로만 보세요."
            pointCount < 20 -> "우연히 나타난 관계일 수 있어 낮은 신뢰도로 해석해야 합니다."
            pointCount < 30 -> "같은 주에 함께 움직이는 패턴을 탐색한 결과입니다."
            else -> "표본은 비교적 안정적이지만 인과관계를 뜻하지 않습니다."
        }
        return "${xMetric.label()}와 ${yMetric.label()}는 같은 주에 함께 움직이는 경향을 보입니다. $strength $sampleWarning"
    }
}
