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
        val statusSummary = TodayFatigueStatusLabeler.currentSummary(primaryState)
        val primaryReading = primaryState.toReading(statusSummary.ofiLabel)
        val projectedReading = projected?.let { state ->
            state.toProjectedReading()
        }

        return when {
            unconfirmedSetCount > 0 && projected != null -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                projectionPrefix = "끝나면 예상 피로도",
                projection = projectedReading,
                axisMessage = statusSummary.axisMessage,
                levelCountMessage = statusSummary.levelCountMessage,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail
            )
            hasConfirmedWork -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                axisMessage = statusSummary.axisMessage,
                levelCountMessage = statusSummary.levelCountMessage,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail
            )
            else -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                axisMessage = statusSummary.axisMessage,
                levelCountMessage = statusSummary.levelCountMessage,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail
            )
        }
    }

    private fun DailyFatigueState.toReading(labelOverride: String? = null): HomeFatigueReading =
        HomeFatigueReading(
            score = overallFatigueIndex,
            label = labelOverride ?: qualitativeLabel()
        )

    private fun DailyFatigueState.toProjectedReading(): HomeFatigueReading {
        val maxAxis = maxOf(
            highForceNeuralScore,
            systemicMuscularScore,
            localMuscularScore,
            highSpeedScore,
            reactiveScore
        )
        val label = when {
            overallFatigueIndex >= FatigueThresholds.OFI_HIGH_START -> "회복 우선 확인"
            overallFatigueIndex >= FatigueThresholds.OFI_CAUTION_START -> "회복 확인 필요"
            maxAxis >= FatigueThresholds.OFI_ELEVATED_START -> "예상 피로도 증가"
            overallFatigueIndex >= FatigueThresholds.OFI_ELEVATED_START -> "예상 피로도 증가"
            else -> "예상 피로도 보통"
        }
        return HomeFatigueReading(
            score = overallFatigueIndex,
            label = label
        )
    }

    private fun DailyFatigueState.qualitativeLabel(): String {
        return when (FatigueLabelResolver.label(overallFatigueIndex)) {
            FatigueReadinessLabel.LOW -> "양호"
            FatigueReadinessLabel.NORMAL -> "보통"
            FatigueReadinessLabel.ELEVATED -> "주의"
            FatigueReadinessLabel.CAUTION -> "높음"
            FatigueReadinessLabel.HIGH_FATIGUE -> "매우 높음"
        }
    }
}
