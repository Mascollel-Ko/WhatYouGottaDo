package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Common strength-exercise e1RM / RIR calculator, for explicitly MAIN or
 * manually progression-linked exercises outside the four canonical targets.
 *
 * Reuses the shipped exercise-assigned nonlinear repetition curve and reviewed
 * RPE→RIR mixture. No percentage-of-1RM shortcut or new strength curve.
 * The relative RIR projections are *not* probabilities of future success.
 */
internal object BCoachCommonStrengthRirCalculator {
    data class Candidate(
        val action: String,
        val loadKg: Double,
        val requiredE1rmKg: Double?,
        val observedE1rmKg: Double?,
        val expectedProjectedRir: Double?,
        val squaredRirError: Double?,
        val lowRirMass: Double?
    )

    private data class Scenario(val probability: Double, val rir: Double, val e1rmKg: Double)

    fun choose(
        performedKg: Double, performedReps: Int, reportedMaxRpe: Double,
        nextReps: Int, incrementKg: Double?,
        curve: ResolvedRepetitionCurve, rirPolicy: RpeRirPolicy
    ): Candidate? {
        if (!performedKg.isFinite() || performedKg <= 0.0 ||
            performedReps !in 1..20 || nextReps !in 1..20) return null
        val mass = rirPolicy.resolve(reportedMaxRpe) ?: return null
        val scenarios = mass.probabilities.map { item ->
            val relative = curve.evaluate(performedReps + item.rir.toDouble())
            if (relative.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
            val q = relative.relativeLoad?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            Scenario(item.probability, item.rir.toDouble(), performedKg / q)
        }

        fun evaluate(load: Double, name: String): Candidate? {
            val qTarget = curve.evaluate(nextReps + 2.0)
            if (qTarget.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
            val targetFraction = qTarget.relativeLoad?.takeIf { it > 0.0 } ?: return null
            val projections = scenarios.map { s ->
                val relative = load / s.e1rmKg
                val inverse = curve.profile.invert(relative)
                if (inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                val atFailure = inverse.repetitions?.let {
                    1.0 + exp(curve.personalTheta) * (it - 1.0)
                } ?: return null
                val rir = atFailure - nextReps
                if (!rir.isFinite()) return null
                s.probability to rir
            }
            val mean = projections.sumOf { it.first * it.second }
            return Candidate(
                action = name, loadKg = load,
                requiredE1rmKg = load / targetFraction,
                observedE1rmKg = scenarios.sumOf { it.probability * it.e1rmKg },
                expectedProjectedRir = mean,
                squaredRirError = projections.sumOf { it.first * (it.second - 2.0).pow(2) },
                lowRirMass = projections.filter { it.second < 1.0 }
                    .sumOf { it.first }
            )
        }

        val hold = evaluate(performedKg, "HOLD") ?: return null
        // A deliberate change of reps changes the intensity prescription. Do not
        // silently flatten a heavy/volume block or extrapolate a large load jump.
        // That transition must be reviewed in the plan, not automatically applied.
        if (performedReps != nextReps) return hold.copy(action = "REP_SCHEME_REVIEW")
        val step = incrementKg?.takeIf { it.isFinite() && it > 0.0 &&
            it / performedKg <= 0.02 } ?: return hold
        val up = evaluate(performedKg + step, "LOAD_UP_ONE_STEP") ?: return hold
        val improves = up.squaredRirError!! + 1e-8 < hold.squaredRirError!! &&
            up.lowRirMass!! <= 0.10 + 1e-8
        return if (improves) up else hold
    }
}
