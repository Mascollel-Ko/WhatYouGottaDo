package com.training.trackplanner.analysis.coach

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

data class CoachAnalysisInsightSummary(
    val fatigueCauses: CoachFatigueCauseSummary,
    val combinedHeadline: String?,
    val checkInGuidance: List<String> = emptyList()
) {
    companion object {
        fun empty() = CoachAnalysisInsightSummary(
            fatigueCauses = CoachFatigueCauseSummary.insufficient(),
            combinedHeadline = null,
            checkInGuidance = emptyList()
        )
    }
}
