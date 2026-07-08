package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.readiness.AnalysisConfidence

enum class BadmintonTransferType(val displayName: String) {
    DIRECT("직접 전이"),
    SUPPORTIVE("보조 전이"),
    GENERAL_STRENGTH("기초근력"),
    LOW("낮은 전이"),
    NONE("제외")
}

enum class BadmintonTransferAxis(val displayName: String) {
    DECELERATION_LANDING("감속·착지 제어"),
    UNILATERAL_STABILITY("편측 안정성"),
    LATERAL_MOVEMENT("측면 이동"),
    ROTATION_CONTROL("회전 제어"),
    RACKET_SUPPORT("라켓 보조"),
    AEROBIC_FOOTWORK("풋워크 지속성"),
    LOW_FATIGUE_CONTROL("저피로 제어")
}

enum class BadmintonTransferFatigueCost {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class BadmintonTransferCautionLevel {
    NORMAL,
    HIGH,
    VERY_HIGH
}

enum class BadmintonTransferDetailChartMode(val displayName: String) {
    AXIS_SHARE("전이축 비중"),
    TRANSFER_TYPE_SHARE("전이유형 비중"),
    WINDOW_COMPARISON("최근 7일 vs 28일"),
    TOP_EXERCISES("운동별 전이 자극 Top 5")
}

data class BadmintonTransferExerciseStimulus(
    val exerciseId: Long,
    val exerciseName: String,
    val stimulus: Double,
    val transferType: BadmintonTransferType,
    val axes: Set<BadmintonTransferAxis>
)

data class BadmintonTransferBarItem(
    val label: String,
    val value: Double,
    val valueLabel: String,
    val colorKey: String? = null
)

data class BadmintonTransferChartData(
    val axisShareBars: List<BadmintonTransferBarItem>,
    val transferTypeShareBars: List<BadmintonTransferBarItem>,
    val windowComparisonBars: List<BadmintonTransferBarItem>,
    val topExerciseBars: List<BadmintonTransferBarItem>
)

data class BadmintonTransferScoreSnapshot(
    val totalTransferStimulus7d: Double,
    val totalTransferStimulus28d: Double,
    val transferRatio7dTo28dAverage: Double?,
    val axisStimulus7d: Map<BadmintonTransferAxis, Double>,
    val axisStimulus28d: Map<BadmintonTransferAxis, Double>,
    val transferTypeStimulus7d: Map<BadmintonTransferType, Double>,
    val topTransferExercises7d: List<BadmintonTransferExerciseStimulus>,
    val sampleEntryCount7d: Int,
    val sampleEntryCount28d: Int
) {
    val axisShare7d: Map<BadmintonTransferAxis, Double>
        get() = axisStimulus7d.toShareMap(totalTransferStimulus7d)

    val axisShare28d: Map<BadmintonTransferAxis, Double>
        get() = axisStimulus28d.toShareMap(totalTransferStimulus28d)

    val transferTypeShare7d: Map<BadmintonTransferType, Double>
        get() = transferTypeStimulus7d.toShareMap(totalTransferStimulus7d)

    private fun <T> Map<T, Double>.toShareMap(total: Double): Map<T, Double> =
        mapValues { (_, value) ->
            if (total <= BadmintonTransferConstants.EPSILON) 0.0 else value / total
        }
}

data class BadmintonTransferWindowSnapshot(
    val windowDays: Int,
    val totalStimulus: Double,
    val axisStimulus: Map<BadmintonTransferAxis, Double>,
    val axisEntryCounts: Map<BadmintonTransferAxis, Int>,
    val sampleEntryCount: Int
) {
    val axisShare: Map<BadmintonTransferAxis, Double>
        get() = axisStimulus.mapValues { (_, value) ->
            if (totalStimulus <= BadmintonTransferConstants.EPSILON) 0.0 else value / totalStimulus
        }
}

data class BadmintonTransferRecommendation(
    val recommendedAxis: BadmintonTransferAxis?,
    val recommendationSentence: String,
    val cautionLevel: BadmintonTransferCautionLevel,
    val recommendedExerciseCandidates: List<String>
)

data class BadmintonTransferMetrics(
    val totalTransferStimulus7d: Double,
    val totalTransferStimulus28d: Double,
    val transferRatio7dTo28dAverage: Double?,
    val axisShare7d: Map<BadmintonTransferAxis, Double>,
    val axisShare28d: Map<BadmintonTransferAxis, Double>,
    val transferTypeShare7d: Map<BadmintonTransferType, Double>,
    val topTransferExercises7d: List<BadmintonTransferExerciseStimulus>,
    val recommendedAxis: BadmintonTransferAxis?,
    val recommendationSentence: String,
    val cautionLevel: BadmintonTransferCautionLevel,
    val detailInsightText: String,
    val recommendedExerciseCandidates: List<String>
)

data class BadmintonTransferSummary(
    val metrics: BadmintonTransferMetrics,
    val chartData: BadmintonTransferChartData,
    val confidence: AnalysisConfidence
)
