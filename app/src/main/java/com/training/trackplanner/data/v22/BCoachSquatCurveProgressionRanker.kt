package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Gate F: one deterministic SHADOW ranking for HOLD / LOAD / REPS / SETS.
 *
 * This does NOT write or authorize a prescription. Only the RPE->RIR uncertainty of the last
 * confirmed session is modeled. Whole-session fatigue, future-day readiness, posterior
 * capacity uncertainty and a validated training-adaptation utility remain outside this slice.
 */
data class BCoachSquatCurveProgressionReport(
    val status: String,
    val asOf: LocalDate,
    val referenceDate: LocalDate?,
    val selectedAction: String?,
    val historicalE1rmKg: Double?,
    val targetRir: Double,
    val doseTargetSets: Int?,
    val ranked: List<Candidate>,
    val notes: List<String>
) {
    data class Candidate(
        val action: String,
        val loadKg: Double,
        val reps: Int,
        val sets: Int,
        val requiredE1rmKg: Double?,
        val historicalE1rmLogGap: Double?,
        val worstCaseRegret: Double?,
        val weightedCost: Double?,
        val targetRirMixtureMass: Double?,
        val volumeLoadChange: Double,
        val eligible: Boolean,
        val reason: String
    )
}

/**
 * Stable comparator, deliberately NO unexplained reward for a load/rep/set increase.
 *
 * e0(s) = observedLoad / q(observedReps + anchorRir(s))
 * ereq(c) = candidateLoad / q(candidateReps + targetRir)
 *
 * cost(c,s) = [distance(projectedRir(c,s), targetBand) / bandWidth]^2
 *           + abs(ln(ereq(c)/e0(s)))
 *           + max(0, targetSets - candidateSets) / targetSets
 *
 * The final rank minimizes max_s(cost(c,s) - min_valid_action cost(action,s)),
 * followed by RPE-mixture-weighted cost, positive work increase and fixed action identity.
 * There is no progression credit; proven completion gates whether change actions compete.
 * Additional sets require an explicit *structural* dose target above observed set count.
 */
internal object BCoachSquatCurveProgressionRanker {
    private val actions = listOf(
        BCoachSquatRelativeShadow.Action.HOLD,
        BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP,
        BCoachSquatRelativeShadow.Action.REPS_UP_ONE,
        BCoachSquatRelativeShadow.Action.SETS_UP_ONE
    )
    private const val EPS = 1e-9

