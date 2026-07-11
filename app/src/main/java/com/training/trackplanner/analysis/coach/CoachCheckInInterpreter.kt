package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.data.DailyCheckIn

class CoachCheckInInterpreter {
    fun guidance(checkIn: DailyCheckIn?, objectiveFatigue: DailyFatigueState?): List<String> {
        if (checkIn == null) return emptyList()
        return buildList {
            if (checkIn.overallFatigue != null && checkIn.overallFatigue >= 4) {
                val normalObjective = objectiveFatigue?.readinessLabel in setOf(
                    FatigueReadinessLabel.LOW,
                    FatigueReadinessLabel.NORMAL
                )
                add(
                    if (normalObjective) {
                        "객관적 운동 부하는 평소 수준이지만, 오늘 전신 피로 입력이 높아 회복 상태를 주의로 봅니다."
                    } else {
                        "오늘 전신 피로 입력이 높아 운동 강도와 양을 보수적으로 조절하는 편이 낫습니다."
                    }
                )
            }
            if (checkIn.lowerBodyFatigue != null && checkIn.lowerBodyFatigue >= 4) {
                add("하체 피로 입력이 높아 오늘은 점프·방향전환·하체 고중량을 줄이는 편이 낫습니다.")
            }
            if (checkIn.jointTendonDiscomfort != null && checkIn.jointTendonDiscomfort >= 4) {
                add("관절/건 불편감이 높게 입력되어 감속·착지·고중량 하체 운동은 주의가 필요합니다.")
            }
            if (checkIn.focusMotivation != null && checkIn.focusMotivation <= 2) {
                add("집중력/의욕 입력이 낮아 반응 드릴과 고강도 배드민턴은 강도를 낮춰 진행하는 편이 낫습니다.")
            }
            if (checkIn.sleepHours != null && checkIn.sleepHours < 6.0) {
                add("수면시간이 낮아 피로 해석을 보수적으로 적용합니다.")
            }
        }
    }

    fun causes(checkIn: DailyCheckIn?, baseScore: Double): List<CoachFatigueCause> {
        if (checkIn == null) return emptyList()
        val base = baseScore.coerceAtLeast(1.0)
        return buildList {
            checkIn.sleepHours?.takeIf { it < 6.0 }?.let { hours ->
                add(cause("수면 부족 / 회복 입력", "수면시간이 ${formatHours(hours)}시간으로 입력되었습니다.", base * 1.15, listOf("전신 근육")))
            }
            checkIn.overallFatigue?.takeIf { it >= 4 }?.let { value ->
                add(cause("전신 피로 입력 상승", "오늘 전신 피로가 $value/5로 입력되었습니다.", base * severity(value), listOf("전신 근육")))
            }
            checkIn.lowerBodyFatigue?.takeIf { it >= 4 }?.let { value ->
                add(cause("하체 피로 입력 상승", "오늘 하체 피로가 $value/5로 입력되었습니다.", base * severity(value), listOf("국소 근육", "관절·건·충격")))
            }
            checkIn.jointTendonDiscomfort?.takeIf { it >= 4 }?.let { value ->
                add(cause("관절/건 불편감 입력", "오늘 불편감이 $value/5로 입력되었습니다.", base * severity(value), listOf("관절·건·충격")))
            }
            checkIn.focusMotivation?.takeIf { it <= 2 }?.let { value ->
                add(cause("집중력/의욕 저하", "오늘 집중력/의욕이 $value/5로 입력되었습니다.", base * if (value == 1) 1.25 else 1.05, listOf("동작·집중", "신경계")))
            }
        }
    }

    private fun cause(label: String, detail: String, score: Double, axes: List<String>) = CoachFatigueCause(
        rank = 0,
        label = label,
        detail = detail,
        contributionScore = score,
        affectedAxes = axes,
        sourceType = CoachFatigueCauseType.RECOVERY_INPUT
    )

    private fun severity(value: Int): Double = if (value >= 5) 1.25 else 1.05

    private fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(hours)
}
