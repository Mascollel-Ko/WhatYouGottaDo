package com.training.trackplanner.analysis.readiness

object TodayFatigueStatusLabeler {
    fun label(summary: TodayReadinessSummary): String {
        val highAxes = summary.detailSections.filter { section ->
            section.isCurrentFatigueAxis() && section.level >= FatigueLevel.HIGH
        }
        val hasVeryHigh = highAxes.any { section ->
            section.level == FatigueLevel.VERY_HIGH || section.level == FatigueLevel.LIMITED
        }

        return when {
            hasVeryHigh -> "피로 심화"
            highAxes.size >= 3 -> "피로 심화"
            highAxes.size >= 2 && highAxes.any { it.isRecoveryOrSystemic() } -> "피로 심화"
            highAxes.size >= 2 -> "피로 누적"
            highAxes.size == 1 -> singleAxisLabel(highAxes.first())
            else -> summary.status.fallbackLabel()
        }
    }

    private fun singleAxisLabel(section: FatigueDetailSection): String =
        when (section.type) {
            FatigueDetailType.SYSTEMIC -> "전신 피로 높음"
            FatigueDetailType.NEURAL_HEAVY,
            FatigueDetailType.NEURAL_SPEED -> "신경계 피로 높음"
            FatigueDetailType.LOCAL_BODY_PART -> jointOrLocalLabel(section)
            FatigueDetailType.BADMINTON_COURT -> "동작 집중 부담 높음"
            FatigueDetailType.RECOVERY -> "회복 부담 증가"
            else -> "특정 피로축 주의"
        }

    private fun jointOrLocalLabel(section: FatigueDetailSection): String {
        val text = section.title + " " + section.relatedCategories.joinToString(" ")
        return if (text.contains("관절") || text.contains("건") || text.contains("충격")) {
            "관절/건 부담 높음"
        } else {
            "국소 근육 피로 높음"
        }
    }

    private fun FatigueDetailSection.isCurrentFatigueAxis(): Boolean =
        type in setOf(
            FatigueDetailType.SYSTEMIC,
            FatigueDetailType.NEURAL_HEAVY,
            FatigueDetailType.NEURAL_SPEED,
            FatigueDetailType.LOCAL_BODY_PART,
            FatigueDetailType.BADMINTON_COURT,
            FatigueDetailType.RECOVERY
        )

    private fun FatigueDetailSection.isRecoveryOrSystemic(): Boolean =
        type == FatigueDetailType.SYSTEMIC || type == FatigueDetailType.RECOVERY

    private fun ReadinessStatus.fallbackLabel(): String =
        when (this) {
            ReadinessStatus.READY -> "보통"
            ReadinessStatus.CAUTION -> "주의"
            ReadinessStatus.FATIGUED -> "피로 누적"
            ReadinessStatus.LIMITED -> "피로 심화"
        }
}
