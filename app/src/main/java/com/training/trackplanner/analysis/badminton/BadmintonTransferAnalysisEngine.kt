package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class BadmintonTransferAnalysisEngine(
    runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
    private val scoreCalculator: BadmintonTransferScoreCalculator =
        BadmintonTransferScoreCalculator(runtimeMetadataCatalog),
    private val recommendationBuilder: BadmintonTransferRecommendationBuilder = BadmintonTransferRecommendationBuilder(),
    private val insightBuilder: BadmintonTransferInsightBuilder = BadmintonTransferInsightBuilder(),
    private val chartDataBuilder: BadmintonTransferChartDataBuilder = BadmintonTransferChartDataBuilder()
) {
    fun analyze(
        today: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        readinessSummary: TodayReadinessSummary?
    ): BadmintonTransferSummary {
        val snapshot = scoreCalculator.calculate(today, exercises, entriesWithSets)
        val recommendation = recommendationBuilder.build(snapshot, readinessSummary, exercises)
        val metrics = BadmintonTransferMetrics(
            totalTransferStimulus7d = snapshot.totalTransferStimulus7d,
            totalTransferStimulus28d = snapshot.totalTransferStimulus28d,
            transferRatio7dTo28dAverage = snapshot.transferRatio7dTo28dAverage,
            axisShare7d = snapshot.axisShare7d,
            axisShare28d = snapshot.axisShare28d,
            transferTypeShare7d = snapshot.transferTypeShare7d,
            topTransferExercises7d = snapshot.topTransferExercises7d,
            recommendedAxis = recommendation.recommendedAxis,
            recommendationSentence = recommendation.recommendationSentence,
            cautionLevel = recommendation.cautionLevel,
            detailInsightText = insightBuilder.build(snapshot),
            recommendedExerciseCandidates = recommendation.recommendedExerciseCandidates
        )

        return BadmintonTransferSummary(
            metrics = metrics,
            chartData = chartDataBuilder.build(metrics),
            confidence = confidence(snapshot, readinessSummary)
        )
    }

    private fun confidence(
        snapshot: BadmintonTransferScoreSnapshot,
        readinessSummary: TodayReadinessSummary?
    ): AnalysisConfidence =
        when {
            snapshot.sampleEntryCount28d <= 0 -> AnalysisConfidence.LOW
            snapshot.sampleEntryCount28d < 4 -> AnalysisConfidence.MEDIUM_LOW
            snapshot.sampleEntryCount28d < 8 -> minOf(
                AnalysisConfidence.MEDIUM,
                readinessSummary?.confidence ?: AnalysisConfidence.MEDIUM
            )
            else -> minOf(
                AnalysisConfidence.HIGH,
                readinessSummary?.confidence ?: AnalysisConfidence.HIGH
            )
        }
}
