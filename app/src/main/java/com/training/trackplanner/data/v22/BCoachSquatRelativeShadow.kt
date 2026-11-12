package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Gate B, SHADOW ONLY: compare immediate working-set alternatives relative to an ACTUALLY
 * confirmed squat set. Does not prescribe a program, write the database, or select a winner.
 *
 * The canonical nonlinear repetition curve and canonical RPE->RIR mixture are reused.
 * Absolute e1RM is *not* used as a small-increment discriminator.
 */
internal object BCoachSquatRelativeShadow {
    enum class Action {
        HOLD,
        LOAD_UP_ONE_STEP,
        LOAD_UP_TWO_STEPS,
        LOAD_DOWN_ONE_STEP,
        REPS_UP_ONE,
        LOAD_UP_ONE_STEP_REPS_DOWN_ONE,
        SETS_UP_ONE,
        SETS_DOWN_ONE
    }

    enum class GridStatus {
        /** Multiple distinct loads are consistent with the configured total barbell step. */
        CONSISTENT,
        /** Not enough distinct confirmed loads to infer whether settings match real equipment. */
        INSUFFICIENT_OBSERVATIONS,
        /** Some observed differences do not lie on the configured barbell step lattice. */
        CONFLICT_REVIEW_REQUIRED
    }

    enum class ResultStatus {
        READY,
        NO_CONFIRMED_ANCHOR,
        NO_VALID_ANCHOR_RPE
    }

    enum class EvaluationStatus {
        READY,
        PARTIALLY_SUPPORTED,
        NO_VALID_ANCHOR_RPE,
        OUTSIDE_CURVE_DOMAIN
    }

    internal data class GridAudit(
        val configuredTotalStepKg: Double,
        val status: GridStatus,
        /** Smallest distinct recorded positive difference, NOT an approved equipment increment. */
        val smallestObservedGapKg: Double?,
        val observedLoadsKg: List<Double>,
        val userConfirmationRequired: Boolean
    )

    internal data class Scenario(
        val anchorRir: Int,
        val probability: Double,
        /** Null means the canonical curve cannot evaluate this scenario. */
        val projectedRir: Double?,
        val reason: String?
    )

    internal data class Candidate(
        val action: Action,
        val totalLoadKg: Double,
        val repetitions: Int,
        val plannedSetCount: Int,
        val evaluationStatus: EvaluationStatus,
        /** Mass over the ORIGINAL RPE/RIR mixture, not a calibrated success probability. */
        val supportedMixtureMass: Double,
        val targetRirMixtureMass: Double,
        /** Conditional on supported mixture mass; never a probability of future success. */
        val expectedRirGivenSupport: Double?,
        val scenarios: List<Scenario>,
        val relativeLoadIncrease: Double,
        val relativeSetIncrease: Double,
        val changedLeverCount: Int,
        /** Descriptive projected work / confirmed work; not a proven training benefit. */
        val nominalVolumeLoadDelta: Double,
        /** Only the reference working set is RIR-evaluated; remaining sets are not. */
        val followUpSetsNotPredicted: Int
    )

    internal data class Result(
        val status: ResultStatus,
        val anchorSet: BCoachConfirmedSquatSet?,
        val anchorSessionDate: java.time.LocalDate?,
        val grid: GridAudit,
        val lowerBoundCapacityFromAnchorKg: Double?,
        val observedLowerBoundExceedsPosteriorHigh80: Boolean,
        val candidates: List<Candidate>,
        val notes: List<String>
    )

