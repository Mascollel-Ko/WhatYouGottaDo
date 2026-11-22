package com.training.trackplanner.data.v22

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
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
    private val curves: RepetitionCurveRegistry
) {
    private val workoutDao = db.workoutDao()
    private val dao = db.programProgressionDao()

    suspend fun inspect(
        targetEntryId: Long,
        asOf: LocalDate = LocalDate.now()
    ): BCoachLinkedSquatNextWeekProposal {
        val link = dao.link(targetEntryId)
            ?: return BCoachLinkedSquatNextWeekProposal("TARGET_NOT_PROGRAM_LINKED")
        val program = db.programDao().findProgramByStableKey(link.sourceProgramStableKey)
            ?: return BCoachLinkedSquatNextWeekProposal("PROGRAM_TEMPLATE_MISSING")
        val metadata = runCatching { JSONObject(program.planningMetadataJson ?: "{}") }.getOrNull()
        if (metadata?.optString("bCoachProgressionEquipmentSchema") != "linked-g-1") {
            return BCoachLinkedSquatNextWeekProposal(
                "EQUIPMENT_SNAPSHOT_MISSING",
                targetEntryId = targetEntryId,
                notes = listOf("이전에 생성한 프로그램은 실제 원판 크기의 저장 정보가 없어 자동 변경하지 않습니다.")
            )
        }
        val key = workoutDao.findEntryById(targetEntryId)?.exerciseStableKey
            ?: return BCoachLinkedSquatNextWeekProposal("TARGET_MISSING")
        if (key !in BCoachConfirmedLiftModel.keys ||
            (key != BCoachLinkedSquatWeekPlanner.SQUAT && !metadata.optBoolean("bCoachIntegratedLifts")))
            return BCoachLinkedSquatNextWeekProposal("LINK_OR_MODE_NOT_ELIGIBLE")
        val plate = metadata.optDouble("bCoachPlateOneSideKg", Double.NaN)
        val step = if (key == "ex_3a7d3eda") metadata.optDouble("bCoachDumbbellDisplayedIncrementKg", Double.NaN)
            else plate * 2.0
        if (!step.isFinite() || step <= 0.0 || (key != "ex_3a7d3eda" && plate !in 0.1..25.0)) {
            return BCoachLinkedSquatNextWeekProposal("INVALID_EQUIPMENT_SNAPSHOT")
        }
        val pipeline = if (key != BCoachLinkedSquatWeekPlanner.SQUAT) null else BCoachSquatShadowPipeline.evaluate(
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
        val records = workoutDao.allEntriesWithSets()
        val links = dao.links()
        val prescriptions = dao.prescriptions()
        val canonicalReport = pipeline as? BCoachSquatShadowPipelineResult.ShadowReport
        // Use the canonical personal curve when available. Even if the async
        // stored posterior has not caught up with the last confirmed workout,
        // the canonical asset q(n) and existing RPE/RIR mixture are still available
        // and can evaluate the actual same-track confirmed sets.
        val curve = canonicalReport?.input?.curve
            ?: curves.resolve(key)
        val evidenceForPlan = BCoachObservedSquatProgression.evaluate(
            targetEntryId = targetEntryId, asOf = asOf,
            links = links, records = records, prescriptions = prescriptions,
            plateOneSideKg = plate, displayedStepKg = step,
            curve = curve, rirPolicy = rirPolicy
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
            ranking = evidenceForPlan?.ranking
        )
        if (report.canApply && dao.suggestions().any {
                it.targetEntryId == targetEntryId &&
                    it.resolution in setOf(
                        ProgressionResolution.ACCEPTED,
                        ProgressionResolution.KEPT_CURRENT_PLAN,
                        ProgressionResolution.MANUAL_OVERRIDE
                    )
            }) return report.copy(status = "PRIOR_EXPLICIT_PROGRESSION_DECISION", evidenceFingerprint = null)
        if (!report.canApply || !metadata.optBoolean("bCoachIntegratedLifts")) return report
        val source = records.single { it.entry.id == report.sourceEntryId }
        val target = records.single { it.entry.id == targetEntryId }
        val loads = BCoachConfirmedLiftModel.nextLoads(source, target, curve, rirPolicy, step)
            ?: return report.copy(status = "SET_SCHEME_REVIEW", evidenceFingerprint = null)
        val changed = target.sets.filter { it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
            kotlin.math.abs(it.weightKg - loads.getValue(it.setIndex)) > 1e-8 }
        if (changed.isEmpty()) return report.copy(status = "CURRENT_PLAN_FITS", evidenceFingerprint = null)
        val first = changed.minBy { it.setIndex }
        val changedLoads = changed.associate { it.setIndex to loads.getValue(it.setIndex) }.toSortedMap()
        return report.copy(affectedSetIndices = changedLoads.keys.toList(), loadsBySetIndex = changedLoads,
            proposedLoadKg = changedLoads.getValue(first.setIndex), proposedReps = first.reps,
            selectedAction = "SET_SPECIFIC", evidenceFingerprint = "${report.evidenceFingerprint}|$changedLoads")
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
        // Write only explicitly approved set indices. A mixed-set Shadow first
        // set must never rewrite untouched follow-up set rows or their provenance.
        val affected = proposal.affectedSetIndices.ifEmpty { sets.map { it.setIndex } }.toSet()
        patch.nextSets.filter { it.setIndex in affected }
            .forEach { workoutDao.updateSet(it) }
        patch.nextPrescriptions.filter { it.plannedSetIndex in affected }
            .forEach { dao.putPrescription(it) }
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
            direction = when (proposal.selectedAction) {
                "LOAD_DOWN_ONE_STEP" -> ProgressionDirection.DECREASE
                "LOAD_UP_ONE_STEP" -> ProgressionDirection.INCREASE
                "SET_SPECIFIC" -> when {
                    proposedKg > originalKg -> ProgressionDirection.INCREASE
                    proposedKg < originalKg -> ProgressionDirection.DECREASE
                    else -> ProgressionDirection.HOLD
                }
                else -> ProgressionDirection.HOLD
            },
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
