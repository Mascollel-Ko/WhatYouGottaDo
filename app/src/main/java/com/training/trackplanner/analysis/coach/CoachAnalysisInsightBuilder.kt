package com.training.trackplanner.analysis.coach

object CoachAnalysisInsightBuilder {
    fun combine(
        fatigue: CoachFatigueCauseSummary,
        checkInGuidance: List<String> = emptyList()
    ): CoachAnalysisInsightSummary {
        val headline = fatigue.headline.takeIf { fatigue.isDataSufficient }
        return CoachAnalysisInsightSummary(fatigue, headline, checkInGuidance)
    }
}
