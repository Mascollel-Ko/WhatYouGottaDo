package com.training.trackplanner.analysis.fatigue

import java.time.LocalDate

enum class FatigueAnalysisPeriod(val weeks: Int, val label: String) {
    TWO_WEEKS(2, "2주"),
    FOUR_WEEKS(4, "4주"),
    EIGHT_WEEKS(8, "8주"),
    TWELVE_WEEKS(12, "12주"),
    TWENTY_EIGHT_WEEKS(28, "28주");

    val usesWeeklyAggregation: Boolean
        get() = weeks >= 12
}

enum class FatigueTarget(val label: String) {
    OVERALL("전체 피로도"),
    NEUROMUSCULAR("신경계"),
    SYSTEMIC_MUSCULAR("전신 근육"),
    LOCAL_MUSCULAR("국소 근육"),
    JOINT_TENDON_IMPACT("관절/건/충격"),
    MOVEMENT_FOCUS("동작 집중"),
    RECOVERY_PRESSURE("회복 지속")
}

enum class ContributionGrouping(val groupType: String, val label: String) {
    REDUNDANCY_GROUP("redundancyGroup", "운동 역할"),
    MOVEMENT_FAMILY("movementFamily", "동작 계열"),
    PROGRAM_SLOT("programSlot", "프로그램 역할"),
    EXERCISE_NAME("exerciseName", "운동명")
}

data class FatigueTimePoint(
    val date: LocalDate,
    val value: Double
)

data class FatigueSeries(
    val key: String,
    val label: String,
    val points: List<FatigueTimePoint>
)

data class FatigueLoadItem(
    val key: String,
    val label: String,
    val score: Int
)

data class FatigueContributionSeries(
    val sourceKey: String,
    val sourceLabel: String,
    val target: FatigueTarget,
    val points: List<FatigueTimePoint>,
    val periodContributionPercent: Int
)

data class FatigueSimpleUiState(
    val ofiSeries: List<FatigueTimePoint> = emptyList(),
    val projectedOfiOverlay: List<FatigueTimePoint> = emptyList(),
    val highLoadItems: List<FatigueLoadItem> = emptyList(),
    val availableLoadItems: List<FatigueLoadItem> = emptyList()
)

data class FatigueDetailUiState(
    val selectedPeriod: FatigueAnalysisPeriod = FatigueAnalysisPeriod.TWO_WEEKS,
    val fatigueTrendSeries: List<FatigueSeries> = emptyList(),
    val selectedFatigueTargets: Set<FatigueTarget> = setOf(FatigueTarget.OVERALL),
    val contributionTarget: FatigueTarget = FatigueTarget.OVERALL,
    val contributionGrouping: ContributionGrouping = ContributionGrouping.REDUNDANCY_GROUP,
    val contributionSeries: List<FatigueContributionSeries> = emptyList(),
    val selectedContributionSourceKeys: Set<String> = emptySet(),
    val defaultContributionSourceKeys: Set<String> = emptySet(),
    val usesWeeklyAggregation: Boolean = false
)

data class FatigueAnalysisUiState(
    val simple: FatigueSimpleUiState = FatigueSimpleUiState(),
    val detail: FatigueDetailUiState = FatigueDetailUiState(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