    fun evaluate(
        input: BCoachSquatShadowPipelineResult.ShadowReport,
        targetRirMin: Double,
        targetRirMax: Double,
        doseTargetSets: Int?,
        userConfirmedEquipmentStep: Boolean = false
    ): BCoachSquatCurveProgressionReport {
        require(targetRirMin.isFinite() && targetRirMax.isFinite() &&
            targetRirMin >= 0.0 && targetRirMax > targetRirMin)
        require(doseTargetSets == null || doseTargetSets > 0)
        val evidence = input.input
        val baseline = input.relative
        val last = evidence.latestAnchor
        val anchor = baseline.anchorSet
        val target = (targetRirMin + targetRirMax) / 2.0
        val candidates = baseline.candidates.filter { it.action in actions }.associateBy { it.action }
        val hold = candidates[BCoachSquatRelativeShadow.Action.HOLD]
        val actualSets = last?.confirmedSets.orEmpty()
        val progressEvidence = anchor != null && actualSets.isNotEmpty() &&
            actualSets.all { record ->
                record.loadKg == anchor.loadKg &&
                    record.repetitions == anchor.repetitions &&
                    record.observedRpe != null && record.observedRpe <= 10.0 - targetRirMin
            }
        val gridConflict = baseline.grid.userConfirmationRequired

        val reference = hold?.scenarios?.mapNotNull { state ->
            val q = evidence.curve.evaluate(anchor!!.repetitions + state.anchorRir.toDouble())
            val relative = q.relativeLoad
            if (state.probability <= 0.0 || q.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                relative == null || relative <= 0.0 || !relative.isFinite()
            ) null else Triple(state.anchorRir, state.probability, anchor.loadKg / relative)
        }.orEmpty()
        val fullReference = anchor != null && hold != null &&
            reference.isNotEmpty() && reference.size == hold.scenarios.size &&
            abs(reference.sumOf { it.second } - 1.0) <= 1e-6 &&
            hold.scenarios.all { it.projectedRir != null }
        val historicalE1rm = if (fullReference)
            reference.sumOf { it.second * it.third } else null

        data class Work(
            val candidate: BCoachSquatRelativeShadow.Candidate,
            val req: Double?,
            val logGap: Double?,
            val costs: Map<Int, Double>,
            val eligible: Boolean,
            val reason: String
        )
        val fullReferenceByRir = reference.associate { it.first to (it.second to it.third) }
        val evaluated = actions.mapNotNull { action ->
            val c = candidates[action] ?: return@mapNotNull null
            val reqCurve = evidence.curve.evaluate(c.repetitions + target)
            val req = reqCurve.relativeLoad?.takeIf {
                reqCurve.status == RepetitionCurveEvaluationStatus.SUPPORTED &&
                    it.isFinite() && it > 0.0
            }?.let { c.totalLoadKg / it }
            val supported = fullReference && req != null &&
                c.evaluationStatus == BCoachSquatRelativeShadow.EvaluationStatus.READY &&
                c.scenarios.size == reference.size &&
                c.scenarios.all { it.projectedRir != null && it.anchorRir in fullReferenceByRir }
            val doseGap = if (doseTargetSets == null) 0.0
                else max(0.0, (doseTargetSets - c.plannedSetCount).toDouble() / doseTargetSets)
            val bandWidth = max(1.0, targetRirMax - targetRirMin)
            val costs = if (supported) c.scenarios.associate { scenario ->
                val observed = checkNotNull(fullReferenceByRir[scenario.anchorRir]).second
                val projected = checkNotNull(scenario.projectedRir)
                val gap = when {
                    projected < targetRirMin -> targetRirMin - projected
                    projected > targetRirMax -> projected - targetRirMax
                    else -> 0.0
                }
                scenario.anchorRir to (
                    (gap / bandWidth) * (gap / bandWidth) +
                        abs(ln(checkNotNull(req) / observed)) + doseGap
                    )
            } else emptyMap()
            val logGap = if (supported) reference.sumOf { (rir, probability, observed) ->
                probability * abs(ln(checkNotNull(req) / observed))
            } else null
            val reason = when {
                !supported -> "CURVE_OR_RPE_SCENARIO_UNSUPPORTED"
                action == BCoachSquatRelativeShadow.Action.HOLD -> "OBSERVED_HOLD"
                !progressEvidence -> "NO_CONFIRMED_ALL_SET_RPE_HEADROOM"
                action == BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP &&
                    gridConflict && !userConfirmedEquipmentStep ->
                    "EQUIPMENT_INCREMENT_REVIEW"
                action == BCoachSquatRelativeShadow.Action.SETS_UP_ONE &&
                    (doseTargetSets == null || doseTargetSets <= actualSets.size) ->
                    "NO_SET_DOSE_SHORTFALL_EVIDENCE"
                else -> "EXPERIMENTAL_ELIGIBLE_NOT_APPROVED"
            }
            Work(c, req, logGap, costs, reason in setOf(
                "OBSERVED_HOLD", "EXPERIMENTAL_ELIGIBLE_NOT_APPROVED"
            ), reason)
        }
        val eligible = evaluated.filter { it.eligible }
        val states = reference.map { it.first }
        val bestByState = if (eligible.isNotEmpty() && states.isNotEmpty()) states.associateWith { rir ->
            eligible.minOf { checkNotNull(it.costs[rir]) }
        } else emptyMap()
        fun regret(work: Work): Double? = if (!work.eligible || bestByState.isEmpty()) null
            else states.maxOf { rir -> checkNotNull(work.costs[rir]) - checkNotNull(bestByState[rir]) }
                .coerceAtLeast(0.0)
        fun weightedCost(work: Work): Double? = if (!work.eligible || states.isEmpty()) null
            else reference.sumOf { (rir, probability, _) ->
                probability * checkNotNull(work.costs[rir])
            }
        val ordered = evaluated.sortedWith(
            compareBy<Work> { if (it.eligible) 0 else 1 }
                .thenBy { regret(it) ?: Double.POSITIVE_INFINITY }
                .thenBy { weightedCost(it) ?: Double.POSITIVE_INFINITY }
                .thenBy { max(0.0, it.candidate.nominalVolumeLoadDelta) }
                .thenBy { it.candidate.changedLeverCount }
                .thenBy { actions.indexOf(it.candidate.action) }
        )
        val selected = ordered.firstOrNull { it.eligible }?.candidate?.action?.name
        val status = when {
            !fullReference -> "INSUFFICIENT_CANONICAL_RPE_EVIDENCE"
            !progressEvidence -> "HOLD_ONLY_NO_VERIFIED_RPE_HEADROOM"
            gridConflict && !userConfirmedEquipmentStep -> "GRID_CONFLICT_SHADOW_REVIEW"
            gridConflict && userConfirmedEquipmentStep -> "CONFIRMED_GRID_HISTORICAL_GAPS_DIFFER"
            else -> "EXPERIMENTAL_RANKING_ONLY"
        }
        val rows = ordered.map { work ->
            BCoachSquatCurveProgressionReport.Candidate(
                action = work.candidate.action.name,
                loadKg = work.candidate.totalLoadKg,
                reps = work.candidate.repetitions,
                sets = work.candidate.plannedSetCount,
                requiredE1rmKg = work.req,
                historicalE1rmLogGap = work.logGap,
                worstCaseRegret = regret(work),
                weightedCost = weightedCost(work),
                targetRirMixtureMass = work.candidate.targetRirMixtureMass
                    .takeIf { work.candidate.evaluationStatus == BCoachSquatRelativeShadow.EvaluationStatus.READY },
                volumeLoadChange = work.candidate.nominalVolumeLoadDelta,
                eligible = work.eligible,
                reason = work.reason
            )
        }
        return BCoachSquatCurveProgressionReport(
            status = status,
            asOf = evidence.asOf,
            referenceDate = last?.date,
            selectedAction = selected,
            historicalE1rmKg = historicalE1rm,
            targetRir = target,
            doseTargetSets = doseTargetSets,
            ranked = rows,
            notes = listOf(
                "SHADOW_ONLY: top candidate is not a prescription or predicted training adaptation",
                "e1RM is derived from the confirmed anchor and existing nonlinear curve; it is not a new absolute posterior",
                "Anchor RPE/RIR mixture is not a future-workout success probability",
                "Only the reference working set has a modeled RIR; later sets' fatigue is unknown",
                "Observed confirmed sets do not prove completion of an earlier prescribed program",
                "Dose set target comes from generated plan structure, not independently validated dose response",
                when {
                    gridConflict && userConfirmedEquipmentStep ->
                        "Historical load gaps differ from the user-confirmed current plate grid; use the declared step for candidate comparison only"
                    gridConflict -> "Observed weight steps disagree with settings: do not auto-select load increase"
                    else -> "No historical weight-step mismatch detected (not proof of plate availability)"
                }
            )
        )
    }
}
