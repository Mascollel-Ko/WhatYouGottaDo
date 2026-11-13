package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionSession
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.abs

/**
 * Gate G: a proposal for ONE linked, unconfirmed, future B-Coach squat workout.
 *
 * The source is not an arbitrary same-day squat workout: it must belong to the same application,
 * the same progression track and the immediately preceding week. The anchor set IDs from Gate F
 * must be exactly the confirmed source IDs. This is user-approved, not silent plan mutation.
 */
data class BCoachLinkedSquatNextWeekProposal(
    val status: String,
    val sourceEntryId: Long? = null,
    val targetEntryId: Long? = null,
    val sourceWeek: Int? = null,
    val targetWeek: Int? = null,
    val sourceText: String? = null,
    val originalPlanText: String? = null,
    val proposedLoadKg: Double? = null,
    val proposedReps: Int? = null,
    val proposedSets: Int? = null,
    val selectedAction: String? = null,
    val evidenceFingerprint: String? = null,
    val notes: List<String> = emptyList()
) {
    val canApply: Boolean get() = status == "READY_FOR_USER_APPROVAL" &&
        evidenceFingerprint != null && proposedLoadKg != null &&
        proposedReps != null && proposedSets != null
}

/** Pure function. No writes, and no fabricated source/target from unmatched dates. */
internal object BCoachLinkedSquatWeekPlanner {
    const val SQUAT = "barbell_back_squat"
    private const val EPS = 1e-8

