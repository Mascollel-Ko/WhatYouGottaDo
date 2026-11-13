package com.training.trackplanner.data.v22

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.ProgressionDirection
import com.training.trackplanner.data.ProgressionResolution
import com.training.trackplanner.data.ProgressionSuggestion
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.TrainingDatabase
import java.time.LocalDate
import org.json.JSONObject

/**
 * Gate G opt-in bridge for ONE future applied squat entry.
 * Re-evaluates inside the write transaction against the same evidence fingerprint.
 *
 * No user workout has to be auto-updated when the user opens a screen. Explicit approval
 * is required because the Gate F loss coefficients and later-set fatigue are not validated.
 */
internal class BCoachLinkedSquatNextWeekService(
    private val db: TrainingDatabase,
    private val evidenceReader: BCoachSquatShadowEvidenceReader,
    private val rirPolicy: RpeRirPolicy,
    private val curves: RepetitionCurveRegistry,
    private val strengthRegistry: StrengthPerformanceRegistry? = null
) {
    private val workoutDao = db.workoutDao()
    private val dao = db.programProgressionDao()

    suspend fun inspect(
        targetEntryId: Long,
        asOf: LocalDate = LocalDate.now()
    ): BCoachLinkedSquatNextWeekProposal {
        val link = dao.link(targetEntryId)
            ?: return BCoachLinkedSquatNextWeekProposal("TARGET_NOT_PROGRAM_LINKED")
        val targetKey = workoutDao.findEntryById(targetEntryId)?.exerciseStableKey
            ?: return BCoachLinkedSquatNextWeekProposal("TARGET_MISSING")
        val program = db.programDao().findProgramByStableKey(link.sourceProgramStableKey)
            ?: return BCoachLinkedSquatNextWeekProposal("PROGRAM_TEMPLATE_MISSING")
        if (!BCoachMainPolicy.canChangeLoad(link.role, targetKey, program.planningMetadataJson)) {
            return BCoachLinkedSquatNextWeekProposal("ACCESSORY_HOLD_NO_E1RM")
        }
        val nonCanonical = targetKey !in BCoachMainPolicy.canonicalFour
        val exercise = db.exerciseDao().findByStableKey(targetKey)
        val reviewedCurve = curves.assignment(targetKey)?.reviewedStatus == "REVIEWED"
        if (nonCanonical && exercise?.estimated1RmEligible != true && !reviewedCurve) {
            return BCoachLinkedSquatNextWeekProposal("E1RM_NOT_ELIGIBLE_FOR_EXERCISE")
        }
        val unsupportedBodyweight = strengthRegistry?.proxyLoadings(targetKey).orEmpty().any {
            it.loadSemantics in setOf(StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD,
                StrengthLoadSemantics.BODYWEIGHT_MINUS_ASSISTANCE,
                StrengthLoadSemantics.BODYWEIGHT_FRACTION_PLUS_ADDED_LOAD)
        }
        if (nonCanonical && unsupportedBodyweight) {
            return BCoachLinkedSquatNextWeekProposal("BODYWEIGHT_VARIATION_REQUIRES_LOAD_MODEL")
        }
        val metadata = runCatching { JSONObject(program.planningMetadataJson ?: "{}") }.getOrNull()
        if (metadata?.optString("bCoachProgressionEquipmentSchema") != "linked-g-1") {
            return BCoachLinkedSquatNextWeekProposal(
                "EQUIPMENT_SNAPSHOT_MISSING",
                targetEntryId = targetEntryId,
                notes = listOf("이전에 생성한 프로그램은 실제 원판 크기의 저장 정보가 없어 자동 변경하지 않습니다.")
            )
        }
        val plate = metadata.optDouble("bCoachPlateOneSideKg", Double.NaN)
        if (!plate.isFinite() || plate !in 0.1..25.0) {
            return BCoachLinkedSquatNextWeekProposal("INVALID_EQUIPMENT_SNAPSHOT")
        }
        val step = when {
            targetKey == BCoachMainPolicy.WEIGHTED_PULL_UP -> plate // added load, not bilateral plates
            else -> when (BBlockEquipmentIncrements.kind(targetKey,
                exercise)) {
                BEquipmentIncrementKind.BARBELL -> plate * 2.0
                BEquipmentIncrementKind.DUMBBELL -> metadata.optDouble("bCoachDumbbellDisplayedKg", Double.NaN)
                BEquipmentIncrementKind.MACHINE -> metadata.optDouble("bCoachMachineDisplayedKg", Double.NaN)
                BEquipmentIncrementKind.UNKNOWN -> Double.NaN
            }
        }
        if (!step.isFinite() || step <= 0.0) {
            return BCoachLinkedSquatNextWeekProposal("EQUIPMENT_INCREMENT_UNKNOWN")
        }
        // Weighted pull-up is measured in BW + external added kg. Reuse recorded
        // check-in/metric bodyweight; never treat added plates as total load.
        suspend fun weightAtOrBefore(date: LocalDate): Double? {
            val start = date.minusDays(7).toString()
            val end = date.toString()
            val checkIns = db.dailyCheckInDao().between(start, end)
                .mapNotNull { row -> row.bodyWeightKg?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { row.date to it } }
            val metrics = db.dailyMetricDao().metricsUntil(end)
                .filter { it.date >= start }
                .mapNotNull { row -> row.bodyWeightKg?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { row.date to it } }
            return (checkIns + metrics).maxByOrNull { it.first }?.second
        }
        // The squat-only shadow pipeline is diagnostic; other main lifts use
        // their own reviewed/general curve assignment, never squat's posterior.
        val pipeline = if (targetKey == BCoachLinkedSquatWeekPlanner.SQUAT) {
            BCoachSquatShadowPipeline.evaluate(
                input = evidenceReader.read(asOf),
                rirPolicy = rirPolicy,
                smallestPlateOneSideKg = plate,
                targetRirMin = 1.0,
                targetRirMax = 3.0,
                policies = listOf(
                    BCoachSquatShadowSelector.Weights(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                ),
                omegaBands = listOf(BCoachSquatShadowSelector.OmegaBand.CENTRAL_80)
            )
        } else null
        val records = workoutDao.allEntriesWithSets()
        val links = dao.links()
        val prescriptions = dao.prescriptions()
        val targetSetCount = prescriptions.count {
            it.entryId == targetEntryId && it.plannedSetIndex != null
        }
        val canonicalReport = pipeline as? BCoachSquatShadowPipelineResult.ShadowReport
        // Use the canonical personal curve when available. Even if the async
        // stored posterior has not caught up with the last confirmed workout,
        // the canonical asset q(n) and existing RPE/RIR mixture are still available
        // and can evaluate the actual same-track confirmed sets.
        val curve = if (targetKey == BCoachLinkedSquatWeekPlanner.SQUAT)
            canonicalReport?.input?.curve ?: curves.resolve(targetKey)
        else curves.resolve(targetKey)
        val sourceLink = links.singleOrNull { it.entryId == targetEntryId }
            ?.let { BCoachProgramLinkChain.predecessor(it, links).link }
        val sourceEntry = sourceLink?.let { prior -> records.singleOrNull { it.entry.id == prior.entryId } }
        val sourceWeight = if (targetKey == BCoachMainPolicy.WEIGHTED_PULL_UP)
            sourceEntry?.let { runCatching { LocalDate.parse(it.entry.date) }.getOrNull() }
                ?.let { weightAtOrBefore(it) } else null
        val targetWeight = if (targetKey == BCoachMainPolicy.WEIGHTED_PULL_UP)
            weightAtOrBefore(asOf) else null
        if (targetKey == BCoachMainPolicy.WEIGHTED_PULL_UP &&
            (sourceWeight == null || targetWeight == null)) {
            return BCoachLinkedSquatNextWeekProposal("BODYWEIGHT_REQUIRED")
        }
        val evidenceForPlan = BCoachObservedSquatProgression.evaluate(
            targetEntryId = targetEntryId, asOf = asOf,
            links = links, records = records, prescriptions = prescriptions,
            plateOneSideKg = plate,
            curve = curve, rirPolicy = rirPolicy,
            exerciseStableKey = targetKey, loadStepKg = step,
            sourceBodyWeightKg = sourceWeight, targetBodyWeightKg = targetWeight,
            maxSingleStepFraction = if (targetKey in BCoachMainPolicy.canonicalFour) 0.02 else 0.15
        )
        // The global Gate F ranker remains a separate diagnostic. This production
        // decision is intentionally anchored to the immediate linked completed
        // workout and requires a monotone improvement in projected RIR distance.
        val report = BCoachLinkedSquatWeekPlanner.inspect(
            asOf = asOf,
            targetEntryId = targetEntryId,
            workouts = records,
            applications = dao.applications(),
            links = links,
            prescriptions = prescriptions,
            anchor = evidenceForPlan?.anchor,
            ranking = evidenceForPlan?.ranking,
            exerciseStableKey = targetKey
        )
        if (report.canApply && dao.suggestions().any {
                it.targetEntryId == targetEntryId &&
                    it.resolution in setOf(
                        ProgressionResolution.ACCEPTED,
                        ProgressionResolution.KEPT_CURRENT_PLAN,
                        ProgressionResolution.MANUAL_OVERRIDE
                    )
            }) return report.copy(status = "PRIOR_EXPLICIT_PROGRESSION_DECISION", evidenceFingerprint = null)
        return report
    }

    /** User taps Apply. No stale previews or half-completed target entries can be written. */
    suspend fun approve(
        targetEntryId: Long,
        expectedFingerprint: String,
        asOf: LocalDate = LocalDate.now(),
        automaticallyAfterConfirm: Boolean = false
    ): Boolean = db.withTransaction {
        if (expectedFingerprint.isBlank()) return@withTransaction false
        val proposal = inspect(targetEntryId, asOf)
        if (!proposal.canApply || proposal.evidenceFingerprint != expectedFingerprint ||
            proposal.targetEntryId != targetEntryId
        ) return@withTransaction false
        val targetLink = dao.link(targetEntryId) ?: return@withTransaction false
        val target = workoutDao.findEntryById(targetEntryId) ?: return@withTransaction false
        if (LocalDate.parse(target.date) < asOf) return@withTransaction false
        val sets = workoutDao.setsForEntry(targetEntryId).sortedBy { it.setIndex }
        val targets = dao.prescriptions(targetEntryId)
        if (sets.size != proposal.proposedSets || sets.isEmpty() ||
            sets.any { it.confirmed } || targets.size != sets.size
        ) return@withTransaction false
        val patch = BCoachLinkedSquatPlanPatch.build(proposal, sets, targets)
            ?: return@withTransaction false
        val proposedKg = checkNotNull(proposal.proposedLoadKg)
        val proposedReps = checkNotNull(proposal.proposedReps)
        val originalKg = sets.first().weightKg
        // All validation above this point; nothing written on a failed/edited target.
        patch.nextSets.forEach { workoutDao.updateSet(it) }
        patch.nextPrescriptions.forEach { dao.putPrescription(it) }
        // A legacy progression suggestion may have been created before the B-Coach
        // decision. Mark it stale in the same transaction so it cannot conflict with
        // the newly applied Gate H prescription on the record screen.
        dao.suggestions().filter {
            it.targetEntryId == targetEntryId &&
                it.resolution == ProgressionResolution.PENDING
        }.forEach { previous ->
            dao.putSuggestion(previous.copy(resolution = ProgressionResolution.STALE))
        }
        val prior = workoutDao.setsForEntry(checkNotNull(proposal.sourceEntryId))
        val priorKg = prior.maxOfOrNull { it.weightKg }
        val priorRpe = prior.mapNotNull { it.rpe }.maxOrNull()
        dao.putSuggestion(ProgressionSuggestion(
            applicationId = targetLink.applicationId,
            trackId = targetLink.trackId,
            sourceEntryId = checkNotNull(proposal.sourceEntryId),
            targetEntryId = targetEntryId,
            previousActualKg = priorKg,
            currentPlanKg = originalKg,
            suggestedKg = proposedKg,
            judgmentRpe = priorRpe,
            direction = if (proposal.selectedAction == "HOLD") ProgressionDirection.HOLD
                else ProgressionDirection.INCREASE,
            reasons = "B_COACH_${if (automaticallyAfterConfirm) "GATE_H_AUTO_AFTER_CONFIRM" else "GATE_G_USER_APPROVED"}_${proposal.selectedAction}_REPS_${proposedReps}_SETS_${proposal.proposedSets}",
            evidenceHash = expectedFingerprint,
            resolution = ProgressionResolution.ACCEPTED,
            resolvedAt = System.currentTimeMillis(),
            resolvedKg = proposedKg,
            rule = targetLink.rule
        ))
        true
    }
}
