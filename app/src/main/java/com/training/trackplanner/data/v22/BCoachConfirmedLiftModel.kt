package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/** An observation of fresh capacity uses the first work set. Later sets have their
 * own fatigue correction in the load resolver. The grid is applied at prescription time. */
internal object BCoachConfirmedLiftModel {
    val keys = setOf("barbell_back_squat", "barbell_deadlift", "barbell_bench_press", "ex_3a7d3eda")

    /** Compare the planned load with one equipment step in either direction.
     * Match set position so a fatigued back-off set cannot calibrate the top set. */
    data class SetDose(val kg: Double, val reps: Int, val failureRisk: Double = 0.0)

    fun nextDoses(source: WorkoutEntryWithSets, target: WorkoutEntryWithSets,
                  curve: ResolvedRepetitionCurve, policy: RpeRirPolicy, step: Double): Map<Int, SetDose>? {
        if (source.entry.exerciseStableKey != target.entry.exerciseStableKey ||
            source.entry.exerciseStableKey !in keys || !step.isFinite() || step <= 0.0 ||
            source.sets.isEmpty() || source.sets.any { !it.confirmed }) return null
        val matches = target.sets.filter { planned -> source.sets.any { it.setIndex == planned.setIndex } }
        return matches.associate { planned ->
            val done = source.sets.singleOrNull { it.setIndex == planned.setIndex } ?: return null
            if (!done.confirmed || planned.confirmed || done.reps !in 1..20 || planned.reps !in 1..20 ||
                done.loadState != ProgramLoadState.EXPLICIT_LOAD || !done.weightKg.isFinite() || done.weightKg <= 0 ||
                planned.manualWeight || planned.loadSource == ProgramLoadSource.USER_FIXED) return null
            val rpe = done.rpe ?: source.entry.rpe ?: return null
            if (!rpe.isFinite() || rpe !in 6.0..10.0) return null
            val mixture = policy.resolve(rpe) ?: return null
            val current = if (planned.loadState == ProgramLoadState.EXPLICIT_LOAD) planned.weightKg else done.weightKg
            if (!current.isFinite() || current <= 0.0) return null
            val goal = 10.0 - (planned.targetRpeMin ?: 8.0)
            if (!goal.isFinite() || goal !in 0.0..5.0) return null
            val capacities = mixture.probabilities.filter { it.probability > 0.0 }.map { row ->
                val point = curve.evaluate(done.reps + row.rir.toDouble())
                if (point.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                done.weightKg / (point.relativeLoad ?: return null) to row.probability
            }
            if (capacities.isEmpty()) return null
            fun projectedRir(dose: SetDose, capacity: Double): Double? {
                val inverse = curve.profile.invert(dose.kg / capacity)
                if (inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                return 1.0 + kotlin.math.exp(curve.personalTheta) *
                    ((inverse.repetitions ?: return null) - 1.0) - dose.reps
            }
            fun loss(dose: SetDose): Double {
                if (dose.kg <= 0.0 || dose.reps !in 1..20) return Double.POSITIVE_INFINITY
                return capacities.sumOf { (capacity, probability) ->
                    val rir = projectedRir(dose, capacity) ?: return Double.POSITIVE_INFINITY
                    probability * ((rir - goal) * (rir - goal) + 4.0 * maxOf(0.0, 1.0 - rir))
                }
            }
            val hold = SetDose(current, planned.reps)
            val maximumStep = if (target.entry.exerciseStableKey == "ex_3a7d3eda") 0.07 else 0.05
            val candidates = if (step / current <= maximumStep) {
                listOf(hold.copy(kg = current - step), hold.copy(kg = current + step))
            } else {
                listOf(hold.copy(reps = planned.reps - 1), hold.copy(reps = planned.reps + 1))
            }
            val holdLoss = loss(hold)
            val best = candidates.minBy(::loss)
            val selected = if (loss(best) + 0.05 < holdLoss) best else hold
            if (!loss(selected).isFinite()) return null
            if (planned.loadState != ProgramLoadState.EXPLICIT_LOAD && capacities.any { (capacity, _) ->
                    (projectedRir(selected, capacity) ?: return null) < 0.0
                }) return null
            val failureRisk = capacities.sumOf { (capacity, probability) ->
                if ((projectedRir(selected, capacity) ?: return null) < 0.0) probability else 0.0
            }
            planned.setIndex to selected.copy(failureRisk = failureRisk)
        }
    }

    fun snapshots(asOf: LocalDate, records: List<WorkoutEntryWithSets>, curves: RepetitionCurveRegistry?,
                  policy: RpeRirPolicy?): Map<String, BCoachV2LoadModelSnapshot> {
        if (curves == null || policy == null) return emptyMap()
        return keys.mapNotNull { key ->
            val latest = records.filter { it.entry.exerciseStableKey == key && it.sets.isNotEmpty() &&
                it.sets.all { set -> set.confirmed } &&
                it.entry.date in asOf.minusDays(55).toString()..asOf.toString() }
                .maxWithOrNull(compareBy<WorkoutEntryWithSets> { it.entry.date }.thenBy { it.entry.id })
                ?: return@mapNotNull null
            if (latest.sets.any { !it.confirmed || it.reps !in 1..20 ||
                    it.loadState != ProgramLoadState.EXPLICIT_LOAD || !it.weightKg.isFinite() ||
                    it.weightKg <= 0.0 }) return@mapNotNull null
            val first = latest.sets.minByOrNull { it.setIndex } ?: return@mapNotNull null
            val rpe = first.rpe ?: latest.entry.rpe ?: return@mapNotNull null
            if (!rpe.isFinite() || rpe !in 6.0..10.0) return@mapNotNull null
            val curve = curves.resolve(key)
            val mixture = policy.resolve(rpe) ?: return@mapNotNull null
            val capacities = mixture.probabilities.filter { it.probability > 0.0 }.map { scenario ->
                val point = curve.evaluate(first.reps + scenario.rir.toDouble())
                val q = point.relativeLoad
                if (point.status != RepetitionCurveEvaluationStatus.SUPPORTED || q == null ||
                    !q.isFinite() || q <= 0.0) return@mapNotNull null
                first.weightKg / q to scenario.probability
            }
            if (capacities.isEmpty()) return@mapNotNull null
            key to BCoachV2LoadModelSnapshot(key, "CONFIRMED_LIFT_CURVE",
                capacities.minOf { it.first }, capacities.sumOf { it.first * it.second },
                capacities.maxOf { it.first }, curve,
                "CONFIRMED_CURVE_V1|${curve.profile.provenance.curveVersion}|${policy.version}",
                listOf("${latest.entry.id}:${first.id}:${first.weightKg}:${first.reps}:$rpe"))
        }.toMap()
    }
}
