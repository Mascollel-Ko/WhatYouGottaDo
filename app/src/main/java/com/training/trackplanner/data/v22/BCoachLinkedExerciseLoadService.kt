package com.training.trackplanner.data.v22

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceLoadResolver
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.LoadResolutionConfidence
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.BCoachUncalibratedLinkPolicy
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionDirection
import com.training.trackplanner.data.ProgressionLinkMode
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionResolution
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionSuggestion
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import kotlin.math.abs
import org.json.JSONObject

/**
 * After-confirm B program progression for linked exercises OTHER than squat.
 *
 * - Default (not explicitly MAIN, not user-linked): copy the first fully performed
 *   working weight to ALL later unstarted occurrences of exactly the same track,
 *   regardless of their week-specific rep/set structure. No auto-increment.
 * - Explicit MAIN or explicitly user-connected noncanonical strength: run the
 *   canonical exercise-assigned e1RM/RIR common calculator on the immediate next
 *   linked occurrence only. The four canonical strength targets retain the
 *   appropriate direct target semantics; bodyweight-plus-added requires bodyweight.
 *
 * Does not create links, does not write generated templates, and does not alter
 * recorded actuals or a user's manually changed/started plan. Each execution is
 * atomically checked and idempotent. No same-name/next-date fallback.
 */
