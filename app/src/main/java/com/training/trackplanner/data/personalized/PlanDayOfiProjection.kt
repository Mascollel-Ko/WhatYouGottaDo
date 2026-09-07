package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.fatigue.FatigueThresholds
import com.training.trackplanner.data.*
import java.time.LocalDate

data class StandaloneDayLoad(val ofi: Int, val axisScores: List<Int>, val cautionReasons: List<String> = emptyList()) {
    val feasible: Boolean get() = ofi < FatigueThresholds.OFI_CAUTION_START &&
        axisScores.none { it >= FatigueThresholds.DAILY_AXIS_CAUTION_START } && cautionReasons.isEmpty()
}

fun interface PlanDayProjection {
    fun evaluate(items: List<ProgramSkeletonItem>): StandaloneDayLoad
}

/** Read-only comparison, never a forecast or a persisted performed workout. */
class PlanDayOfiProjection(
    val cutoff: LocalDate,
    private val calculator: DailyFatigueCalculator,
    private val exercises: List<Exercise>,
    history: List<WorkoutEntryWithSets>,
    private val profile: InitialUserProfile?,
    metrics: List<DailyMetric> = emptyList()
) : PlanDayProjection {
    val projectionDate: LocalDate = cutoff.plusDays(1)
    private val history = history.filter { runCatching { LocalDate.parse(it.entry.date) <= cutoff }.getOrDefault(false) }
        .map { it.copy(sets = it.sets.filter(WorkoutSet::confirmed)) }
    private val metrics = metrics.filter { runCatching { LocalDate.parse(it.date) <= cutoff }.getOrDefault(false) }
    internal fun syntheticRows(items: List<ProgramSkeletonItem>): List<WorkoutEntryWithSets> = items.mapIndexed { index, item ->
        val id = -(index + 1L)
        WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = projectionDate.toString(), exerciseStableKey = item.exerciseStableKey,
                exerciseName = item.exerciseName, category = item.category, restSeconds = item.restSeconds,
                rpe = null, createdAt = 0L, displayOrder = index),
            item.setPrescriptions.map { set -> WorkoutSet(entryId = id, setIndex = set.setIndex,
                reps = set.reps, weightKg = set.weightKg, seconds = set.seconds, confirmed = true, rpe = null) }
        )
    }
    override fun evaluate(items: List<ProgramSkeletonItem>): StandaloneDayLoad {
        val result = calculator.calculate(projectionDate, exercises, history + syntheticRows(items), profile, metrics).state
        return StandaloneDayLoad(result.overallFatigueIndex, listOf(result.highForceNeuralScore,
            result.systemicMuscularScore, result.localMuscularScore, result.highSpeedScore,
            result.reactiveScore, result.recoveryPressureScore), result.cautionReasons)
    }
}
