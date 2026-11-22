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

/** Re-evaluate the linked prescription and its evidence inside the write transaction. */
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
        val personal = if (key == BCoachLinkedSquatWeekPlanner.SQUAT)
            evidenceReader.read(asOf) as? BCoachSquatEvidenceResult.Ready else null
        val curve = personal?.evidence?.curve ?: curves.resolve(key)
        val records = workoutDao.allEntriesWithSets()
        val links = dao.links()
        val prescriptions = dao.prescriptions()
        val integrated = metadata.optBoolean("bCoachIntegratedLifts")
        val evidenceForPlan = if (integrated) null else BCoachObservedSquatProgression.evaluate(
            targetEntryId = targetEntryId, asOf = asOf,
            links = links, records = records, prescriptions = prescriptions,
            plateOneSideKg = plate, displayedStepKg = step,
            curve = curve, rirPolicy = rirPolicy
        )
        val report = BCoachLinkedSquatWeekPlanner.inspect(
            asOf = asOf,
            targetEntryId = targetEntryId,
            workouts = records,
            applications = dao.applications(),
            links = links,
            prescriptions = prescriptions,
            anchor = evidenceForPlan?.anchor,
            ranking = evidenceForPlan?.ranking,
            resolveDoses = if (integrated) { source, target ->
                BCoachConfirmedLiftModel.nextDoses(source, target, curve, rirPolicy, step)
            } else null
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
        val originalKg = sets.first { it.setIndex in proposal.affectedSetIndices || proposal.affectedSetIndices.isEmpty() }.weightKg
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
