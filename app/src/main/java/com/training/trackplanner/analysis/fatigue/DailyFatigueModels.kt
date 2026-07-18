package com.training.trackplanner.analysis.fatigue

import java.time.LocalDate

enum class FatigueReadinessLabel {
    LOW,
    NORMAL,
    ELEVATED,
    CAUTION,
    HIGH_FATIGUE
}

enum class FatigueConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class FatigueAxisValues(
    val highForceNeural: Double = 0.0,
    val systemicMuscular: Double = 0.0,
    val localMuscular: Double = 0.0,
    val highSpeed: Double = 0.0,
    val reactive: Double = 0.0,
    val recoveryPressure: Double = 0.0
) {
    fun map(transform: (Double) -> Double): FatigueAxisValues =
        FatigueAxisValues(
            highForceNeural = transform(highForceNeural),
            systemicMuscular = transform(systemicMuscular),
            localMuscular = transform(localMuscular),
            highSpeed = transform(highSpeed),
            reactive = transform(reactive),
            recoveryPressure = transform(recoveryPressure)
        )

    fun values(): List<Double> = listOf(
        highForceNeural,
        systemicMuscular,
        localMuscular,
        highSpeed,
        reactive,
        recoveryPressure
    )

    operator fun plus(other: FatigueAxisValues): FatigueAxisValues =
        FatigueAxisValues(
            highForceNeural + other.highForceNeural,
            systemicMuscular + other.systemicMuscular,
            localMuscular + other.localMuscular,
            highSpeed + other.highSpeed,
            reactive + other.reactive,
            recoveryPressure + other.recoveryPressure
        )
}

data class RecordFatigueContribution(
    val date: LocalDate,
    val stableKey: String,
    val exerciseName: String,
    val trainingLoad: Double,
    val axes: FatigueAxisValues,
    val recoveryDurationClass: String,
    val strengthProgressionGroup: String,
    val redundancyGroup: String,
    val movementFamily: String,
    val programSlot: String,
    val confidence: FatigueConfidence
)

data class GroupFatigueState(
    val date: LocalDate,
    val groupType: String,
    val groupKey: String,
    val highForceNeuralFatigue: Double = 0.0,
    val systemicMuscularFatigue: Double = 0.0,
    val localFatigue: Double,
    val highSpeedFatigue: Double,
    val reactiveFatigue: Double,
    val recoveryPressure: Double
)

data class DailyFatigueState(
    val date: LocalDate,
    val highForceNeuralFatigue: Double,
    val systemicMuscularFatigue: Double,
    val localMuscularFatigue: Double,
    val highSpeedFatigue: Double,
    val reactiveFatigue: Double,
    val recoveryPressure: Double,
    val highForceNeuralScore: Int,
    val systemicMuscularScore: Int,
    val localMuscularScore: Int,
    val highSpeedScore: Int,
    val reactiveScore: Int,
    val recoveryPressureScore: Int,
    val overallFatigueIndex: Int,
    val readinessLabel: FatigueReadinessLabel,
    val cautionReasons: List<String>,
    val confidence: FatigueConfidence,
    val confirmedTrainingLoad: Double = 0.0
)

data class DailyFatigueResult(
    val state: DailyFatigueState,
    val groupStates: List<GroupFatigueState>,
    val recordContributions: List<RecordFatigueContribution>
)

data class FatigueBaselineSeed(
    val axes: FatigueAxisValues,
    val ofi: Double
)

data class EffectiveFatigueBaseline(
    val axes: FatigueAxisValues,
    val seedWeight: Double,
    val observedDayCount: Int,
    val confidence: FatigueConfidence
)

data class MiniTrendPoint(
    val date: LocalDate,
    val value: Double
)

data class HomeFatigueReading(
    val score: Int,
    val label: String
)

data class HomeFatigueCardSummary(
    val primaryPrefix: String,
    val primary: HomeFatigueReading,
    val projectionPrefix: String? = null,
    val projection: HomeFatigueReading? = null,
    val statusText: String? = null,
    val axisMessage: String? = null,
    val levelCountMessage: String? = null,
    val phaseLabel: String? = null,
    val headline: String? = null,
    val detail: String? = null,
    val actionLabel: String? = null
)

data class HomeTodaySummaryState(
    val date: LocalDate,
    val plannedExerciseCount: Int,
    val confirmedSetCount: Int,
    val unconfirmedSetCount: Int,
    val fatigueLabel: FatigueReadinessLabel,
    val fatigueScore: Int?,
    val fatigueHeadline: String,
    val fatigueCard: HomeFatigueCardSummary,
    val cautionReasons: List<String>,
    val recentTrainingLoadSeries: List<MiniTrendPoint>,
    val projectedTrainingLoadSeries: List<MiniTrendPoint>?,
    val recentFatigueSeries: List<MiniTrendPoint>,
    val projectedFatigueSeries: List<MiniTrendPoint>?,
    val confidence: FatigueConfidence,
    val projectedFatigueScore: Int? = null
) {
    companion object {
        fun empty(date: LocalDate = LocalDate.now()): HomeTodaySummaryState =
            HomeTodaySummaryState(
                date = date,
                plannedExerciseCount = 0,
                confirmedSetCount = 0,
                unconfirmedSetCount = 0,
                fatigueLabel = FatigueReadinessLabel.LOW,
                fatigueScore = null,
                fatigueHeadline = "피로도를 계산하고 있습니다.",
                fatigueCard = HomeFatigueCardSummary(
                    primaryPrefix = "운동 전",
                    primary = HomeFatigueReading(0, "계산 중")
                ),
                cautionReasons = emptyList(),
                recentTrainingLoadSeries = emptyList(),
                projectedTrainingLoadSeries = null,
                recentFatigueSeries = emptyList(),
                projectedFatigueSeries = null,
                confidence = FatigueConfidence.LOW
            )
    }
}