internal class BCoachLinkedExerciseLoadService(
    private val db: TrainingDatabase,
    private val curves: RepetitionCurveRegistry,
    private val rirPolicy: RpeRirPolicy
) {
    private val dao = db.programProgressionDao()
    private val workoutDao = db.workoutDao()

    private val canonicalFour = setOf(
        "barbell_back_squat", "barbell_deadlift",
        "barbell_bench_press", "ex_e41f4c2b"
    )

    suspend fun afterConfirmation(
        sourceEntryId: Long, asOf: LocalDate = LocalDate.now()
    ): BCoachPostConfirmOutcome = db.withTransaction {
        fun result(status: String, target: Long? = null, kg: Double? = null,
                   reps: Int? = null, count: Int? = null, action: String? = null,
                   detail: String? = null) =
            BCoachPostConfirmOutcome(sourceEntryId, status, target, kg, reps, count, action, detail)
        val sourceLink = dao.link(sourceEntryId) ?: return@withTransaction result("SOURCE_NOT_PROGRAM_LINKED")
        if (sourceLink.mode != ProgressionMode.APP || sourceLink.trackId == "untracked") {
            return@withTransaction result("LINK_MODE_NOT_ELIGIBLE")
        }
        val program = db.programDao().findProgramByStableKey(sourceLink.sourceProgramStableKey)
            ?: return@withTransaction result("PROGRAM_TEMPLATE_MISSING")
        if (!BCoachUncalibratedLinkPolicy.isBProgram(program.planningMetadataJson)) {
            return@withTransaction result("NOT_B_PROGRAM")
        }
        val applications = dao.applications()
        val active = applications.firstOrNull { it.id == sourceLink.applicationId }
            ?: return@withTransaction result("APPLICATION_NOT_ACTIVE")
        if (runCatching { LocalDate.parse(active.startDate) <= asOf }.getOrDefault(false).not() ||
            applications.any { it.programStableKey == active.programStableKey &&
                it.id != active.id && it.appliedAt > active.appliedAt &&
                runCatching { LocalDate.parse(it.startDate) <= asOf }.getOrDefault(false) }
        ) return@withTransaction result("SUPERSEDED_APPLICATION")
        val entries = workoutDao.allEntriesWithSets().associateBy { it.entry.id }
        val source = entries[sourceEntryId] ?: return@withTransaction result("SOURCE_MISSING")
        val key = source.entry.exerciseStableKey
        if (key == BCoachLinkedSquatWeekPlanner.SQUAT) return@withTransaction result("SQUAT_OWN_SERVICE")
        val sourceDate = runCatching { LocalDate.parse(source.entry.date) }.getOrNull()
            ?: return@withTransaction result("SOURCE_DATE_INVALID")
        if (sourceDate > asOf) return@withTransaction result("SOURCE_IN_FUTURE")
        val actuals = source.sets.sortedBy { it.setIndex }
        val weightedPullup = key == "ex_e41f4c2b"
        if (actuals.isEmpty() || actuals.any {
            !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() ||
                (if (weightedPullup) it.weightKg < 0.0 else it.weightKg <= 0.0) ||
                it.reps !in 1..20
        }) return@withTransaction result("WAITING_FOR_CONFIRMED_WEIGHT_SETS")
        val kg = actuals.first().weightKg
        val reps = actuals.first().reps
        if (actuals.any { abs(it.weightKg - kg) > 1e-8 || it.reps != reps }) {
            return@withTransaction result("SOURCE_NON_UNIFORM_LOAD_REVIEW")
        }
        val original = dao.prescriptions(sourceEntryId)
        if (original.size != actuals.size || original.any { p ->
            !p.originalExists || p.plannedSetIndex == null ||
                actuals.singleOrNull { it.setIndex == p.plannedSetIndex }?.let { set ->
                    set.reps < maxOf(p.originalReps, p.plannedReps) ||
                        set.weightKg + 1e-8 < maxOf(p.originalKg, p.plannedKg) ||
                        set.seconds < maxOf(p.originalSeconds, p.plannedSeconds)
                } != false
        }) return@withTransaction result("SOURCE_ORIGINAL_TARGET_NOT_COMPLETED")
        val chain = dao.links().filter { it.applicationId == sourceLink.applicationId &&
            it.trackId == sourceLink.trackId &&
            it.sourceProgramStableKey == sourceLink.sourceProgramStableKey &&
            it.mode == ProgressionMode.APP }
            .sortedBy { it.sequence }
        if (chain.size < 2 || chain.any { link ->
                entries[link.entryId]?.entry?.exerciseStableKey != key
            }) return@withTransaction result("NO_COMPARABLE_LINKED_CHAIN")
        val next = chain.filter { it.sequence > sourceLink.sequence }
        if (next.isEmpty()) return@withTransaction result("NO_NEXT_LINKED_EXERCISE")

        val member = dao.items().firstOrNull { it.logicalItemId == sourceLink.sourceItemId }
        val track = dao.tracks().firstOrNull { it.id == sourceLink.trackId }
        // An inferred single-member role of MAIN does not authorize load progression.
        val explicitlyMain = member?.signature?.plannerRole == ProgressionRole.MAIN ||
            track?.roleOverride == ProgressionRole.MAIN
        val manuallyConnected = member?.linkMode == ProgressionLinkMode.EXISTING
        val main = key in canonicalFour || explicitlyMain || manuallyConnected
        if (!main && chain.first().entryId != sourceEntryId) {
            return@withTransaction result("NONMAIN_FIRST_SESSION_ONLY")
        }
        val weightResolver = if (weightedPullup) StrengthPerformanceLoadResolver(
            db.dailyMetricDao().allMetrics(), db.dailyCheckInDao().all(),
            db.initialUserProfileDao().profile()
        ) else null
        val sourceTotalKg = if (weightedPullup) {
            val strengthLoad = checkNotNull(weightResolver).resolve(
                sourceDate, actuals.first(), StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD
            )
            if (!strengthLoad.isResolved ||
                strengthLoad.confidence !in setOf(
                    LoadResolutionConfidence.HIGH, LoadResolutionConfidence.MODERATE
                )
            ) return@withTransaction result("WEIGHTED_PULLUP_BODYWEIGHT_REQUIRED",
                detail = "당일 또는 최근 30일 이내의 실제 체중 기록이 필요합니다.")
            checkNotNull(strengthLoad.totalLoadKg)
        } else kg
        val metadata = runCatching { JSONObject(program.planningMetadataJson ?: "{}") }.getOrNull()
        val exercise = db.exerciseDao().findByStableKey(key)
        val instrument = BBlockEquipmentIncrements.kind(key, exercise)
        val step = when (instrument) {
            BEquipmentIncrementKind.BARBELL ->
                metadata?.optDouble("bCoachPlateOneSideKg", Double.NaN)?.takeIf {
                    it.isFinite() && it in 0.1..25.0
                }?.times(2.0)
            BEquipmentIncrementKind.DUMBBELL ->
                metadata?.optDouble("bCoachDumbbellIncrementKg", Double.NaN)?.takeIf {
                    it.isFinite() && it > 0.0
                }
            BEquipmentIncrementKind.MACHINE ->
                metadata?.optDouble("bCoachMachineIncrementKg", Double.NaN)?.takeIf {
                    it.isFinite() && it > 0.0
                }
            BEquipmentIncrementKind.UNKNOWN -> null
        }

        val userOverrideStep = sourceLink.rule.incrementKg?.takeIf {
            it.isFinite() && it > 0.0
        }
        val verifiedStep = if (weightedPullup) userOverrideStep else userOverrideStep ?: step

        val destinations = if (main) next.take(1) else next
        var updated = 0
        var firstApplied: BCoachPostConfirmOutcome? = null
        var firstProblem: String? = null
        val priorDecisions = dao.suggestions()
        for (destination in destinations) {
            val target = entries[destination.entryId]
            val targetDate = target?.entry?.date?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
            if (target == null || targetDate == null || targetDate < asOf ||
                targetDate < sourceDate || destination.sequence <= sourceLink.sequence ||
                target.entry.completedAt != null || target.entry.firstConfirmedAt != null ||
                target.sets.isEmpty() || target.sets.any { it.confirmed }
            ) {
                if (firstProblem == null) firstProblem = "TARGET_ALREADY_STARTED_OR_PAST"
                continue
            }
            if (priorDecisions.any { it.targetEntryId == destination.entryId &&
                it.resolution in setOf(ProgressionResolution.ACCEPTED,
                    ProgressionResolution.KEPT_CURRENT_PLAN,
                    ProgressionResolution.MANUAL_OVERRIDE)
            }) {
                if (firstProblem == null) firstProblem = "PRIOR_EXPLICIT_PROGRESSION_DECISION"
                continue
            }
            val setTargets = dao.prescriptions(destination.entryId)
            val targetSets = target.sets.sortedBy { it.setIndex }
             if (!unmodifiedTarget(destination, target, targetSets, setTargets)) {
                if (firstProblem == null) firstProblem = "USER_EDIT_OR_STRUCTURE_PROTECTED"
                continue
            }
            val targetReps = targetSets.map { it.reps }.distinct().singleOrNull()
            val targetBodyweight = if (weightedPullup && main) {
                val bw = checkNotNull(weightResolver).resolveBodyWeight(targetDate)
                if (bw == null || bw.confidence !in setOf(
                        LoadResolutionConfidence.HIGH, LoadResolutionConfidence.MODERATE
                    )
                ) {
                    if (firstProblem == null) firstProblem = "WEIGHTED_PULLUP_BODYWEIGHT_REQUIRED"
                    continue
                }
                bw.kg
            } else null
            val candidate = if (!main) null else {
                val rpe = actuals.mapNotNull { it.rpe ?: source.entry.rpe }.maxOrNull()
                if (rpe == null || targetReps == null) {
                    if (firstProblem == null) firstProblem = "MAIN_RPE_OR_REPS_REQUIRED"
                    continue
                }
                val choice = BCoachCommonStrengthRirCalculator.choose(
                    performedKg = sourceTotalKg, performedReps = reps,
                    reportedMaxRpe = rpe, nextReps = targetReps,
                    incrementKg = verifiedStep, curve = curves.resolve(key), rirPolicy = rirPolicy
                )
                if (choice == null || choice.action == "REP_SCHEME_REVIEW") {
                    if (firstProblem == null) firstProblem = "MAIN_RIR_REVIEW_REQUIRED"
                    continue
                }
                choice
            }
            val selectedKg = if (candidate != null && targetBodyweight != null) {
                (candidate.loadKg - targetBodyweight).takeIf { it.isFinite() && it >= 0.0 }
                    ?: run {
                        if (firstProblem == null) firstProblem = "WEIGHTED_PULLUP_ADDED_LOAD_REVIEW"
                        continue
                    }
            } else candidate?.loadKg ?: kg
            val action = candidate?.action ?: "FIRST_WEEK_LOAD_HOLD"
            // A nonmain's reps and number of sets are never overwritten.
            // We only calibrate the planned kg of the existing set structure.
            targetSets.forEach { set ->
                workoutDao.updateSet(set.copy(weightKg = selectedKg,
                    loadState = ProgramLoadState.EXPLICIT_LOAD,
                    manualWeight = false))
            }
            setTargets.forEach { planned ->
                dao.putPrescription(planned.copy(plannedKg = selectedKg,
                    plannedLoadState = ProgramLoadState.EXPLICIT_LOAD))
            }
            priorDecisions.filter { it.targetEntryId == destination.entryId &&
                it.resolution == ProgressionResolution.PENDING
            }.forEach { previous ->
                dao.putSuggestion(previous.copy(resolution = ProgressionResolution.STALE))
            }
            dao.putSuggestion(ProgressionSuggestion(
                applicationId = destination.applicationId, trackId = destination.trackId,
                sourceEntryId = sourceEntryId, targetEntryId = destination.entryId,
                previousActualKg = kg, currentPlanKg = targetSets.first().weightKg,
                suggestedKg = selectedKg, judgmentRpe = actuals.mapNotNull { it.rpe }.maxOrNull(),
                direction = if (selectedKg > kg + 1e-8) ProgressionDirection.INCREASE
                    else ProgressionDirection.HOLD,
                reasons = "B_COACH_GATE_M_AUTO_AFTER_CONFIRM_${action}",
                evidenceHash = "linked-m:${sourceEntryId}:${destination.entryId}:${selectedKg}:${targetReps}",
                resolution = ProgressionResolution.ACCEPTED,
                resolvedAt = System.currentTimeMillis(), resolvedKg = selectedKg,
                rule = destination.rule
            ))
            updated++
            if (firstApplied == null) firstApplied = result(
                "NEXT_LINKED_UPDATED", destination.entryId, selectedKg,
                targetReps ?: reps, targetSets.size, action
            )
        }
        if (updated == 0) return@withTransaction result(firstProblem ?: "NO_UNMODIFIED_FUTURE_TARGET")
        val first = checkNotNull(firstApplied)
        first.copy(detail = if (!main) "${updated}개 연결 운동에 첫 기록의 중량을 유지 적용했습니다."
            else "공통 e1RM–RIR 곡선으로 다음 연결 운동 중량을 검토했습니다.")
    }

    private fun unmodifiedTarget(
        link: ProgramWorkoutLink,
        target: WorkoutEntryWithSets,
        sets: List<WorkoutSet>,
        prescriptions: List<ProgramPrescriptionSet>
    ): Boolean {
        if (link.mode != ProgressionMode.APP || target.entry.id != link.entryId ||
            sets.isEmpty() || sets.size != prescriptions.size ||
            sets.any { it.confirmed }) return false
        val mapped = prescriptions.associateBy { it.plannedSetIndex }
        if (mapped.size != sets.size || null in mapped) return false
        return sets.all { set ->
            val plan = mapped[set.setIndex] ?: return@all false
            plan.entryId == link.entryId && plan.originalExists &&
                plan.plannedSetIndex == set.setIndex &&
                plan.originalReps == plan.plannedReps &&
                abs(plan.originalKg - plan.plannedKg) < 1e-8 &&
                plan.originalSeconds == plan.plannedSeconds &&
                plan.originalLoadState == plan.plannedLoadState &&
                set.reps == plan.plannedReps &&
                abs(set.weightKg - plan.plannedKg) < 1e-8 &&
                set.seconds == plan.plannedSeconds &&
                set.loadState == plan.plannedLoadState
        }
    }
}
