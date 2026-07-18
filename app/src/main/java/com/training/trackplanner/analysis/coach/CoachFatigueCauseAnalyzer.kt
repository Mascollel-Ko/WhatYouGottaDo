package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.FatigueAxisValues
import com.training.trackplanner.analysis.fatigue.FatigueDecayModel
import com.training.trackplanner.data.DailyCheckIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CoachFatigueCauseAnalyzer {
    fun analyze(
        today: LocalDate,
        history: List<DailyFatigueResult>,
        checkIns: List<DailyCheckIn> = emptyList(),
        windowDays: Int = 14,
        limit: Int = 5
    ): CoachFatigueCauseSummary {
        val safeWindow = windowDays.coerceAtLeast(1)
        val start = today.minusDays(safeWindow.toLong() - 1)
        val exerciseCauses = history.asSequence()
            .filter { result -> result.state.date in start..today }
            .flatMap { result -> result.recordContributions.asSequence() }
            .filter { contribution -> contribution.date in start..today }
            .groupBy { contribution -> contribution.stableKey.ifBlank { contribution.exerciseName } }
            .mapNotNull { (_, grouped) ->
                val axes = grouped.fold(FatigueAxisValues()) { total, contribution ->
                    val daysSince = ChronoUnit.DAYS.between(contribution.date, today).toInt()
                    val generalDecay = FatigueDecayModel.factor(contribution.recoveryDurationClass, daysSince)
                    total + FatigueAxisValues(
                        highForceNeural = contribution.axes.highForceNeural * generalDecay,
                        systemicMuscular = contribution.axes.systemicMuscular * generalDecay,
                        localMuscular = contribution.axes.localMuscular * generalDecay,
                        highSpeed = contribution.axes.highSpeed * generalDecay,
                        reactive = contribution.axes.reactive * generalDecay,
                        recoveryPressure = contribution.axes.recoveryPressure * generalDecay
                    )
                }
                val axisScores = axisValues(axes)
                    .filter { it.second > 0.0001 }
                val score = axisScores.sumOf { it.second }
                if (score <= 0.0001) return@mapNotNull null
                val affectedAxes = axisScores
                    .sortedByDescending { it.second }
                    .take(2)
                    .map { it.first }
                CoachFatigueCause(
                    rank = 0,
                    label = grouped.first().exerciseName,
                    detail = "최근 ${safeWindow}일 동안 ${affectedAxes.joinToString("·")} 부하에 많이 기여한 기록입니다.",
                    contributionScore = score,
                    affectedAxes = affectedAxes,
                    sourceType = CoachFatigueCauseType.EXERCISE,
                    axisContributionScores = axisScores.toMap()
                )
            }

        val latestCheckIn = checkIns.asSequence()
            .filter { checkIn ->
                runCatching { LocalDate.parse(checkIn.date) }.getOrNull()?.let { date -> date in start..today } == true
            }
            .maxByOrNull { checkIn -> checkIn.date }
        val checkInCauses = CoachCheckInInterpreter().causes(
            checkIn = latestCheckIn,
            baseScore = exerciseCauses.maxOfOrNull { cause -> cause.contributionScore } ?: 50.0
        )
        val causes = (exerciseCauses + checkInCauses)
            .sortedByDescending { cause -> cause.contributionScore }
            .take(limit.coerceAtLeast(1))
            .mapIndexed { index, cause -> cause.copy(rank = index + 1) }

        if (causes.isEmpty()) return CoachFatigueCauseSummary.insufficient(safeWindow)
        val top = exerciseCauses.maxByOrNull { cause -> cause.contributionScore } ?: causes.first()
        return CoachFatigueCauseSummary(
            windowDays = safeWindow,
            causes = causes,
            headline = "최근 피로 상승은 ${top.label} 기록과 ${top.affectedAxes.joinToString("·")} 부담이 함께 누적된 영향일 가능성이 큽니다.",
            isDataSufficient = true,
            axisExerciseCauses = exerciseCauses.sortedByDescending { cause -> cause.contributionScore }
        )
    }

    private fun axisValues(axes: FatigueAxisValues): List<Pair<String, Double>> = listOf(
        "고중량·힘 신경계" to axes.highForceNeural,
        "전신 근육" to axes.systemicMuscular,
        "국소 근육" to axes.localMuscular,
        "고속" to axes.highSpeed,
        "반응" to axes.reactive
    )
}
