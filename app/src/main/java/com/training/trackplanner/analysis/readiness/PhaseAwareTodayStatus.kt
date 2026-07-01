package com.training.trackplanner.analysis.readiness

enum class TodayStatusPhase {
    REMAINING_PLAN,
    COMPLETED
}

data class PhaseAwareTodayStatus(
    val phase: TodayStatusPhase,
    val current: TodayReadinessSummary,
    val projected: TodayReadinessSummary?,
    val plannedSetCount: Int,
    val confirmedSetCount: Int,
    val unconfirmedSetCount: Int,
    val phaseLabel: String,
    val headline: String,
    val detail: String,
    val actionLabel: String,
    val keyAxes: List<String>
)

class PhaseAwareTodayStatusBuilder(
    private val engine: TodayReadinessEngine = TodayReadinessEngine()
) {
    fun build(input: TodayReadinessEngineInput): PhaseAwareTodayStatus {
        val todayString = input.today.toString()
        val todayEntries = input.entriesWithSets.filter { item -> item.entry.date == todayString }
        val confirmedSetCount = todayEntries.sumOf { item -> item.sets.count { set -> set.confirmed } }
        val unconfirmedSetCount = todayEntries.sumOf { item -> item.sets.count { set -> !set.confirmed } }
        val plannedSetCount = confirmedSetCount + unconfirmedSetCount
        val current = engine.analyze(input)
        val projected = if (unconfirmedSetCount > 0) {
            engine.analyze(
                input.copy(
                    entriesWithSets = input.entriesWithSets.map { item ->
                        if (item.entry.date != todayString) {
                            item
                        } else {
                            item.copy(sets = item.sets.map { set -> set.copy(confirmed = true) })
                        }
                    }
                )
            )
        } else {
            null
        }

        val phase = if (unconfirmedSetCount > 0) {
            TodayStatusPhase.REMAINING_PLAN
        } else {
            TodayStatusPhase.COMPLETED
        }

        return when (phase) {
            TodayStatusPhase.REMAINING_PLAN -> remainingPlanStatus(
                current = current,
                projected = requireNotNull(projected),
                plannedSetCount = plannedSetCount,
                confirmedSetCount = confirmedSetCount,
                unconfirmedSetCount = unconfirmedSetCount
            )
            TodayStatusPhase.COMPLETED -> completedStatus(
                current = current,
                plannedSetCount = plannedSetCount,
                confirmedSetCount = confirmedSetCount,
                unconfirmedSetCount = unconfirmedSetCount
            )
        }
    }

    private fun remainingPlanStatus(
        current: TodayReadinessSummary,
        projected: TodayReadinessSummary,
        plannedSetCount: Int,
        confirmedSetCount: Int,
        unconfirmedSetCount: Int
    ): PhaseAwareTodayStatus {
        val action = when {
            projected.status == ReadinessStatus.READY -> "계획대로 진행 가능"
            projected.status == ReadinessStatus.CAUTION ->
                "계획이 적절하며 운동 후 휴식이 필요합니다."
            projected.status == ReadinessStatus.FATIGUED && current.status == ReadinessStatus.READY ->
                "계획이 적절하며 운동 후 휴식이 필요합니다."
            projected.status == ReadinessStatus.FATIGUED -> "강도 축소 권장"
            else -> "계획의 강도가 높습니다. 다음 날 운동에 영향을 줄 수 있습니다."
        }
        val gotWorse = projected.status.ordinal > current.status.ordinal
        val headline = when {
            projected.status == ReadinessStatus.READY ->
                "남은 계획을 마쳐도 현재 기준에서는 관리 가능한 범위입니다."
            projected.status == ReadinessStatus.CAUTION ->
                "계획이 적절하며 운동 후 휴식이 필요합니다."
            projected.status == ReadinessStatus.FATIGUED ->
                "계획의 강도가 높습니다. 다음 날 운동에 영향을 줄 수 있습니다."
            gotWorse ->
                "남은 계획을 모두 마치면 부담이 더 올라갈 수 있습니다."
            else ->
                "현재 상태를 기준으로 남은 계획을 조절해 진행하세요."
        }
        val detail = when (projected.status) {
            ReadinessStatus.READY ->
                "현재 상태와 계획 완료 예상 모두 진행 가능한 범위입니다. 기록된 컨디션이 안정적이면 계획대로 진행해도 됩니다."
            ReadinessStatus.CAUTION ->
                "현재 상태보다 계획 완료 예상의 부담이 커집니다. 남은 세트는 RPE를 낮추거나 반복 수를 줄이는 쪽이 낫습니다."
            ReadinessStatus.FATIGUED ->
                "남은 계획을 모두 수행하면 회복 부담이 커질 수 있습니다. 고강도 하체, 점프, 방향전환 계열은 줄이는 편이 낫습니다."
            ReadinessStatus.LIMITED ->
                "남은 계획보다 회복과 부담 조절을 우선하세요. 불편감이 있는 부위와 고강도 항목은 제외하는 편이 낫습니다."
        }
        return PhaseAwareTodayStatus(
            phase = TodayStatusPhase.REMAINING_PLAN,
            current = current,
            projected = projected,
            plannedSetCount = plannedSetCount,
            confirmedSetCount = confirmedSetCount,
            unconfirmedSetCount = unconfirmedSetCount,
            phaseLabel = "남은 계획 판단",
            headline = headline,
            detail = detail,
            actionLabel = action,
            keyAxes = keyAxes(projected)
        )
    }

    private fun completedStatus(
        current: TodayReadinessSummary,
        plannedSetCount: Int,
        confirmedSetCount: Int,
        unconfirmedSetCount: Int
    ): PhaseAwareTodayStatus {
        val noPlan = plannedSetCount == 0
        val headline = if (noPlan) {
            "오늘 계획은 아직 없습니다."
        } else {
            when (current.status) {
                ReadinessStatus.READY -> "오늘 운동 후 피로도는 평소 범위입니다."
                ReadinessStatus.CAUTION -> "오늘 운동 후 일부 부담이 올라 회복이 필요합니다."
                ReadinessStatus.FATIGUED -> "오늘 운동 후 피로 부담이 높습니다."
                ReadinessStatus.LIMITED -> "오늘 운동 후 제한 신호를 우선 봅니다."
            }
        }
        val detail = if (noPlan) {
            "오늘 기록된 운동 기준으로는 피로가 과도하지 않습니다."
        } else {
            when (current.status) {
                ReadinessStatus.READY ->
                    "오늘 기록된 운동 기준으로는 피로가 과도하지 않습니다."
                ReadinessStatus.CAUTION ->
                    "현재 일부 피로 신호가 올라왔습니다. 휴식과 수면을 우선하세요."
                ReadinessStatus.FATIGUED ->
                    "현재 피로가 누적된 상태입니다. 추가 고강도 운동은 피하는 편이 좋습니다."
                ReadinessStatus.LIMITED ->
                    "현재 제한 신호가 있어 회복과 부담 조절을 우선하세요."
            }
        }
        val action = if (noPlan) {
            "상태 확인"
        } else {
            when (current.status) {
                ReadinessStatus.READY -> "현재 유지"
                ReadinessStatus.CAUTION -> "회복 보강"
                ReadinessStatus.FATIGUED -> "회복 우선"
                ReadinessStatus.LIMITED -> "부담 낮추기"
            }
        }
        return PhaseAwareTodayStatus(
            phase = TodayStatusPhase.COMPLETED,
            current = current,
            projected = null,
            plannedSetCount = plannedSetCount,
            confirmedSetCount = confirmedSetCount,
            unconfirmedSetCount = unconfirmedSetCount,
            phaseLabel = if (noPlan) "오늘 상태" else "현재 회복 판단",
            headline = headline,
            detail = detail,
            actionLabel = action,
            keyAxes = keyAxes(current)
        )
    }

    private fun keyAxes(summary: TodayReadinessSummary): List<String> =
        summary.detailSections
            .filter { section -> section.level >= FatigueLevel.HIGH }
            .map { section -> section.title }
            .ifEmpty { summary.primaryReasons }
            .take(3)
}
