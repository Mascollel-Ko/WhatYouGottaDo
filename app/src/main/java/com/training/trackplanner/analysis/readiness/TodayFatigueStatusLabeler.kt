package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.fatigue.FatigueLabelResolver
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.analysis.fatigue.FatigueThresholds

object TodayFatigueStatusLabeler {
    fun currentSummary(ofi: Int, summary: TodayReadinessSummary?): CurrentFatigueStatusSummary {
        val axes = summary?.let(::axisStates).orEmpty()
        val counts = axes.fold(LevelCounts()) { acc, axis -> acc.plus(axis.level) }
        return CurrentFatigueStatusSummary(
            ofi = ofi,
            ofiLabel = ofiDisplayLabel(ofi),
            axisMessage = axisMessage(axes),
            levelCountMessage = counts.message(),
            veryHighCount = counts.veryHigh,
            highCount = counts.high,
            normalCount = counts.normal,
            lowCount = counts.low
        )
    }

    fun axisSummary(summary: TodayReadinessSummary): CurrentFatigueAxisSummary {
        val axes = axisStates(summary)
        val counts = axes.fold(LevelCounts()) { acc, axis -> acc.plus(axis.level) }
        return CurrentFatigueAxisSummary(
            axisMessage = axisMessage(axes),
            levelCountMessage = counts.message(),
            veryHighCount = counts.veryHigh,
            highCount = counts.high,
            normalCount = counts.normal,
            lowCount = counts.low
        )
    }

    fun label(summary: TodayReadinessSummary): String {
        val axes = axisStates(summary)
        val veryHighAxes = axes.filter { axis -> axis.level.isVeryHigh() }
        if (veryHighAxes.isNotEmpty()) return veryHighAxes.joinLabels()
        val highAxes = axes.filter { axis -> axis.level == FatigueLevel.HIGH }
        return when {
            highAxes.isNotEmpty() -> highAxes.joinLabels()
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

    private fun axisMessage(axes: List<TodayFatigueAxisState>): String {
        val veryHighAxes = axes.filter { axis -> axis.level.isVeryHigh() }
        if (veryHighAxes.isNotEmpty()) {
            return "${veryHighAxes.joinLabels()} 피로도가 높습니다. 해당 스트레스에 대해 주의하세요."
        }
        val highAxes = axes.filter { axis -> axis.level == FatigueLevel.HIGH }
        if (highAxes.isNotEmpty()) {
            return "${highAxes.joinLabels()} 피로도가 높습니다. 해당 스트레스를 줄이면 좋습니다."
        }
        return "모든 피로도가 양호합니다. 힘차게 운동!"
    }

    private fun ofiDisplayLabel(ofi: Int): String =
        when (FatigueLabelResolver.label(ofi)) {
            FatigueReadinessLabel.LOW -> "피로도 낮음"
            FatigueReadinessLabel.NORMAL -> "피로도 보통"
            FatigueReadinessLabel.ELEVATED -> "피로도 주의"
            FatigueReadinessLabel.CAUTION -> "피로도 높음"
            FatigueReadinessLabel.HIGH_FATIGUE -> "피로 심화"
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
            score >= FatigueThresholds.PRESENTATION_VERY_HIGH_SCORE -> FatigueLevel.VERY_HIGH
            score >= FatigueThresholds.PRESENTATION_RESTRICTED_SCORE -> FatigueLevel.HIGH
            score >= FatigueThresholds.PRESENTATION_ELEVATED_SCORE -> FatigueLevel.ELEVATED
            score >= 35 -> FatigueLevel.NORMAL
            else -> FatigueLevel.LOW
        }

    private fun List<TodayFatigueAxisState>.joinLabels(): String =
        joinToString(", ") { axis -> axis.label }

    private fun FatigueLevel.isVeryHigh(): Boolean =
        this == FatigueLevel.VERY_HIGH || this == FatigueLevel.LIMITED

    private fun LevelCounts.plus(level: FatigueLevel): LevelCounts =
        when {
            level.isVeryHigh() -> copy(veryHigh = veryHigh + 1)
            level == FatigueLevel.HIGH -> copy(high = high + 1)
            level == FatigueLevel.LOW -> copy(low = low + 1)
            else -> copy(normal = normal + 1)
        }

    private fun LevelCounts.message(): String =
        "축별 상태: 매우 높음($veryHigh), 높음($high), 보통($normal), 낮음($low)"

    private data class LevelCounts(
        val veryHigh: Int = 0,
        val high: Int = 0,
        val normal: Int = 0,
        val low: Int = 0
    )
}

data class CurrentFatigueStatusSummary(
    val ofi: Int,
    val ofiLabel: String,
    val axisMessage: String,
    val levelCountMessage: String,
    val veryHighCount: Int,
    val highCount: Int,
    val normalCount: Int,
    val lowCount: Int
)

data class CurrentFatigueAxisSummary(
    val axisMessage: String,
    val levelCountMessage: String,
    val veryHighCount: Int,
    val highCount: Int,
    val normalCount: Int,
    val lowCount: Int
)

data class TodayFatigueAxisState(
    val label: String,
    val level: FatigueLevel
) {
    val displayLabel: String
        get() = when (level) {
            FatigueLevel.LOW -> "낮음"
            FatigueLevel.NORMAL,
            FatigueLevel.ELEVATED -> "보통"
            FatigueLevel.HIGH -> "높음"
            FatigueLevel.VERY_HIGH,
            FatigueLevel.LIMITED -> "매우 높음"
        }
}
