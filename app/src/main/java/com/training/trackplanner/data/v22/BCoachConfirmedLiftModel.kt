package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/** An observation of fresh capacity uses the first work set. Later sets have their
 * own fatigue correction in the load resolver. The grid is applied at prescription time. */
internal object BCoachConfirmedLiftModel {
    val keys = setOf("barbell_back_squat", "barbell_deadlift", "barbell_bench_press", "ex_3a7d3eda")

    /** Compare the planned load with one equipment step in either direction.
     * Match set position so a fatigued back-off set cannot calibrate the top set. */
    fun nextLoads(source: WorkoutEntryWithSets, target: WorkoutEntryWithSets,
                  curve: com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve,
                  policy: RpeRirPolicy, step: Double): Map<Int, Double>? {
        if (source.entry.exerciseStableKey != target.entry.exerciseStableKey ||
            source.entry.exerciseStableKey !in keys || !step.isFinite() || step <= 0.0 ||
            source.sets.size != target.sets.size) return null
        return target.sets.associate { planned ->
            val done = source.sets.singleOrNull { it.setIndex == planned.setIndex } ?: return null
            if (!done.confirmed || planned.confirmed || done.reps !in 1..20 || planned.reps !in 1..20 ||
                done.loadState != ProgramLoadState.EXPLICIT_LOAD || !done.weightKg.isFinite() || done.weightKg <= 0 ||
                planned.loadSource == com.training.trackplanner.data.ProgramLoadSource.USER_FIXED) return null
            val mixture = policy.resolve(done.rpe ?: source.entry.rpe ?: return null) ?: return null
            val current = if (planned.loadState == ProgramLoadState.EXPLICIT_LOAD) planned.weightKg else done.weightKg
            if (!current.isFinite() || current <= 0.0) return null
            val goal = 10.0 - (planned.targetRpeMin ?: 8.0)
            if (!goal.isFinite() || goal !in 0.0..5.0) return null
            val capacities = mixture.probabilities.map { row ->
                val point = curve.evaluate(done.reps + row.rir.toDouble())
                if (point.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                done.weightKg / (point.relativeLoad ?: return null) to row.probability
            }
            fun loss(kg: Double): Double {
                if (kg <= 0) return Double.POSITIVE_INFINITY
                return capacities.sumOf { (capacity, probability) ->
                    val inverse = curve.profile.invert(kg / capacity)
                    if (inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED) return Double.POSITIVE_INFINITY
                    val rir = 1.0 + kotlin.math.exp(curve.personalTheta) *
                        ((inverse.repetitions ?: return Double.POSITIVE_INFINITY) - 1.0) - planned.reps
                    probability * ((rir - goal) * (rir - goal) + 4.0 * maxOf(0.0, 1.0 - rir))
                }
            }
            val hold = loss(current)
            val choices = if (step / current <= 0.07) listOf(current - step, current + step) else emptyList()
            val best = choices.minByOrNull(::loss)
            val selected = if (best != null && hold.isFinite() && loss(best) + 0.05 < hold) best else current
            planned.setIndex to selected
        }
    }

    fun snapshots(asOf: LocalDate, records: List<WorkoutEntryWithSets>, curves: RepetitionCurveRegistry?,
                  policy: RpeRirPolicy?): Map<String, BCoachV2LoadModelSnapshot> {
        if (curves == null || policy == null) return emptyMap()
        return keys.mapNotNull { key ->
            val latest = records.filter { it.entry.exerciseStableKey == key &&
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
