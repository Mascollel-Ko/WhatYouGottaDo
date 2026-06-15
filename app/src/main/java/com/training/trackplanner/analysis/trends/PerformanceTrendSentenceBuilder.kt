package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence

class PerformanceTrendSentenceBuilder {
    fun dashboardSentence(
        strength: List<TrendDataPoint>,
        badminton: List<TrendDataPoint>,
        fatigue: List<TrendDataPoint>,
        confidence: AnalysisConfidence
    ): String {
        if (confidence == AnalysisConfidence.LOW || strength.countValues() < 4) {
            return "기록이 더 쌓이면 추세 판단이 안정됩니다."
        }
        val strengthTrend = trendLabel(strength)
        val badmintonTrend = trendLabel(badminton)
        val fatigueTrend = trendLabel(fatigue)
        return "근력운동은 $strengthTrend, 배드민턴 훈련량은 $badmintonTrend, 피로도는 $fatigueTrend 흐름입니다."
            .safeTrendSentence()
    }

    fun strengthInterpretation(latest: StrengthWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 근력운동 흐름을 볼 수 있습니다."
        } else {
            "최근 근력운동 퍼포먼스는 강도와 수행량의 상대 변화로 계산됩니다."
        }

    fun badmintonInterpretation(latest: BadmintonWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 배드민턴 관련 수행량을 볼 수 있습니다."
        } else {
            "배드민턴 훈련량은 코트 수행량, 풋워크/반응, 보조훈련량을 합친 흐름입니다."
        }

    fun fatigueInterpretation(latest: FatigueWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 피로 부담 흐름을 볼 수 있습니다."
        } else {
            "피로도 종합지수는 개인 기준선 대비 부담을 차트용으로 압축한 값입니다."
        }

    private fun trendLabel(points: List<TrendDataPoint>): String {
        val values = points.mapNotNull { point -> point.value }.takeLast(4)
        if (values.size < 2) return "유지권"
        val delta = values.last() - values.first()
        return when {
            delta > PerformanceTrendConstants.TREND_STABLE_DELTA -> "상승"
            delta < -PerformanceTrendConstants.TREND_STABLE_DELTA -> "하락"
            else -> "유지권"
        }
    }

    private fun List<TrendDataPoint>.countValues(): Int =
        count { point -> point.value != null }

    private fun String.safeTrendSentence(): String {
        val banned = listOf(
            "부상 위험",
            "다칠 수 있습니다",
            "과훈련",
            "위험합니다",
            "진단",
            "실력 향상",
            "때문에",
            "원인입니다",
            "확실합니다"
        )
        require(banned.none { word -> contains(word) }) {
            "Performance trend sentence contains a prohibited expression."
        }
        return this
    }
}

internal fun TrendMetricId.label(): String =
    when (this) {
        TrendMetricId.STRENGTH_PERFORMANCE -> "근력운동 퍼포먼스"
        TrendMetricId.STRENGTH_INTENSITY -> "강도"
        TrendMetricId.STRENGTH_VOLUME -> "수행량"
        TrendMetricId.STRENGTH_EFFICIENCY -> "효율"
        TrendMetricId.BADMINTON_TRAINING -> "배드민턴 훈련량"
        TrendMetricId.COURT_VOLUME -> "코트 수행량"
        TrendMetricId.FOOTWORK_REACTIVE -> "풋워크/반응"
        TrendMetricId.BADMINTON_SUPPORT -> "보조훈련량"
        TrendMetricId.FATIGUE_COMPOSITE -> "피로도 종합지수"
        TrendMetricId.SYSTEMIC_FATIGUE -> "전신 부담"
        TrendMetricId.STRENGTH_FATIGUE -> "근력운동 부담"
        TrendMetricId.BADMINTON_FATIGUE -> "배드민턴 부담"
        TrendMetricId.LOCAL_BODY_PART_FATIGUE -> "국소/부위 부담"
        TrendMetricId.RECOVERY_PERFORMANCE_PENALTY -> "회복/수행 보정"
        TrendMetricId.STRENGTH_DELTA_NEXT -> "다음 근력 변화"
        TrendMetricId.FATIGUE_DELTA_NEXT -> "다음 피로 변화"
        TrendMetricId.STRENGTH_VOLUME_ONLY -> "근력운동 수행량"
        TrendMetricId.STRENGTH_INTENSITY_ONLY -> "근력운동 강도"
    }
