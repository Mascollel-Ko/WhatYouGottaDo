package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import kotlin.math.abs

/**
 * Evidence-only progression for a linked, fully confirmed and uniform squat session.
 *
 * Used when the asynchronous strength-posterior state is not ready. This is a
 * transparent, bounded engineering rule, not a posterior probability or
 * physiological forecast. All future-target/plan-edit guards stay in Gate I.
 */
internal object BCoachObservedSquatProgression {
    data class ProposalEvidence(
        val anchor: BCoachSquatAnchorSession,
        val ranking: BCoachSquatCurveProgressionReport
    )
    private const val EPS = 1e-6

    fun evaluate(
        targetEntryId: Long,
        asOf: LocalDate,
        links: List<ProgramWorkoutLink>,
        records: List<WorkoutEntryWithSets>,
        prescriptions: List<ProgramPrescriptionSet>,
        plateOneSideKg: Double
    ): ProposalEvidence? {
        if (!plateOneSideKg.isFinite() || plateOneSideKg !in 0.1..25.0) return null
        val targetLink = links.singleOrNull { it.entryId == targetEntryId } ?: return null
        val predecessor = BCoachProgramLinkChain.predecessor(targetLink, links)
        if (!predecessor.found) return null
        val sourceLink = predecessor.link ?: return null
        val source = records.singleOrNull { it.entry.id == sourceLink.entryId } ?: return null
        val target = records.singleOrNull { it.entry.id == targetEntryId } ?: return null
        if (source.entry.exerciseStableKey != BCoachLinkedSquatWeekPlanner.SQUAT ||
            target.entry.exerciseStableKey != BCoachLinkedSquatWeekPlanner.SQUAT) return null
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
                !it.weightKg.isFinite() || it.weightKg <= 0.0 ||
                it.reps !in 1..20 || (it.rpe ?: source.entry.rpe)?.let { r ->
                    !r.isFinite() || r !in 6.0..10.0
                } != false
            }) return null
        val load = done.first().weightKg
        val reps = done.first().reps
        if (done.any { abs(it.weightKg - load) > EPS || it.reps != reps }) return null
        // Never treat intentionally missed/reduced original prescriptions as success.
        if (sourceOriginal.any { p ->
            !p.originalExists || p.plannedSetIndex == null ||
                done.singleOrNull { it.setIndex == p.plannedSetIndex }?.let { actual ->
                    actual.reps < maxOf(p.originalReps, p.plannedReps) ||
                        actual.weightKg + EPS < maxOf(p.originalKg, p.plannedKg) ||
                        actual.seconds < maxOf(p.originalSeconds, p.plannedSeconds)
                } != false
        }) return null

        // The next B occurrence may have a different SET count; preserve that
        // structure. A change of REP scheme needs a purpose-aware intensity model.
        if (targetOriginal.any { !it.originalExists || it.originalReps != reps } ||
            planned.any { it.reps != reps || it.confirmed } ||
            targetOriginal.mapNotNull { it.plannedSetIndex }.distinct().size != planned.size
        ) return null
        val maxRpe = done.maxOf { it.rpe ?: source.entry.rpe!! }
        val step = BBlockPeriodization.barbellIncrement(plateOneSideKg)
        val modestStep = step.isFinite() && step > 0.0 && step / load <= 0.02
        // Very easy, completed work can justify only one small step; otherwise
        // retain the last actually performed weight instead of leaving kg blank.
        val increase = maxRpe <= 7.0 && modestStep &&
            planned.size <= done.size
        val kg = if (increase) load + step else load
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
            requiredE1rmKg = null, historicalE1rmLogGap = null,
            worstCaseRegret = null, weightedCost = null,
            targetRirMixtureMass = null,
            volumeLoadChange = (kg * reps * planned.size - load * reps * done.size),
            eligible = true, reason = if (increase)
                "OBSERVED_UNIFORM_COMPLETION_RPE_AT_MOST_7_STEP_AT_MOST_2_PERCENT"
            else "OBSERVED_UNIFORM_COMPLETION_HOLD_NO_CANONICAL_POSTERIOR"
        )
        return ProposalEvidence(
            anchor = anchor,
            ranking = BCoachSquatCurveProgressionReport(
                status = "OBSERVATION_ONLY_NO_POSTERIOR",
                asOf = asOf, referenceDate = day,
                selectedAction = action, historicalE1rmKg = null,
                targetRir = 2.0, doseTargetSets = planned.size,
                ranked = listOf(candidate),
                notes = listOf(
                    "연결된 직전 세션에서 실제 확인한 세트만 사용했습니다.",
                    "모델이 준비되지 않아 미래 수행 확률이나 e1RM을 계산하지 않았습니다."
                )
            )
        )
    }
}
