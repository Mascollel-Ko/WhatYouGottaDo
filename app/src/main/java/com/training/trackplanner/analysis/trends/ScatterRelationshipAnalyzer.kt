package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import kotlin.math.abs

class ScatterRelationshipAnalyzer {
    fun analyze(
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): ScatterAnalysisResult {
        val xPoints = metricSeries[xMetric].orEmpty()
        val yPoints = metricSeries[yMetric].orEmpty()
        val paired = xPoints.zip(yPoints).mapNotNull { (x, y) ->
            val xValue = x.value ?: return@mapNotNull null
            val yValue = y.value ?: return@mapNotNull null
            ScatterPoint(
                x = xValue,
                y = yValue,
                label = x.weekStart.toString()
            )
        }
        val correlation = TrendMath.pearson(
            xs = paired.map { point -> point.x },
            ys = paired.map { point -> point.y }
        )
        val confidence = when {
            paired.size >= 12 && correlation != null -> AnalysisConfidence.MEDIUM
            paired.size >= PerformanceTrendConstants.SCATTER_MIN_POINTS -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }
        return ScatterAnalysisResult(
            xMetric = xMetric,
            yMetric = yMetric,
            dataPoints = paired,
            correlation = correlation,
            interpretation = interpretation(paired.size, correlation, xMetric, yMetric),
            confidence = confidence,
            dataSufficiency = if (paired.size >= PerformanceTrendConstants.SCATTER_MIN_POINTS) {
                "분석 가능"
            } else {
                "기록 부족"
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
            return "아직 기록이 부족해 뚜렷한 관계를 판단하기 어렵습니다."
        }
        val strength = when {
            abs(correlation) < 0.20 -> "뚜렷한 관계는 약합니다."
            abs(correlation) < 0.40 -> "약한 경향이 있습니다."
            abs(correlation) < 0.60 -> "중간 정도의 경향이 있습니다."
            else -> "비교적 뚜렷한 경향이 있습니다."
        }
        return "${xMetric.label()}와 ${yMetric.label()}는 함께 나타나는 편입니다. $strength"
    }
}
