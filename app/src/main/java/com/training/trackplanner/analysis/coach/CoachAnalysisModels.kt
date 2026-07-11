package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.badminton.BadmintonTransferAxis

enum class CoachFatigueCauseType {
    EXERCISE,
    MOVEMENT_AXIS,
    STRESS_TAG,
    BODY_REGION,
    RECOVERY_INPUT,
    BADMINTON_SESSION
}

data class CoachFatigueCause(
    val rank: Int,
    val label: String,
    val detail: String,
    val contributionScore: Double,
    val affectedAxes: List<String>,
    val sourceType: CoachFatigueCauseType,
    val axisContributionScores: Map<String, Double> = emptyMap()
)

data class CoachFatigueCauseSummary(
    val windowDays: Int,
    val causes: List<CoachFatigueCause>,
    val headline: String,
    val isDataSufficient: Boolean,
    val axisExerciseCauses: List<CoachFatigueCause> = causes
) {
    companion object {
        fun insufficient(windowDays: Int = 14) = CoachFatigueCauseSummary(
            windowDays = windowDays,
            causes = emptyList(),
            headline = "아직 원인 분석에 필요한 기록이 부족합니다.",
            isDataSufficient = false
        )
    }
}

enum class TransferAxisStatusType {
    MISSING,
    LOW,
    BALANCED,
    HIGH,
    OVERLOADED
}

data class BadmintonTransferAxisStatus(
    val axis: BadmintonTransferAxis,
    val label: String,
    val status: TransferAxisStatusType,
    val recentShare: Double,
    val baselineShare: Double?,
    val detail: String
)

data class BadmintonTransferCoverageSummary(
    val recentWindowDays: Int,
    val baselineWindowDays: Int,
    val statuses: List<BadmintonTransferAxisStatus>,
    val lowAxes: List<BadmintonTransferAxisStatus>,
    val cautionAxes: List<BadmintonTransferAxisStatus>,
    val headline: String,
    val isDataSufficient: Boolean
) {
    companion object {
        fun insufficient() = BadmintonTransferCoverageSummary(
            recentWindowDays = 14,
            baselineWindowDays = 28,
            statuses = emptyList(),
            lowAxes = emptyList(),
            cautionAxes = emptyList(),
            headline = "아직 배드민턴 전이 축을 판단할 기록이 부족합니다.",
            isDataSufficient = false
        )
    }
}

data class CoachAnalysisInsightSummary(
    val fatigueCauses: CoachFatigueCauseSummary,
    val transferCoverage: BadmintonTransferCoverageSummary,
    val combinedHeadline: String?,
    val checkInGuidance: List<String> = emptyList()
) {
    companion object {
        fun empty() = CoachAnalysisInsightSummary(
            fatigueCauses = CoachFatigueCauseSummary.insufficient(),
            transferCoverage = BadmintonTransferCoverageSummary.insufficient(),
            combinedHeadline = null,
            checkInGuidance = emptyList()
        )
    }
}
