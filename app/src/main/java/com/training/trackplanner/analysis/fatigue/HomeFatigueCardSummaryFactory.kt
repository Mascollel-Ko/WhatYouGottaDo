package com.training.trackplanner.analysis.fatigue

object HomeFatigueCardSummaryFactory {
    fun create(
        preWorkout: DailyFatigueState,
        current: DailyFatigueState,
        projected: DailyFatigueState?,
        confirmedSetCount: Int,
        unconfirmedSetCount: Int
    ): HomeFatigueCardSummary {
        val hasConfirmedWork = confirmedSetCount > 0
        val primaryState = if (hasConfirmedWork) current else preWorkout
        val primaryPrefix = if (hasConfirmedWork) "현재" else "운동 전"

        return when {
            unconfirmedSetCount > 0 && projected != null -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryState.toReading(),
                projectionPrefix = if (hasConfirmedWork) "남은 계획 후 예상" else "계획 후 예상",
                projection = projected.toReading()
            )
            hasConfirmedWork -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryState.toReading(),
                statusText = "계획 완료"
            )
            else -> HomeFatigueCardSummary(
                primaryPrefix = primaryPrefix,
                primary = primaryState.toReading(),
                statusText = "오늘 계획 없음"
            )
        }
    }

    private fun DailyFatigueState.toReading(): HomeFatigueReading =
        HomeFatigueReading(
            score = overallFatigueIndex,
            label = qualitativeLabel()
        )

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
