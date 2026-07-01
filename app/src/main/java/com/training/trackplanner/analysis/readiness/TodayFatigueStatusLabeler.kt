package com.training.trackplanner.analysis.readiness

object TodayFatigueStatusLabeler {
    fun label(summary: TodayReadinessSummary): String {
        val axes = axisStates(summary)
        if (axes.isEmpty()) return summary.status.fallbackLabel()

        val highAxes = axes.filter { axis -> axis.level >= FatigueLevel.HIGH }
        val hasVeryHigh = highAxes.any { axis ->
            axis.level == FatigueLevel.VERY_HIGH || axis.level == FatigueLevel.LIMITED
        }

        return when {
            hasVeryHigh -> "피로 심화"
            highAxes.size >= 3 -> "피로 심화"
            highAxes.size >= 2 && highAxes.any { it.isRecoveryOrSystemic() } -> "피로 심화"
            highAxes.size >= 2 -> "피로 누적"
            highAxes.size == 1 -> singleAxisLabel(highAxes.first())
            axes.any { axis -> axis.level == FatigueLevel.ELEVATED } -> "주의"
            else -> "보통"
        }
    }

    fun axisStates(summary: TodayReadinessSummary): List<TodayFatigueAxisState> {
        val recoveryLevel = summary.detailSections
            .firstOrNull { section -> section.type == FatigueDetailType.RECOVERY }
            ?.level
            ?: FatigueLevel.LOW
        summary.fatiguePresentation?.let { presentation ->
            return listOf(
                TodayFatigueAxisState("신경계", levelFromScore(presentation.neuralScore)),
                TodayFatigueAxisState("전신 근육", levelFromScore(presentation.systemicScore)),
                TodayFatigueAxisState("국소 근육", levelFromScore(presentation.localMuscleScore)),
                TodayFatigueAxisState("관절/건/충격", levelFromScore(presentation.jointTendonScore)),
                TodayFatigueAxisState("동작 집중", levelFromScore(presentation.focusScore)),
                TodayFatigueAxisState("회복 지속", recoveryLevel)
            )
        }

        if (summary.detailSections.isEmpty()) return emptyList()
        return listOf(
            TodayFatigueAxisState("신경계", maxLevel(summary, FatigueDetailType.NEURAL_HEAVY, FatigueDetailType.NEURAL_SPEED)),
            TodayFatigueAxisState("전신 근육", maxLevel(summary, FatigueDetailType.SYSTEMIC)),
            TodayFatigueAxisState("국소 근육", maxLevel(summary, FatigueDetailType.LOCAL_BODY_PART)),
            TodayFatigueAxisState("관절/건/충격", jointLevel(summary)),
            TodayFatigueAxisState("동작 집중", maxLevel(summary, FatigueDetailType.BADMINTON_COURT)),
            TodayFatigueAxisState("회복 지속", recoveryLevel)
        )
    }

    private fun singleAxisLabel(axis: TodayFatigueAxisState): String =
        when (axis.label) {
            "전신 근육" -> "전신 피로 높음"
            "신경계" -> "신경계 피로 높음"
            "국소 근육" -> "국소 근육 피로 높음"
            "관절/건/충격" -> "관절/건 부담 높음"
            "동작 집중" -> "동작 집중 부담 높음"
            "회복 지속" -> "회복 부담 증가"
            else -> "특정 피로축 주의"
        }

    private fun maxLevel(summary: TodayReadinessSummary, vararg types: FatigueDetailType): FatigueLevel =
        summary.detailSections
            .filter { section -> section.type in types }
            .maxOfOrNull { section -> section.level }
            ?: FatigueLevel.LOW

    private fun jointLevel(summary: TodayReadinessSummary): FatigueLevel =
        summary.detailSections
            .filter { section ->
                section.type == FatigueDetailType.PAIN ||
                    section.title.contains("관절") ||
                    section.title.contains("건") ||
                    section.title.contains("충격") ||
                    section.relatedCategories.any { it.contains("관절") || it.contains("건") || it.contains("충격") }
            }
            .maxOfOrNull { section -> section.level }
            ?: FatigueLevel.LOW

    private fun levelFromScore(score: Int): FatigueLevel =
        when {
            score >= 90 -> FatigueLevel.VERY_HIGH
            score >= 70 -> FatigueLevel.HIGH
            score >= 60 -> FatigueLevel.ELEVATED
            score >= 35 -> FatigueLevel.NORMAL
            else -> FatigueLevel.LOW
        }

    private fun TodayFatigueAxisState.isRecoveryOrSystemic(): Boolean =
        label == "전신 근육" || label == "회복 지속"

    private fun ReadinessStatus.fallbackLabel(): String =
        when (this) {
            ReadinessStatus.READY -> "보통"
            ReadinessStatus.CAUTION -> "주의"
            ReadinessStatus.FATIGUED -> "피로 누적"
            ReadinessStatus.LIMITED -> "피로 심화"
        }
}

data class TodayFatigueAxisState(
    val label: String,
    val level: FatigueLevel
) {
    val displayLabel: String
        get() = when (level) {
            FatigueLevel.LOW -> "낮음"
            FatigueLevel.NORMAL,
            FatigueLevel.ELEVATED -> "보통"
            FatigueLevel.HIGH -> "현재 높음"
            FatigueLevel.VERY_HIGH,
            FatigueLevel.LIMITED -> "매우 높음"
        }
}
