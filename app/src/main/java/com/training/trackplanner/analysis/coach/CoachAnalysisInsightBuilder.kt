package com.training.trackplanner.analysis.coach

object CoachAnalysisInsightBuilder {
    fun combine(
        fatigue: CoachFatigueCauseSummary,
        transfer: BadmintonTransferCoverageSummary
    ): CoachAnalysisInsightSummary {
        val headline = when {
            fatigue.isDataSufficient && transfer.cautionAxes.isNotEmpty() ->
                "${fatigue.causes.first().label} 관련 피로가 많이 누적되었고, ${transfer.cautionAxes.first().label} 전이축도 주의 수준입니다."
            fatigue.isDataSufficient && transfer.lowAxes.isNotEmpty() ->
                "${fatigue.causes.first().label} 관련 피로가 많이 누적되었으며, ${transfer.lowAxes.first().label} 자극은 부족합니다."
            fatigue.isDataSufficient -> fatigue.headline
            transfer.isDataSufficient -> transfer.headline
            else -> null
        }
        return CoachAnalysisInsightSummary(fatigue, transfer, headline)
    }
}
