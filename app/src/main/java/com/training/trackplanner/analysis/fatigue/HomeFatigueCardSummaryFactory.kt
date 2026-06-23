package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.ReadinessStatus

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
        val primaryReading = todayStatus?.current?.let { summary ->
            primaryState.toReading(summary.status.homeLabel())
        } ?: primaryState.toReading()
        val projectedReading = projected?.let { state ->
            todayStatus?.projected?.let { summary -> state.toReading(summary.status.homeLabel()) } ?: state.toReading()
        }

        return when {
            unconfirmedSetCount > 0 && projected != null -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                projectionPrefix = if (hasConfirmedWork) "남은 계획 후 예상" else "계획 후 예상",
                projection = projectedReading,
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail,
                actionLabel = todayStatus?.actionLabel
            )
            hasConfirmedWork -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                statusText = "계획 완료",
                phaseLabel = todayStatus?.phaseLabel,
                headline = todayStatus?.headline,
                detail = todayStatus?.detail,
                actionLabel = todayStatus?.actionLabel
            )
            else -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryReading,
                statusText = "오늘 계획 없음",
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

    private fun ReadinessStatus.homeLabel(): String = when (this) {
        ReadinessStatus.READY -> "진행 가능"
        ReadinessStatus.CAUTION -> "조절 권장"
        ReadinessStatus.FATIGUED -> "회복 필요"
        ReadinessStatus.LIMITED -> "부담 낮추기"
    }

    private fun DailyFatigueState.qualitativeLabel(): String {
        if (overallFatigueIndex < 40) return "양호"
        if (overallFatigueIndex < 65) return "적정"

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
