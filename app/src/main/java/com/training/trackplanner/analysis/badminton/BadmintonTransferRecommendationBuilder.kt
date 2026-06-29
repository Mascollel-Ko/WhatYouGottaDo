package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog

class BadmintonTransferRecommendationBuilder(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun build(
        snapshot: BadmintonTransferScoreSnapshot,
        readinessSummary: TodayReadinessSummary?,
        exercises: List<Exercise>
    ): BadmintonTransferRecommendation {
        val cautionLevel = cautionLevel(readinessSummary)
        val axisShares = snapshot.axisShare7d
        val recommendedAxis = when {
            cautionLevel == BadmintonTransferCautionLevel.VERY_HIGH -> null
            cautionLevel == BadmintonTransferCautionLevel.HIGH -> lowFatigueAxis(axisShares)
            snapshot.totalTransferStimulus7d <= BadmintonTransferConstants.EPSILON &&
                snapshot.totalTransferStimulus28d <= BadmintonTransferConstants.EPSILON ->
                BadmintonTransferAxis.LOW_FATIGUE_CONTROL
            else -> lowPriorityAxis(axisShares)
        }
        val sentence = sentenceFor(recommendedAxis, cautionLevel, snapshot)
        val candidates = recommendedAxis?.let { axis ->
            exerciseCandidates(axis, cautionLevel, exercises)
        }.orEmpty()

        return BadmintonTransferRecommendation(
            recommendedAxis = recommendedAxis,
            recommendationSentence = sentence,
            cautionLevel = cautionLevel,
            recommendedExerciseCandidates = candidates
        )
    }

    private fun cautionLevel(summary: TodayReadinessSummary?): BadmintonTransferCautionLevel {
        if (summary == null) return BadmintonTransferCautionLevel.NORMAL
        val hasVeryHigh = summary.detailSections.any { section ->
            section.level in setOf(FatigueLevel.VERY_HIGH, FatigueLevel.LIMITED)
        }
        val hasHigh = summary.detailSections.any { section -> section.level >= FatigueLevel.HIGH }
        return when {
            summary.status == ReadinessStatus.LIMITED || hasVeryHigh -> BadmintonTransferCautionLevel.VERY_HIGH
            summary.status == ReadinessStatus.FATIGUED || hasHigh -> BadmintonTransferCautionLevel.HIGH
            else -> BadmintonTransferCautionLevel.NORMAL
        }
    }

    private fun lowFatigueAxis(axisShares: Map<BadmintonTransferAxis, Double>): BadmintonTransferAxis =
        if ((axisShares[BadmintonTransferAxis.LOW_FATIGUE_CONTROL] ?: 0.0) <
            BadmintonTransferConstants.shareThreshold(BadmintonTransferAxis.LOW_FATIGUE_CONTROL)
        ) {
            BadmintonTransferAxis.LOW_FATIGUE_CONTROL
        } else {
            BadmintonTransferAxis.ROTATION_CONTROL
        }

    private fun lowPriorityAxis(axisShares: Map<BadmintonTransferAxis, Double>): BadmintonTransferAxis =
        BadmintonTransferConstants.recommendationPriority.firstOrNull { axis ->
            (axisShares[axis] ?: 0.0) < BadmintonTransferConstants.shareThreshold(axis)
        } ?: BadmintonTransferAxis.LOW_FATIGUE_CONTROL

    private fun sentenceFor(
        axis: BadmintonTransferAxis?,
        cautionLevel: BadmintonTransferCautionLevel,
        snapshot: BadmintonTransferScoreSnapshot
    ): String =
        when {
            cautionLevel == BadmintonTransferCautionLevel.VERY_HIGH ->
                "오늘은 배드민턴 전이 운동보다 회복을 우선 추천드립니다."
            cautionLevel == BadmintonTransferCautionLevel.HIGH ->
                "오늘은 저피로 보완운동을 추천드립니다."
            snapshot.totalTransferStimulus7d <= BadmintonTransferConstants.EPSILON &&
                snapshot.totalTransferStimulus28d <= BadmintonTransferConstants.EPSILON ->
                "오늘은 저피로 보완운동을 추천드립니다."
            axis == BadmintonTransferAxis.DECELERATION_LANDING ->
                "오늘은 감속·착지 제어 운동을 추천드립니다."
            axis == BadmintonTransferAxis.UNILATERAL_STABILITY ->
                "오늘은 편측 안정성 운동을 추천드립니다."
            axis == BadmintonTransferAxis.LATERAL_MOVEMENT ->
                "오늘은 측면 이동 보완 운동을 추천드립니다."
            axis == BadmintonTransferAxis.ROTATION_CONTROL ->
                "오늘은 회전 제어 운동을 추천드립니다."
            axis == BadmintonTransferAxis.RACKET_SUPPORT ->
                "오늘은 라켓 보조 운동을 추천드립니다."
            axis == BadmintonTransferAxis.AEROBIC_FOOTWORK ->
                "오늘은 풋워크 지속성 운동을 추천드립니다."
            axis == BadmintonTransferAxis.LOW_FATIGUE_CONTROL ->
                "오늘은 저피로 보완운동을 추천드립니다."
            else -> "오늘은 균형 유지용 보완운동을 추천드립니다."
        }

    private fun exerciseCandidates(
        axis: BadmintonTransferAxis,
        cautionLevel: BadmintonTransferCautionLevel,
        exercises: List<Exercise>
    ): List<String> =
        exercises.filter { exercise -> exercise.isActive }.mapNotNull { exercise ->
            val features = AnalysisFeatureExtractor.fromExercise(
                exercise,
                runtimeMetadataCatalog.resolve(exercise)
            )
            val axes = BadmintonTransferMetadataMapper.transferAxes(features)
            if (axis !in axes) return@mapNotNull null
            val transferType = BadmintonTransferMetadataMapper.transferType(features)
            if (transferType == BadmintonTransferType.NONE) return@mapNotNull null
            val fatigueCost = BadmintonTransferMetadataMapper.fatigueCost(features)
            if (
                cautionLevel != BadmintonTransferCautionLevel.NORMAL &&
                fatigueCost in setOf(BadmintonTransferFatigueCost.HIGH, BadmintonTransferFatigueCost.VERY_HIGH)
            ) {
                return@mapNotNull null
            }
            Candidate(
                name = exercise.name,
                typeWeight = BadmintonTransferConstants.transferWeight(transferType),
                fatigueCost = fatigueCost
            )
        }
            .sortedWith(
                compareByDescending<Candidate> { candidate -> candidate.typeWeight }
                    .thenBy { candidate -> candidate.fatigueCost.ordinal }
                    .thenBy { candidate -> candidate.name }
            )
            .map { candidate -> candidate.name }
            .distinct()
            .take(BadmintonTransferConstants.CANDIDATE_LIMIT)

    private data class Candidate(
        val name: String,
        val typeWeight: Double,
        val fatigueCost: BadmintonTransferFatigueCost
    )
}
