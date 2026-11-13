package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Uses confirmed linked squat work as a CURRENT capacity observation, even when
 * the persisted multi-session posterior has not processed the newest workout.
 * The actual RPE->RIR distribution and canonical nonlinear repetition curve
 * supply e1RM scenarios and next-load projected RIR; no RPE<=7 shortcut.
 *
 * Not a prediction of future success or adaptation and not a substitute for
 * the personalized posterior when that has been evaluated.
 */
internal object BCoachObservedSquatProgression {
    data class ProposalEvidence(
        val anchor: BCoachSquatAnchorSession,
        val ranking: BCoachSquatCurveProgressionReport
    )

    private const val EPS = 1e-6
    private const val TARGET_RIR_MIN = 1.0
    private const val TARGET_RIR_MAX = 3.0
    private const val TARGET_RIR = 2.0
    private const val MAX_LOW_RIR_MIXTURE_MASS = 0.10
    private const val MAX_SINGLE_STEP_FRACTION = 0.02

    private data class CurveScenario(
        val probability: Double,
        val estimatedOneRm: Double,
        val projectedRir: Double
    )
    private data class Evaluation(
        val loadKg: Double,
        val scenarios: List<CurveScenario>,
        val expectedE1rmKg: Double,
        val averageSquaredRirError: Double,
        val lowRirMixtureMass: Double,
        val targetRirMixtureMass: Double,
        val requiredE1rmKg: Double
    )

