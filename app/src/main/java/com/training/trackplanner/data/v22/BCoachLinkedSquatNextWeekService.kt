package com.training.trackplanner.data.v22

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
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
    private val rirPolicy: RpeRirPolicy
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
        val plate = metadata.optDouble("bCoachPlateOneSideKg", Double.NaN)
        if (!plate.isFinite() || plate !in 0.1..25.0) {
            return BCoachLinkedSquatNextWeekProposal("INVALID_EQUIPMENT_SNAPSHOT")
        }
        val evidence = evidenceReader.read(asOf)
        val pipeline = BCoachSquatShadowPipeline.evaluate(
            input = evidence,
            rirPolicy = rirPolicy,
            smallestPlateOneSideKg = plate,
            targetRirMin = 1.0,
            targetRirMax = 3.0,
            policies = listOf(
                BCoachSquatShadowSelector.Weights(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
            ),
            omegaBands = listOf(BCoachSquatShadowSelector.OmegaBand.CENTRAL_80)
        )
        if (pipeline !is BCoachSquatShadowPipelineResult.ShadowReport) {
            return BCoachLinkedSquatNextWeekProposal(
                "CANONICAL_MODEL_NOT_READY",
                targetEntryId = targetEntryId
            )
        }
        val targetSetCount = dao.prescriptions(targetEntryId).count { it.plannedSetIndex != null }
        val ranking = BCoachSquatCurveProgressionRanker.evaluate(
            input = pipeline, targetRirMin = 1.0, targetRirMax = 3.0,
            doseTargetSets = targetSetCount.takeIf { it > 0 },
            userConfirmedEquipmentStep = true
        )
        val report = BCoachLinkedSquatWeekPlanner.inspect(
            asOf = asOf,
            targetEntryId = targetEntryId,
            workouts = workoutDao.allEntriesWithSets(),
            applications = dao.applications(),
            links = dao.links(),
            prescriptions = dao.prescriptions(),
            anchor = pipeline.input.latestAnchor,
            ranking = ranking
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
        asOf: LocalDate = LocalDate.now()
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
        val proposedKg = proposal.proposedLoadKg ?: return@withTransaction false
        val proposedReps = proposal.proposedReps ?: return@withTransaction false
        val originalKg = sets.first().weightKg
        for (set in sets) {
            val prescribed = targets.singleOrNull { it.plannedSetIndex == set.setIndex }
                ?: return@withTransaction false
            workoutDao.updateSet(set.copy(
                reps = proposedReps,
                weightKg = proposedKg,
                loadState = ProgramLoadState.EXPLICIT_LOAD,
                manualWeight = false
            ))
            dao.putPrescription(prescribed.copy(
                plannedReps = proposedReps,
                plannedKg = proposedKg,
                plannedLoadState = ProgramLoadState.EXPLICIT_LOAD
            ))
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
            reasons = "B_COACH_GATE_G_USER_APPROVED_${proposal.selectedAction}_REPS_${proposedReps}_SETS_${proposal.proposedSets}",
            evidenceHash = expectedFingerprint,
            resolution = ProgressionResolution.ACCEPTED,
            resolvedAt = System.currentTimeMillis(),
            resolvedKg = proposedKg,
            rule = targetLink.rule
        ))
        true
    }
}
