package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayFatigueStatusLabeler

object HomeFatigueCardSummaryFactory {
    fun create(
        preWorkout: DailyFatigueState,
        current: DailyFatigueState,
        projected: DailyFatigueState?,
        confirmedSetCount: Int,
        unconfirmedSetCount: Int,
        todayStatus: PhaseAwareTodayStatus? = null
    ): HomeFatigueCardSummary {
        val hasConfirmedWork = confirmedSetCount > 0
        val primaryState = if (hasConfirmedWork) current else preWorkout
        val primaryPrefix = if (hasConfirmedWork) "현재" else "운동 전"
        // OFI is the shared home/analysis/lab summary score. Readiness only supplies labels and guidance here.
        val primaryReading = todayStatus?.current?.let { summary ->
            primaryState.toReading(TodayFatigueStatusLabeler.label(summary))
        } ?: primaryState.toReading()
        val projectedReading = projected?.let { state ->
            state.toProjectedReading(todayStatus?.projected?.let(TodayFatigueStatusLabeler::label))
        }

        return when {
            unconfirmedSetCount > 0 && projected != null -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                projectionPrefix = "운동 후 예상 부하",
                projection = projectedReading,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail,
                actionLabel = todayStatus?.actionLabel
            )
            hasConfirmedWork -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail,
                actionLabel = todayStatus?.actionLabel
            )
            else -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail,
                actionLabel = todayStatus?.actionLabel
            )
        }
    }

    private fun DailyFatigueState.toReading(labelOverride: String? = null): HomeFatigueReading =
        HomeFatigueReading(
            score = overallFatigueIndex,
            label = labelOverride ?: qualitativeLabel()
        )

    private fun DailyFatigueState.toProjectedReading(readinessLabel: String?): HomeFatigueReading {
        val maxAxis = maxOf(
            neuromuscularScore,
            systemicMuscularScore,
            localMuscularScore,
            jointTendonImpactScore,
            movementFocusScore,
            recoveryPressureScore
        )
        val label = when {
            overallFatigueIndex >= FatigueThresholds.OFI_HIGH_START -> "회복 우선 확인"
            overallFatigueIndex >= FatigueThresholds.OFI_CAUTION_START -> "회복 확인 필요"
            maxAxis >= FatigueThresholds.OFI_ELEVATED_START -> "예상 부하 증가"
            overallFatigueIndex >= FatigueThresholds.OFI_ELEVATED_START -> "예상 부하 증가"
            readinessLabel in setOf("피로 누적", "피로 심화", "주의") -> "예상 부하 보통"
            else -> "예상 부하 보통"
        }
        return HomeFatigueReading(
            score = overallFatigueIndex,
            label = label
        )
    }

    private fun DailyFatigueState.qualitativeLabel(): String {
        if (overallFatigueIndex < 40) return "양호"
        if (overallFatigueIndex < FatigueThresholds.OFI_ELEVATED_START) return "적정"

        val dominantAxis = listOf(
            neuromuscularScore to "신경계 피로 주의",
            systemicMuscularScore to "전신 피로 주의",
            localMuscularScore to "국소 근육 피로 주의",
            jointTendonImpactScore to "관절/건 피로 주의",
            movementFocusScore to "동작 집중 피로 주의",
            recoveryPressureScore to "회복 부담 주의"
        ).maxByOrNull { it.first }

        return dominantAxis?.second ?: when (readinessLabel) {
            FatigueReadinessLabel.LOW -> "양호"
            FatigueReadinessLabel.NORMAL -> "적정"
            FatigueReadinessLabel.ELEVATED, FatigueReadinessLabel.CAUTION -> "주의"
            FatigueReadinessLabel.HIGH_FATIGUE -> "높음"
        }
    }
}