    fun evaluate(
        targetEntryId: Long,
        asOf: LocalDate,
        links: List<ProgramWorkoutLink>,
        records: List<WorkoutEntryWithSets>,
        prescriptions: List<ProgramPrescriptionSet>,
        plateOneSideKg: Double,
        curve: ResolvedRepetitionCurve,
        rirPolicy: RpeRirPolicy,
        exerciseStableKey: String = BCoachLinkedSquatWeekPlanner.SQUAT,
        loadStepKg: Double? = null,
        sourceBodyWeightKg: Double? = null,
        targetBodyWeightKg: Double? = null,
        maxSingleStepFraction: Double = MAX_SINGLE_STEP_FRACTION
    ): ProposalEvidence? {
        if (!plateOneSideKg.isFinite() || plateOneSideKg !in 0.1..25.0 ||
            !maxSingleStepFraction.isFinite() || maxSingleStepFraction !in 0.0..0.20) return null
        val targetLink = links.singleOrNull { it.entryId == targetEntryId } ?: return null
        val predecessor = BCoachProgramLinkChain.predecessor(targetLink, links)
        if (!predecessor.found) return null
        val sourceLink = predecessor.link ?: return null
        val source = records.singleOrNull { it.entry.id == sourceLink.entryId } ?: return null
        val target = records.singleOrNull { it.entry.id == targetEntryId } ?: return null
        if (source.entry.exerciseStableKey != exerciseStableKey ||
            target.entry.exerciseStableKey != exerciseStableKey) return null
        val bodyweightLift = exerciseStableKey == BCoachMainPolicy.WEIGHTED_PULL_UP
        if (bodyweightLift && (sourceBodyWeightKg == null || targetBodyWeightKg == null ||
                !sourceBodyWeightKg.isFinite() || sourceBodyWeightKg <= 0 ||
                !targetBodyWeightKg.isFinite() || targetBodyWeightKg <= 0)) return null
        val day = runCatching { LocalDate.parse(source.entry.date) }.getOrNull() ?: return null
        val targetDay = runCatching { LocalDate.parse(target.entry.date) }.getOrNull() ?: return null
        if (day > asOf || targetDay < asOf || targetDay < day) return null

        val done = source.sets.sortedBy { it.setIndex }
        val planned = target.sets.sortedBy { it.setIndex }
        val sourceOriginal = prescriptions.filter { it.entryId == source.entry.id }
        val targetOriginal = prescriptions.filter { it.entryId == targetEntryId }
        if (done.isEmpty() || planned.isEmpty() || sourceOriginal.size != done.size ||
            targetOriginal.size != planned.size ||
            done.any { !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() ||
                (if (bodyweightLift) it.weightKg < 0.0 else it.weightKg <= 0.0) ||
                it.reps !in 1..20 || (it.rpe ?: source.entry.rpe)?.let { r ->
                    !r.isFinite() || r !in 6.0..10.0
                } != false
            }) return null
        val load = done.first().weightKg
        val reps = done.first().reps
        if (done.any { abs(it.weightKg - load) > EPS || it.reps != reps }) return null
        // A changed/reduced original goal never manufactures a completed program.
        if (sourceOriginal.any { p ->
            !p.originalExists || p.plannedSetIndex == null ||
                done.singleOrNull { it.setIndex == p.plannedSetIndex }?.let { actual ->
                    actual.reps < maxOf(p.originalReps, p.plannedReps) ||
                        actual.weightKg + EPS < maxOf(p.originalKg, p.plannedKg) ||
                        actual.seconds < maxOf(p.originalSeconds, p.plannedSeconds)
                } != false
        }) return null

        // Only a comparable repetition scheme is automatically eligible.
        // Deliberate heavy/volume switches require a separate policy.
        if (targetOriginal.any { !it.originalExists || it.originalReps != reps } ||
            planned.any { it.reps != reps || it.confirmed } ||
            targetOriginal.mapNotNull { it.plannedSetIndex }.distinct().size != planned.size
        ) return null

        val maxRpe = done.maxOf { it.rpe ?: source.entry.rpe!! }
        val rirDistribution = rirPolicy.resolve(maxRpe)
        val step = loadStepKg ?: BBlockPeriodization.barbellIncrement(plateOneSideKg)
        val observedTotal = load + (if (bodyweightLift) checkNotNull(sourceBodyWeightKg) else 0.0)
        if (observedTotal <= 0.0) return null

        fun score(candidateKg: Double): Evaluation? {
            val distribution = rirDistribution ?: return null
            val candidateTotal = candidateKg +
                (if (bodyweightLift) checkNotNull(targetBodyWeightKg) else 0.0)
            if (candidateTotal <= 0.0) return null
            val scenarios = distribution.probabilities.map { item ->
                val relative = curve.evaluate(reps + item.rir.toDouble())
                if (relative.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                val q = relative.relativeLoad?.takeIf { it.isFinite() && it > 0.0 }
                    ?: return null
                val projectedRelative = q * candidateTotal / observedTotal
                val inverse = curve.profile.invert(projectedRelative)
                if (inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED) return null
                val effectiveRepLimit = inverse.repetitions?.let {
                    1.0 + exp(curve.personalTheta) * (it - 1.0)
                } ?: return null
                val projectedRir = effectiveRepLimit - reps
                if (!projectedRir.isFinite()) return null
                CurveScenario(item.probability, observedTotal / q, projectedRir)
            }
            if (abs(scenarios.sumOf { it.probability } - 1.0) > EPS) return null
            val requiredRelative = curve.evaluate(reps + TARGET_RIR)
            val requiredQ = requiredRelative.relativeLoad ?: return null
            if (requiredRelative.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                requiredQ <= 0.0) return null
            return Evaluation(
                loadKg = candidateKg,
                scenarios = scenarios,
                expectedE1rmKg = scenarios.sumOf { it.probability * it.estimatedOneRm },
                averageSquaredRirError = scenarios.sumOf {
                    it.probability * (it.projectedRir - TARGET_RIR) *
                        (it.projectedRir - TARGET_RIR)
                },
                lowRirMixtureMass = scenarios.filter { it.projectedRir < TARGET_RIR_MIN }
                    .sumOf { it.probability },
                targetRirMixtureMass = scenarios.filter {
                    it.projectedRir in TARGET_RIR_MIN..TARGET_RIR_MAX
                }.sumOf { it.probability },
                requiredE1rmKg = candidateTotal / requiredQ
            )
        }

        val hold = score(load)
        val up = if (step.isFinite() && step > 0 && step / observedTotal <= maxSingleStepFraction)
            score(load + step) else null
        // The same canonical q curve computes both capacity and projected RIR.
        // Prefer a one-step increase only when it improves target-RIR fit without
        // appreciable low-RIR tail risk. Four completed sets corroborate session
        // completion but do not imply four independent maximal-capacity draws.
        val increase = hold != null && up != null &&
            up.lowRirMixtureMass <= MAX_LOW_RIR_MIXTURE_MASS + EPS &&
            up.averageSquaredRirError + EPS < hold.averageSquaredRirError &&
            planned.size <= done.size
        val chosen = if (increase) checkNotNull(up) else hold
        val kg = chosen?.loadKg ?: load
        val action = if (increase) "LOAD_UP_ONE_STEP" else "HOLD"
        val anchor = BCoachSquatAnchorSession(
            sessionStableKey = source.entry.sessionStableKey,
            date = day,
            confirmedSets = done.map {
                BCoachConfirmedSquatSet(it.id, it.setIndex, it.weightKg, it.reps,
                    it.rpe ?: source.entry.rpe)
            }
        )
        val candidate = BCoachSquatCurveProgressionReport.Candidate(
            action = action, loadKg = kg, reps = reps, sets = planned.size,
            requiredE1rmKg = chosen?.requiredE1rmKg,
            historicalE1rmLogGap = chosen?.let {
                abs(ln(it.requiredE1rmKg / it.expectedE1rmKg))
            },
            worstCaseRegret = null,
            weightedCost = chosen?.averageSquaredRirError,
            targetRirMixtureMass = chosen?.targetRirMixtureMass,
            volumeLoadChange = ((kg + (if (bodyweightLift) checkNotNull(targetBodyWeightKg) else 0.0)) * reps * planned.size) /
                (observedTotal * reps * done.size) - 1.0,
            eligible = true,
            reason = when {
                increase -> "LINKED_E1RM_CURVE_RIR_ONE_STEP"
                hold != null -> "LINKED_E1RM_CURVE_RIR_HOLD"
                else -> "LINKED_CONFIRMED_HOLD_CURVE_UNSUPPORTED"
            }
        )
        return ProposalEvidence(
            anchor = anchor,
            ranking = BCoachSquatCurveProgressionReport(
                status = if (chosen == null) "CONFIRMED_HOLD_CURVE_UNSUPPORTED"
                    else "LINKED_E1RM_CURVE_RIR_RANKING",
                asOf = asOf, referenceDate = day,
                selectedAction = action,
                historicalE1rmKg = chosen?.expectedE1rmKg,
                targetRir = TARGET_RIR, doseTargetSets = planned.size,
                ranked = listOf(candidate),
                notes = listOf(
                    "확정된 연결 세트, 기존 RPE→RIR 분포, 비선형 반복곡선으로 e1RM 시나리오를 계산했습니다.",
                    "다음 중량은 목표 RIR과 위험 구간을 비교해 결정합니다. 미래 성공률이 아닙니다.",
                    "후속 세트의 누적피로는 별도로 예측하지 않습니다."
                )
            )
        )
    }
}