    /**
     * Generates a fixed neighborhood. All target conditions are explicit caller inputs;
     * no arbitrary default RIR or change-amplitude parameter is silently introduced.
     *
     * 1) q(lastReps + possibleLastRir) = observedLoad / latentCapacity
     * 2) q(nextReps + nextRir) = q(lastReps + possibleLastRir) * nextLoad/observedLoad
     * => latentCapacity cancels, even when the absolute posterior is broad.
     */
    fun evaluate(
        evidence: BCoachSquatEvidence,
        rirPolicy: RpeRirPolicy,
        smallestPlateOneSideKg: Double,
        targetRirMin: Double,
        targetRirMax: Double
    ): Result {
        require(smallestPlateOneSideKg.isFinite() && smallestPlateOneSideKg in 0.1..25.0)
        require(targetRirMin.isFinite() && targetRirMax.isFinite())
        require(targetRirMin >= 0.0 && targetRirMax >= targetRirMin)

        // Exact existing app rule: one plate PER SIDE, so the full barbell increment is x2.
        val step = BBlockPeriodization.barbellIncrement(smallestPlateOneSideKg)
        val grid = auditGrid(step, evidence.recentObservedLoadsKg)
        val anchorSession = evidence.latestAnchor
        val anchor = anchorSession?.confirmedSets
            ?.maxWithOrNull(compareBy<BCoachConfirmedSquatSet>({ it.loadKg }, { -it.setIndex }))
        val notes = mutableListOf<String>()
        if (grid.userConfirmationRequired) {
            notes += "RECENT_LOAD_GRID_CONFLICT: compare configured and observed steps; do not silently alter settings"
        }
        if (anchor == null) return Result(
            ResultStatus.NO_CONFIRMED_ANCHOR, null, null, grid, null, false, emptyList(),
            notes + "Confirmed actual squat working set is required; no fabricated anchor"
        )
        val lowerBoundFromObserved = evidence.curve.evaluate(anchor.repetitions.toDouble())
            .takeIf { it.status == RepetitionCurveEvaluationStatus.SUPPORTED }
            ?.relativeLoad?.takeIf { it > 0.0 }
            ?.let { anchor.loadKg / it }
        val tension = lowerBoundFromObserved != null &&
            lowerBoundFromObserved > evidence.capacity.high80
        if (tension) notes += "OBSERVED_CAPACITY_LOWER_BOUND_EXCEEDS_POSTERIOR_HIGH80: review calibration, not a proven failure"
        if (anchorSession.confirmedSets.size > 1) {
            notes += "FOLLOW_UP_SETS_TARGET_ONLY: no fatigue/rep-performance distribution for later sets"
        }

        val rirMixture = anchor.observedRpe?.let(rirPolicy::resolve)
        val setCount = anchorSession.confirmedSets.size
        val lastVolumeLoad = anchorSession.confirmedSets.sumOf { it.loadKg * it.repetitions }
        val specs = listOf(
            Action.HOLD to Triple(anchor.loadKg, anchor.repetitions, setCount),
            Action.LOAD_UP_ONE_STEP to Triple(anchor.loadKg + step, anchor.repetitions, setCount),
            Action.LOAD_UP_TWO_STEPS to Triple(anchor.loadKg + 2 * step, anchor.repetitions, setCount),
            Action.LOAD_DOWN_ONE_STEP to Triple(anchor.loadKg - step, anchor.repetitions, setCount),
            Action.REPS_UP_ONE to Triple(anchor.loadKg, anchor.repetitions + 1, setCount),
            Action.LOAD_UP_ONE_STEP_REPS_DOWN_ONE to Triple(anchor.loadKg + step, anchor.repetitions - 1, setCount),
            Action.SETS_UP_ONE to Triple(anchor.loadKg, anchor.repetitions, setCount + 1),
            Action.SETS_DOWN_ONE to Triple(anchor.loadKg, anchor.repetitions, setCount - 1)
        ).filter { (_, spec) ->
            spec.first.isFinite() && spec.first > 0.0 &&
                spec.second in 1..20 && spec.third >= 1
        }

        val candidates = specs.map { (action, prescription) ->
            val (load, reps, sets) = prescription
            val scenarios = rirMixture?.probabilities?.map { item ->
                val lastRepsAtLimit = anchor.repetitions + item.rir.toDouble()
                val lastQ = evidence.curve.evaluate(lastRepsAtLimit)
                val nextQ = (lastQ.relativeLoad ?: Double.NaN) * load / anchor.loadKg
                val inverse = evidence.curve.profile.invert(nextQ)
                val nextTotalRepsAtLimit = inverse.repetitions?.let { baseReps ->
                    1.0 + exp(evidence.curve.personalTheta) * (baseReps - 1.0)
                }
                if (lastQ.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                    inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                    nextTotalRepsAtLimit == null || !nextTotalRepsAtLimit.isFinite()
                ) {
                    Scenario(item.rir, item.probability, null, "OUTSIDE_CANONICAL_CURVE")
                } else {
                    // A negative projected RIR indicates the proposed reps exceed the modeled
                    // repetitions-at-limit. Do not clip to zero or count as a successful set.
                    Scenario(item.rir, item.probability, nextTotalRepsAtLimit - reps, null)
                }
            }.orEmpty()
            val supported = scenarios.filter { it.projectedRir != null }
            val supportedMass = supported.sumOf(Scenario::probability)
            val targetMass = supported.filter { checkNotNull(it.projectedRir) in targetRirMin..targetRirMax }
                .sumOf(Scenario::probability)
            val evaluationStatus = when {
                rirMixture == null -> EvaluationStatus.NO_VALID_ANCHOR_RPE
                supportedMass < 1e-12 -> EvaluationStatus.OUTSIDE_CURVE_DOMAIN
                supportedMass < 1.0 - 1e-8 -> EvaluationStatus.PARTIALLY_SUPPORTED
                else -> EvaluationStatus.READY
            }
            Candidate(
                action = action, totalLoadKg = load, repetitions = reps, plannedSetCount = sets,
                evaluationStatus = evaluationStatus,
                supportedMixtureMass = supportedMass,
                targetRirMixtureMass = targetMass,
                expectedRirGivenSupport = if (supportedMass > 0.0)
                    supported.sumOf { it.probability * checkNotNull(it.projectedRir) } / supportedMass
                else null,
                scenarios = scenarios,
                relativeLoadIncrease = max(0.0, load / anchor.loadKg - 1.0),
                relativeSetIncrease = max(0.0, sets.toDouble() / setCount - 1.0),
                changedLeverCount = listOf(
                    abs(load - anchor.loadKg) > 1e-8,
                    reps != anchor.repetitions,
                    sets != setCount
                ).count { it },
                nominalVolumeLoadDelta = (load * reps * sets - lastVolumeLoad) /
                    lastVolumeLoad.coerceAtLeast(1e-9),
                followUpSetsNotPredicted = sets - 1
            )
        }
        return Result(
            status = if (rirMixture == null) ResultStatus.NO_VALID_ANCHOR_RPE else ResultStatus.READY,
            anchorSet = anchor,
            anchorSessionDate = anchorSession.date,
            grid = grid,
            lowerBoundCapacityFromAnchorKg = lowerBoundFromObserved,
            observedLowerBoundExceedsPosteriorHigh80 = tension,
            candidates = candidates,
            notes = notes + listOf(
                "SHADOW_ONLY: no winner, no approved risk cutoffs or lambda weights",
                "Relative RIR is a same-anchor curve projection, not an independent future-session success forecast",
                "Mixture mass is conditional on reported RPE and the canonical curve"
            )
        )
    }

    fun auditGrid(configuredTotalStepKg: Double, observedLoads: List<Double>): GridAudit {
        require(configuredTotalStepKg.isFinite() && configuredTotalStepKg > 0.0)
        val loads = observedLoads.filter { it.isFinite() && it > 0.0 }.distinct().sorted()
        val gaps = loads.zipWithNext().map { (a, b) -> b - a }.filter { it > 1e-8 }
        val conflict = gaps.any { gap ->
            val quotient = gap / configuredTotalStepKg
            abs(quotient - kotlin.math.round(quotient)) > 1e-6
        }
        val status = when {
            gaps.isEmpty() -> GridStatus.INSUFFICIENT_OBSERVATIONS
            conflict -> GridStatus.CONFLICT_REVIEW_REQUIRED
            else -> GridStatus.CONSISTENT
        }
        return GridAudit(
            configuredTotalStepKg = configuredTotalStepKg,
            status = status,
            smallestObservedGapKg = gaps.minOrNull(),
            observedLoadsKg = loads,
            userConfirmationRequired = conflict
        )
    }
}
