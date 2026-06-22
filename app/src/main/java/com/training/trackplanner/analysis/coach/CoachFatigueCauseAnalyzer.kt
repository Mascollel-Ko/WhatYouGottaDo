package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.FatigueAxisValues
import com.training.trackplanner.analysis.fatigue.FatigueDecayModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CoachFatigueCauseAnalyzer {
    fun analyze(
        today: LocalDate,
        history: List<DailyFatigueResult>,
        windowDays: Int = 14,
        limit: Int = 5
    ): CoachFatigueCauseSummary {
        val safeWindow = windowDays.coerceAtLeast(1)
        val start = today.minusDays(safeWindow.toLong() - 1)
        val contributions = history.asSequence()
            .filter { result -> result.state.date in start..today }
            .flatMap { result -> result.recordContributions.asSequence() }
            .filter { contribution -> contribution.date in start..today }
            .toList()
        if (contributions.isEmpty()) return CoachFatigueCauseSummary.insufficient(safeWindow)

        val ranked = contributions
            .groupBy { contribution -> contribution.stableKey.ifBlank { contribution.exerciseName } }
            .mapNotNull { (_, grouped) ->
                val axes = grouped.fold(FatigueAxisValues()) { total, contribution ->
                    val daysSince = ChronoUnit.DAYS.between(contribution.date, today).toInt()
                    val generalDecay = FatigueDecayModel.factor(contribution.recoveryDurationClass, daysSince)
                    val jointDecay = FatigueDecayModel.factor(contribution.jointRecoveryDurationClass, daysSince)
                    total + FatigueAxisValues(
                        neuromuscular = contribution.axes.neuromuscular * generalDecay,
                        systemicMuscular = contribution.axes.systemicMuscular * generalDecay,
                        localMuscular = contribution.axes.localMuscular * generalDecay,
                        jointTendonImpact = contribution.axes.jointTendonImpact * jointDecay,
                        movementFocus = contribution.axes.movementFocus * generalDecay,
                        recoveryPressure = contribution.axes.recoveryPressure * generalDecay
                    )
                }
                val score = axes.values().sum()
                if (score <= 0.0001) return@mapNotNull null
                AggregatedCause(grouped.first().exerciseName, axes, score)
            }
            .sortedByDescending { cause -> cause.score }
            .take(limit.coerceAtLeast(1))

        if (ranked.isEmpty()) return CoachFatigueCauseSummary.insufficient(safeWindow)
        val causes = ranked.mapIndexed { index, cause ->
            val affectedAxes = axisValues(cause.axes).filter { it.second > 0.0001 }
                .sortedByDescending { it.second }
                .take(2)
                .map { it.first }
            CoachFatigueCause(
                rank = index + 1,
                label = cause.label,
                detail = "최근 ${safeWindow}일 ${affectedAxes.joinToString("·")} 부담에 많이 누적된 기록입니다.",
                contributionScore = cause.score,
                affectedAxes = affectedAxes,
                sourceType = CoachFatigueCauseType.EXERCISE
            )
        }
        val top = causes.first()
        return CoachFatigueCauseSummary(
            windowDays = safeWindow,
            causes = causes,
            headline = "최근 피로 상승은 ${top.label} 기록과 ${top.affectedAxes.joinToString("·")} 부담이 함께 누적된 영향일 가능성이 큽니다.",
            isDataSufficient = true
        )
    }

    private fun axisValues(axes: FatigueAxisValues): List<Pair<String, Double>> = listOf(
        "신경계" to axes.neuromuscular,
        "전신 근육" to axes.systemicMuscular,
        "국소 근육" to axes.localMuscular,
        "관절/건/충격" to axes.jointTendonImpact,
        "동작 집중" to axes.movementFocus,
        "회복 지속" to axes.recoveryPressure
    )

    private data class AggregatedCause(
        val label: String,
        val axes: FatigueAxisValues,
        val score: Double
    )
}
