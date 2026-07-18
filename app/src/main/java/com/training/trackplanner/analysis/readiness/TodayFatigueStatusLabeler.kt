package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.fatigue.FatigueLabelResolver
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.analysis.fatigue.FatigueThresholds
import com.training.trackplanner.analysis.fatigue.DailyFatigueState

object TodayFatigueStatusLabeler {
    fun currentSummary(state: DailyFatigueState): CurrentFatigueStatusSummary =
        currentSummary(state.overallFatigueIndex, axisStates(state))

    fun currentSummary(ofi: Int, summary: TodayReadinessSummary?): CurrentFatigueStatusSummary {
        val axes = summary?.let(::axisStates).orEmpty()
        return currentSummary(ofi, axes)
    }

    private fun currentSummary(ofi: Int, axes: List<TodayFatigueAxisState>): CurrentFatigueStatusSummary {
        val counts = axes.fold(LevelCounts()) { acc, axis -> acc.plus(axis.level) }
        return CurrentFatigueStatusSummary(
            ofi = ofi,
            ofiLabel = ofiDisplayLabel(ofi),
            judgementMessage = judgementMessage(axes),
            axisMessage = axisMessage(axes),
            levelCountMessage = counts.message(),
            veryHighCount = counts.veryHigh,
            highCount = counts.high,
            normalCount = counts.normal,
            lowCount = counts.low
        )
    }

    fun axisSummary(state: DailyFatigueState): CurrentFatigueAxisSummary =
        axisSummary(axisStates(state))

    fun axisSummary(summary: TodayReadinessSummary): CurrentFatigueAxisSummary {
        return axisSummary(axisStates(summary))
    }

    private fun axisSummary(axes: List<TodayFatigueAxisState>): CurrentFatigueAxisSummary {
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
        summary.fatiguePresentation?.let { presentation ->
            return listOf(
                TodayFatigueAxisState("고중량·힘 신경계", levelFromScore(presentation.highForceNeuralScore)),
                TodayFatigueAxisState("전신 근육", levelFromScore(presentation.systemicMuscularScore)),
                TodayFatigueAxisState("국소 근육", levelFromScore(presentation.localMuscularScore)),
                TodayFatigueAxisState("고속", levelFromScore(presentation.highSpeedScore)),
                TodayFatigueAxisState("반응", levelFromScore(presentation.reactiveScore))
            )
        }

        if (summary.detailSections.isEmpty()) return emptyList()
        return listOf(
            TodayFatigueAxisState("고중량·힘 신경계", maxLevel(summary, FatigueDetailType.NEURAL_HEAVY)),
            TodayFatigueAxisState("전신 근육", maxLevel(summary, FatigueDetailType.SYSTEMIC)),
            TodayFatigueAxisState("국소 근육", maxLevel(summary, FatigueDetailType.LOCAL_BODY_PART)),
            TodayFatigueAxisState("고속", maxLevel(summary, FatigueDetailType.NEURAL_SPEED)),
            TodayFatigueAxisState("반응", maxLevel(summary, FatigueDetailType.BADMINTON_COURT))
        )
    }

    fun axisStates(state: DailyFatigueState): List<TodayFatigueAxisState> = listOf(
        TodayFatigueAxisState("고중량·힘 신경계", levelFromScore(state.highForceNeuralScore)),
        TodayFatigueAxisState("전신 근육", levelFromScore(state.systemicMuscularScore)),
        TodayFatigueAxisState("국소 근육", levelFromScore(state.localMuscularScore)),
        TodayFatigueAxisState("고속", levelFromScore(state.highSpeedScore)),
        TodayFatigueAxisState("반응", levelFromScore(state.reactiveScore))
    )

    fun axisWarningSentence(axis: TodayFatigueAxisState): String =
        if (axis.level.isVeryHigh()) {
            "${axis.label} 피로도가 높습니다. 주의하세요."
        } else {
            "${axis.label} 피로도가 높습니다. 스트레스를 줄이면 좋습니다."
        }

    private fun axisMessage(axes: List<TodayFatigueAxisState>): String {
        val veryHighAxes = axes.filter { axis -> axis.level.isVeryHigh() }
        if (veryHighAxes.isNotEmpty()) {
            val labels = veryHighAxes.joinLabels()
            return "$labels 피로도가 높습니다. 주의하세요."
        }
        val highAxes = axes.filter { axis -> axis.level == FatigueLevel.HIGH }
        if (highAxes.isNotEmpty()) {
            val labels = highAxes.joinLabels()
            return "$labels 피로도가 높습니다. 스트레스를 줄이면 좋습니다."
        }
        return "모든 피로도가 양호합니다. 힘차게 운동!"
    }

    private fun judgementMessage(axes: List<TodayFatigueAxisState>): String {
        val cautionAxes = axes.filter { axis -> axis.level == FatigueLevel.HIGH || axis.level.isVeryHigh() }
        if (cautionAxes.isEmpty()) return "판단: 현재 특별히 조절이 필요한 피로도 축은 없습니다."
        return "판단: ${cautionAxes.map { it.label }.joinKoreanLabels()} 피로도를 조절하면 좋습니다."
    }

    fun ofiDisplayLabel(ofi: Int): String =
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

    private fun List<String>.joinKoreanLabels(): String =
        when (size) {
            0 -> ""
            1 -> first()
            2 -> "${this[0]}${this[0].josaAnd()} ${this[1]}"
            else -> dropLast(1).joinToString(", ") + last().let { last -> "${dropLast(1).last().josaAnd()} $last" }
        }

    private fun String.josaAnd(): String {
        val last = lastOrNull() ?: return "와"
        if (last !in '\uAC00'..'\uD7A3') return "와"
        return if (((last.code - 0xAC00) % 28) == 0) "와" else "과"
    }

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
    val judgementMessage: String,
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