    fun inspect(
        asOf: LocalDate,
        targetEntryId: Long,
        workouts: List<WorkoutEntryWithSets>,
        applications: List<ProgramApplication>,
        links: List<ProgramWorkoutLink>,
        prescriptions: List<ProgramPrescriptionSet>,
        anchor: BCoachSquatAnchorSession?,
        ranking: BCoachSquatCurveProgressionReport?
    ): BCoachLinkedSquatNextWeekProposal {
        val byEntry = workouts.associateBy { it.entry.id }
        val target = byEntry[targetEntryId] ?: return BCoachLinkedSquatNextWeekProposal("TARGET_MISSING")
        val targetLink = links.firstOrNull { it.entryId == targetEntryId }
            ?: return BCoachLinkedSquatNextWeekProposal("TARGET_NOT_PROGRAM_LINKED")
        fun result(status: String, sourceEntryId: Long? = null, notes: List<String> = emptyList()) =
            BCoachLinkedSquatNextWeekProposal(
                status = status, sourceEntryId = sourceEntryId,
                targetEntryId = targetEntryId, targetWeek = targetLink.weekNumber, notes = notes
            )
        if (target.entry.exerciseStableKey != SQUAT || targetLink.trackId == "untracked" ||
            targetLink.mode in setOf(ProgressionMode.OFF, ProgressionMode.DIRECT) ||
            (targetLink.needsReview && targetLink.basePolicy != ProgressionBase.REVIEW)
        ) return result("LINK_OR_MODE_NOT_ELIGIBLE")
        val application = applications.firstOrNull {
            it.id == targetLink.applicationId &&
                it.programStableKey == targetLink.sourceProgramStableKey &&
                runCatching { LocalDate.parse(it.startDate) <= asOf }.getOrDefault(false)
        } ?: return result("APPLICATION_NOT_ACTIVE")
        // An old application cannot override a newer application of the same program.
        if (applications.any { it.programStableKey == application.programStableKey &&
            it.id != application.id && it.appliedAt > application.appliedAt &&
            runCatching { LocalDate.parse(it.startDate) <= asOf }.getOrDefault(false)
        }) return result("SUPERSEDED_APPLICATION")
        val targetDate = runCatching { LocalDate.parse(target.entry.date) }.getOrNull()
            ?: return result("INVALID_TARGET_DATE")
        if (targetDate < asOf) return result("TARGET_IN_PAST")
        if (target.sets.isEmpty() || target.sets.any { it.confirmed } ||
            target.entry.completedAt != null || target.entry.firstConfirmedAt != null
        ) return result("TARGET_ALREADY_STARTED")

        val targets = prescriptions.filter { it.entryId == targetEntryId }
            .sortedBy(ProgramPrescriptionSet::setIndex)
        val sets = target.sets.sortedBy { it.setIndex }
        if (targets.isEmpty() || targets.size != sets.size ||
            targets.any { it.plannedSetIndex == null || !it.originalExists } ||
            targets.mapNotNull { it.plannedSetIndex }.distinct().size != sets.size ||
            targets.any { p ->
                val actual = sets.singleOrNull { it.setIndex == p.plannedSetIndex }
                actual == null || actual.reps != p.plannedReps ||
                    abs(actual.weightKg - p.plannedKg) > EPS ||
                    actual.seconds != p.plannedSeconds ||
                    actual.loadState != p.plannedLoadState
            }
        ) return result("TARGET_STRUCTURE_CHANGED")
        if (targets.any { p ->
                p.plannedReps != p.originalReps ||
                    abs(p.plannedKg - p.originalKg) > EPS ||
                    p.plannedSeconds != p.originalSeconds ||
                    p.plannedLoadState != p.originalLoadState
            }
        ) return result("USER_EDIT_PROTECTED")

        // Follow precisely the Plan editor's explicitly linked progression track.
        // A separate (even same-day or same-exercise) track cannot become evidence.
        // The nearest earlier sequence is authoritative regardless of week or weekday.
        val predecessor = BCoachProgramLinkChain.predecessor(targetLink, links)
        if (!predecessor.found) return result(predecessor.status)
        val sourceLink = checkNotNull(predecessor.link)
        val sourceRecord = byEntry[sourceLink.entryId]
            ?: return result("LINKED_PREDECESSOR_RECORD_MISSING")
        if (sourceRecord.entry.exerciseStableKey != SQUAT) {
            return result("TRACK_EXERCISE_MISMATCH")
        }
        val sourceDate = runCatching { LocalDate.parse(sourceRecord.entry.date) }.getOrNull()
            ?: return result("INVALID_SOURCE_DATE")
        if (sourceDate > asOf || sourceDate > targetDate) {
            return result("LINKED_PREDECESSOR_NOT_CHRONOLOGICAL")
        }
        val sourceSets = sourceRecord.sets.sortedBy { it.setIndex }
        val sourceTargets = prescriptions.filter { it.entryId == sourceLink.entryId }
        val session = ProgressionSession(sourceLink, sourceSets, sourceTargets)
        // Set edits made immediately before confirmation can change plannedReps/plannedKg.
        // They cannot turn a failed original B prescription into a synthetic success.
        // A deliberately lowered manual plan should require a separate explicit decision.
        val originalProgramTargetsMet = sourceTargets.isNotEmpty() &&
            sourceTargets.all { p ->
                p.originalExists && p.plannedSetIndex != null &&
                    sourceSets.singleOrNull { it.setIndex == p.plannedSetIndex }?.let { done ->
                        done.reps >= maxOf(p.originalReps, p.plannedReps) &&
                            done.weightKg >= maxOf(p.originalKg, p.plannedKg) &&
                            done.seconds >= maxOf(p.originalSeconds, p.plannedSeconds)
                    } == true
            }
        if (!session.targetCompleted || !originalProgramTargetsMet ||
            sourceSets.size != sourceTargets.size ||
            sourceSets.any { !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() || it.weightKg <= 0.0 || it.reps !in 1..20 ||
                (it.rpe ?: sourceRecord.entry.rpe)?.let { r -> !r.isFinite() || r !in 6.0..10.0 } != false
            }
        ) return result("PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL", sourceLink.entryId)
        val actualIds = sourceSets.map { it.id }.sorted()
        if (anchor?.date != sourceDate ||
            anchor.sessionStableKey != sourceRecord.entry.sessionStableKey ||
            anchor.confirmedSets.map { it.setId }.sorted() != actualIds
        ) return result("LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE", sourceLink.entryId)

        val candidate = ranking?.ranked?.firstOrNull { it.action == ranking.selectedAction && it.eligible }
            ?: return result("RANKING_NOT_AVAILABLE", sourceLink.entryId)
        // Connecting two program items does not authorize flattening deliberately
        // different heavy/volume repetition schemes. A different target pattern is
        // acceptable only if the ranked candidate already exactly matches it.
        val performedReps = sourceSets.map { it.reps }.distinct().singleOrNull()
        val targetReps = targets.map { it.originalReps }.distinct().singleOrNull()
        if (performedReps == null || targetReps == null ||
            (targetReps != performedReps && targetReps != candidate.reps)
        ) return result("LINKED_TARGET_REP_SCHEME_REVIEW", sourceLink.entryId)
        if (candidate.loadKg <= 0.0 || candidate.reps <= 0 || candidate.sets <= 0 ||
            !candidate.loadKg.isFinite()
        ) return result("INVALID_CANDIDATE", sourceLink.entryId)
        if (candidate.sets != sets.size) return result(
            "SET_COUNT_REQUIRES_REVIEW", sourceLink.entryId,
            listOf("The experimental choice would change the number of future sets; no automatic removal/addition in Gate G.")
        )
        val fingerprint = fingerprint(
            asOf, application, sourceLink, sourceRecord, sourceTargets,
            targetLink, target, targets, ranking, candidate.action
        )
        return BCoachLinkedSquatNextWeekProposal(
            status = "READY_FOR_USER_APPROVAL",
            sourceEntryId = sourceLink.entryId,
            targetEntryId = targetEntryId,
            sourceWeek = sourceLink.weekNumber,
            targetWeek = targetLink.weekNumber,
            sourceText = sourceSets.joinToString(" / ") {
                "${it.weightKg}kg×${it.reps}회(RPE ${it.rpe ?: sourceRecord.entry.rpe})"
            },
            originalPlanText = sets.joinToString(" / ") { "${it.weightKg}kg×${it.reps}회" },
            proposedLoadKg = candidate.loadKg,
            proposedReps = candidate.reps,
            proposedSets = candidate.sets,
            selectedAction = candidate.action,
            evidenceFingerprint = fingerprint,
            notes = listOf(
                "같은 프로그램에서 명시적으로 연결한 세션의 직전 운동 순서만 사용했습니다.",
                "현재 날짜와 실제 운동기록으로 계산한 Gate F 후보입니다. 다음 주 성공확률이 아닙니다.",
                "확인 시 선택한 다음 주 미완료 계획만 갱신하며 지난 기록·원본 템플릿은 유지합니다."
            )
        )
    }

    private fun fingerprint(
        asOf: LocalDate, application: ProgramApplication,
        sourceLink: ProgramWorkoutLink, source: WorkoutEntryWithSets,
        sourceTargets: List<ProgramPrescriptionSet>,
        targetLink: ProgramWorkoutLink, target: WorkoutEntryWithSets,
        targetTargets: List<ProgramPrescriptionSet>,
        ranking: BCoachSquatCurveProgressionReport,
        action: String
    ): String {
        val raw = listOf(asOf, application, sourceLink, source.entry,
            source.sets.sortedBy { it.id }, sourceTargets.sortedBy { it.setIndex },
            targetLink, target.entry, target.sets.sortedBy { it.id },
            targetTargets.sortedBy { it.setIndex }, ranking, action
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
