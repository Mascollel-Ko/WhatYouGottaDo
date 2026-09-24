package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

data class PostSplitObjective(val balance: BalanceObjective, val maxMajorAnchors: Int, val majorCoLocations: Int,
    val strengthPrimary: StrengthPrimaryObjective = StrengthPrimaryObjective(0,0)): Comparable<PostSplitObjective> {
    override fun compareTo(other: PostSplitObjective) = compareValuesBy(this,other,
        PostSplitObjective::strengthPrimary,PostSplitObjective::balance,PostSplitObjective::maxMajorAnchors,PostSplitObjective::majorCoLocations)
    fun toJson() = JSONObject().put("balance",balance.toJson()).put("maxMajorAnchors",maxMajorAnchors).put("majorCoLocations",majorCoLocations)
        .put("strengthPrimaryOverlap",strengthPrimary.overlap).put("maxStrengthPrimaryPerDay",strengthPrimary.maximumPerDay)
}
data class PostSplitMove(val localId: String,val stableKey: String,val from: Int,val to: Int,
    val before: PostSplitObjective,val after: PostSplitObjective) {
    fun toJson() = JSONObject().put("localId",localId).put("stableKey",stableKey).put("from",from).put("to",to)
        .put("before",before.toJson()).put("after",after.toJson()).put("ofiGate","DESTINATION_PASS")
        .put("tissueGate","NO_BLOCKED_UNITS_NO_NEW_UNRESOLVED_MOVED_KEY_RESOLVED")
}
data class PostSplitReflowTrace(val state: String,val parentIds: List<String>,val fixedChunkIds: List<String>,
    val initialFingerprint: String,val finalFingerprint: String,val initialDays: List<BalanceDay> = emptyList(),val finalDays: List<BalanceDay> = emptyList(),
    val initialObjective: PostSplitObjective? = null,val finalObjective: PostSplitObjective? = null,
    val moves: List<PostSplitMove> = emptyList(),val rejections: Map<String,Int> = emptyMap(),val spacingJson: String = "[]",
    val qcrBeforeJson: String = "[]",val qcrAfterJson: String = "[]",val tissue: PlannedTissueWeek? = null,val diagnostic: String = "",
    val timeReference: Double = 0.0,val ofiReference: Double = 0.0) {
    fun toJson() = JSONObject().put("state",state).put("mandatoryParentIds",JSONArray(parentIds)).put("protectedSplitChunkLocalIds",JSONArray(fixedChunkIds))
        .put("initialFingerprint",initialFingerprint).put("finalFingerprint",finalFingerprint)
        .put("initialDays",JSONArray(initialDays.map { it.toJson() })).put("finalDays",JSONArray(finalDays.map { it.toJson() }))
        .put("initialObjective",initialObjective?.toJson()).put("finalObjective",finalObjective?.toJson())
        .put("moves",JSONArray(moves.map { it.toJson() })).put("rejections",JSONObject(rejections)).put("primaryAnchorSpacing",JSONArray(spacingJson))
        .put("qcrBefore",JSONArray(qcrBeforeJson)).put("qcrAfter",JSONArray(qcrAfterJson)).put("qcrUnchanged",qcrBeforeJson==qcrAfterJson)
        .put("chronologicalTissue",tissue?.toJson()).put("diagnostic",diagnostic).put("timeReference",timeReference).put("ofiReference",ofiReference)
        .put("ofiGate",if(moves.isEmpty()) "NO_ACCEPTED_MOVE" else "EVERY_DESTINATION_PASS; EXISTING_OTHER_DAY_WARNINGS_RETAINED")
}
internal data class PostSplitReflowResult(val skeleton: GeneratedProgramSkeleton,val trace: PostSplitReflowTrace)

/** Optional measurement sink; one fresh instance per invocation, never a ranking input. */
internal data class ReflowEvaluationCounts(var candidates: Int = 0, var dayProjections: Int = 0,
    var tissueProjections: Int = 0, var objectives: Int = 0, var acceptedActions: Int = 0,
    val performanceMetrics: PlannerPerformanceMetrics? = null)

/** Deterministic whole-item moves around fixed chunks. No prescription, demand, funding or progression mutation. */
internal class PostSplitWeeklyReflow {
    fun review(plan: GeneratedProgramSkeleton,snapshot: PlanningHistorySnapshot,state: AthletePlanningState,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        counts: ReflowEvaluationCounts = ReflowEvaluationCounts(),
        canonicalFailureEmitter: ((StimulusCanonicalEvaluationFailureReason, String?) -> Nothing)? = null): PostSplitReflowResult {
        val fingerprint=personalizedProgramFingerprint(plan.request,plan.items)
        val authority=plan.personalizedDecision?.authorizedScheduling
        val parents=authority?.authorized.orEmpty().filter { ContinuitySplitPolicy.mandatory(snapshot,it) }.map { it.id }
        val fixed=plan.items.filter { row -> authority?.localOrigins?.get(row.localId)?.let {
            it.authorizedDemandId in parents && it.splitGroupId.isNotBlank() } == true }.map { it.localId }
        val materializedParents=authority?.localOrigins.orEmpty().filterKeys { it in fixed }.values.map { it.authorizedDemandId }.distinct().sorted()
        fun unchanged(status: String,diagnostic: String="") = PostSplitReflowResult(plan,PostSplitReflowTrace(status,materializedParents,fixed,
            fingerprint,fingerprint,diagnostic=diagnostic))
        if(fixed.isEmpty()) return unchanged("NOT_APPLICABLE_NO_MANDATORY_SPLIT")
        progress.report(PersonalizedPlannerStage.POST_SPLIT_REFLOW)
        val execution = ReflowProgress(progress)
        return try {
            run(plan,snapshot,state,requireNotNull(authority),materializedParents,fixed,counts,execution,canonicalFailureEmitter)
                .also { execution.complete() }
        } catch(error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch(error: StimulusCanonicalEvaluationFailure) {
            throw error
        } catch(error: Exception) {
            // The legacy review path keeps its safe-unchanged behavior. The
            // production canonical path opts into a strict boundary so
            // unexpected programmer/data failures propagate unchanged.
            if (canonicalFailureEmitter != null) throw error
            unchanged("FAILED_SAFE_UNCHANGED",error.message ?: error.javaClass.simpleName)
        }
    }

    private fun run(plan: GeneratedProgramSkeleton,snapshot: PlanningHistorySnapshot,state: AthletePlanningState,
        authority: AuthorizedSchedulingTrace,parents: List<String>,fixed: List<String>,counts: ReflowEvaluationCounts,
        execution: ReflowProgress,
        canonicalFailureEmitter: ((StimulusCanonicalEvaluationFailureReason, String?) -> Nothing)?): PostSplitReflowResult {
        val projection=requireNotNull(snapshot.planDayProjection) { "MISSING_CANONICAL_OFI_PROJECTION" }
        val tissueProjection=requireNotNull(snapshot.planWeekTissueProjection) { "MISSING_CANONICAL_TISSUE_PROJECTION" }
        // RepresentativeWeek verifies these positional atoms have exactly isomorphic per-week immutable content.
        val atoms=plan.items.groupBy { it.weekNumber }.values.flatMap { rows -> rows.mapIndexed { i,row -> row.localId to "reflow_$i" } }.toMap()
        val week=requireNotNull(RepresentativeWeek.derive(plan,atoms)) { "NON_ISOMORPHIC_WEEK_OR_BINDING" }
        execution.prepared()
        val initial=week.items
        val sources=authority.authorized.associateBy { it.id }
        fun source(row: ProgramSkeletonItem)=authority.localOrigins[row.localId]?.let { sources[it.authorizedDemandId] }
        val primary=PrimaryStrengthAnchorSpacingPolicy.keys(snapshot,state,authority.authorized.filter { it.continuity }.mapTo(mutableSetOf()) { it.item.stableKey })
        require(PrimaryStrengthAnchorSpacingPolicy.allowedRows(initial,primary)) { "PRE_REFLOW_PRIMARY_ANCHOR_SPACING_VIOLATION" }
        val loads=mutableMapOf<List<ProgramSkeletonItem>,StandaloneDayLoad>()
        fun load(rows: List<ProgramSkeletonItem>)=loads.getOrPut(rows.map { it.copy(dayOfWeek=1,orderIndex=0) }) {
            counts.dayProjections++; counts.performanceMetrics?.let { it.dayProjectionCalls++ }; projection.evaluate(rows)
        }
        val referenceDays=week.days.filter { day -> initial.any { it.dayOfWeek==day } }
        execution.baselineValidation()
        val timeRef=planningMedian(referenceDays.map { day -> initial.filter { it.dayOfWeek==day }.sumOf(::plannedSeconds).toDouble() })
        val ofiRef=planningMedian(referenceDays.map { day -> load(initial.filter { it.dayOfWeek==day }).ofi.toDouble() })
        require(timeRef>0 && timeRef.isFinite() && ofiRef.isFinite())
        fun metrics(rows: List<ProgramSkeletonItem>)=week.days.map { day ->
            val items=rows.filter { it.dayOfWeek==day }; val seconds=items.sumOf(::plannedSeconds); val ofi=load(items).ofi
            BalanceDay(day,seconds,ofi,seconds/timeRef,if(ofiRef>0) ofi/ofiRef else null)
        }
        val objectives=mutableMapOf<List<ProgramSkeletonItem>,PostSplitObjective>()
        fun objective(rows: List<ProgramSkeletonItem>): PostSplitObjective = objectives.getOrPut(rows) {
            counts.objectives++
            val majorCounts=week.days.map { day -> rows.filter { it.dayOfWeek==day && it.exerciseStableKey in primary }.map { it.exerciseStableKey }.distinct().size }
            PostSplitObjective(balanceObjective(metrics(rows)),majorCounts.maxOrNull() ?: 0,majorCounts.sumOf { it*(it-1)/2 },
                StrengthPrimaryMainPolicy.objective(rows,week.days))
        }
        fun lower(row: ProgramSkeletonItem)=snapshot.metadata[row.exerciseStableKey]?.let { meta ->
            snapshot.movementCoverage(row.exerciseStableKey) in setOf(MovementCoverage.LOWER_KNEE,MovementCoverage.POSTERIOR_CHAIN,MovementCoverage.CALVES) ||
                meta.jointTendonImpactStressLevel in setOf("HIGH","VERY_HIGH") } ?: false
        fun maxLower(rows: List<ProgramSkeletonItem>)=week.days.maxOf { day -> rows.filter { it.dayOfWeek==day && lower(it) }.sumOf(::plannedSeconds) }
        fun restriction(row: ProgramSkeletonItem): String? {
            val owned=source(row) ?: return "MISSING_EXACT_SOURCE"
            if(row.localId in fixed) return "FIXED_SPLIT_CHUNK"
            if(owned.item.scheduleTier()==ScheduleTier.CORE_MUST_DO && !MainSchedulingPolicy.ordinaryMain(row,owned.item,plan) || row.requiredTemplateAnchor) return "CORE_OR_TEMPLATE"
            val binding = row.progressionBinding
            if (binding != null && (plan.progressionSessions.none { it.key == binding.sessionKey } ||
                binding.signature.variant.isNotBlank() || binding.signature.style !in setOf("", StrengthProgrammingStyle.NONE.name,
                    StrengthProgrammingStyle.STRAIGHT_5X5.name, StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS.name))) return "STRUCTURED_OR_MISSING_SESSION"
            // Whole uniform supportive bouts have no implicit weekday binding. Do not confuse
            // their performance-domain label with a structured, day-specific prescription.
            if(snapshot.activityKind(row.exerciseStableKey) in setOf(PlannedActivityKind.GENERIC_COURT_SESSION, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.OTHER) ||
                row.setPrescriptions.map { it.copy(setIndex = 0) }.distinct().size != 1 || row.progressionVariant.isNotBlank() ||
                row.progressionStyle !in setOf("", StrengthProgrammingStyle.NONE.name, StrengthProgrammingStyle.STRAIGHT_5X5.name, StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS.name) ||
                owned.item.styleVariant.isNotBlank() || owned.item.style !in setOf(StrengthProgrammingStyle.NONE,StrengthProgrammingStyle.STRAIGHT_5X5,StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS)) return "STRUCTURED_OR_ATOMIC"
            val equipment=snapshot.exercises[row.exerciseStableKey]?.equipment.orEmpty().split('|',',').map(String::trim).filter(String::isNotBlank)
            if(row.exerciseStableKey !in snapshot.exercises || row.exerciseStableKey in plan.request.excludedExerciseStableKeys || snapshot.explicitlyRestricted(row.exerciseStableKey) ||
                snapshot.metadata[row.exerciseStableKey]?.planningEligibility !in setOf("PROGRAM_SELECTABLE","SELECTABLE") ||
                plan.request.availableEquipment.isNotEmpty() && equipment.any { it!="BODYWEIGHT" && it !in plan.request.availableEquipment }) return "USER_EQUIPMENT_ELIGIBILITY"
            if(!postProcessTissueAllowed(snapshot,state,row.exerciseStableKey)) return "CURRENT_TISSUE"
            return null
        }
        val rpe=plan.weekPlans.firstOrNull { it.weekIndex==1 }?.targetRpeMax ?: 8.5
        val tissueCache=mutableMapOf<List<ProgramSkeletonItem>,PlannedTissueWeek>()
        fun tissue(rows: List<ProgramSkeletonItem>)=tissueCache.getOrPut(rows) {
            counts.tissueProjections++; counts.performanceMetrics?.let { it.weekTissueProjectionCalls++ }; tissueProjection.evaluate(rows,rpe)
        }
        val baselineTissue=tissue(initial)
        fun tissueAllowed(rows: List<ProgramSkeletonItem>,moved: String): Boolean {
            val result=tissue(rows)
            // Existing unrelated unresolved catalogue inputs remain explicitly diagnostic, never called a full PASS.
            return result.diagnostic=="CANONICAL_RCV_PROJECTION" && result.days.all { day -> day.blockedUnits.isEmpty() && moved !in day.unresolvedKeys &&
                day.unresolvedKeys.all { it in baselineTissue.days.firstOrNull { old -> old.day==day.day }?.unresolvedKeys.orEmpty() } }
        }
        val demand=AuthorizedPlanningDemand(snapshot,authority.authorized.map { AuthorizedPrescription(it.id,it.item,it.prescription,it.continuity) },
            plan.personalizedDecision!!.adaptationGaps,initial)
        fun qcr(rows: List<ProgramSkeletonItem>)=JSONArray(demand.residuals(rows).map { it.toJson() }).toString()
        fun immutable(rows: List<ProgramSkeletonItem>)=rows.map { it.copy(dayOfWeek=1,orderIndex=0) }
        val initialQcr=qcr(initial)
        var rows=initial
        val initialObjective=objective(initial)
        val moves=mutableListOf<PostSplitMove>(); val rejected=sortedMapOf<String,Int>()
        fun reject(reason: String) { rejected[reason]=(rejected[reason] ?: 0)+1 }
        val visited=mutableSetOf(rows.map { it.localId to it.dayOfWeek })
        data class Candidate(val rows: List<ProgramSkeletonItem>,val move: PostSplitMove,val priority: Int)
        val comparator=compareBy<Candidate> { it.move.after }.thenBy { it.priority }.thenBy { it.move.stableKey }
            .thenBy { it.move.from }.thenBy { it.move.to }.thenBy { it.move.localId }
        // Explicit bound; every accepted step strictly decreases the finite objective tuple.
        repeat(128) { round ->
            execution.roundStarted(round)
            val before=objective(rows)
            val lowerBefore=maxLower(rows)
            val candidates=mutableListOf<Candidate>()
            for((rowIndex,row) in rows.withIndex()) {
                restriction(row)?.let { reject(it) } ?: run {
                    for(day in week.days.filter { it!=row.dayOfWeek }) {
                        counts.candidates++
                        counts.performanceMetrics?.let { it.postSplitCandidates++ }
                        val trial=rows.map { if(it.localId==row.localId) it.copy(dayOfWeek=day,
                            orderIndex=(rows.filter { existing -> existing.dayOfWeek==day }.maxOfOrNull { existing -> existing.orderIndex } ?: 0)+1) else it }
                        val destination=trial.filter { it.dayOfWeek==day }
                        val reason=when {
                            destination.map { it.exerciseStableKey }.distinct().size!=destination.size -> "SAME_KEY"
                            destination.sumOf(::plannedSeconds)>plan.request.sessionMinutes*60 -> "SESSION_TIME"
                            !PrimaryStrengthAnchorSpacingPolicy.allowedRows(trial,primary) -> "PRIMARY_ANCHOR_CALENDAR_SPACING"
                            maxLower(trial)>lowerBefore -> "LOWER_IMPACT_CONCENTRATION"
                            !load(destination).feasible -> "DESTINATION_OFI"
                            objective(trial)>=before -> "NO_STRICT_IMPROVEMENT"
                            else -> null
                        }
                        if(reason!=null) { reject(reason); continue }
                        val candidate=Candidate(trial,PostSplitMove(row.localId,row.exerciseStableKey,row.dayOfWeek,day,before,objective(trial)),source(row)!!.item.priority)
                        candidates+=candidate
                    }
                }
                execution.comparedRows(round,rowIndex+1,rows.size)
            }
            // Tissue is solely a PASS/FAIL gate, never a comparator input. A stable sort with
            // the unchanged comparator followed by first feasible equals the exhaustive minimum.
            // All structural/ranking candidates still exist; only unneeded tissue calls are skipped.
            // Rejection counts describe gates actually executed, not hypothetical losing candidates.
            var validated = 0
            val selected=candidates.sortedWith(comparator).firstOrNull { candidate ->
                execution.validating(round,validated,candidates.size)
                tissueAllowed(candidate.rows,candidate.move.stableKey).also {
                    if(!it) reject("CHRONOLOGICAL_TISSUE")
                    execution.validating(round,++validated,candidates.size)
                }
            } ?: return finish(plan,week,initial,rows,authority,parents,fixed,primary,moves,rejected,
            initialObjective,objective(rows),metrics(initial),metrics(rows),initialQcr,qcr(rows),tissue(rows),timeRef,ofiRef,"FINITE_LOCAL_OPTIMUM",execution,
                canonicalFailureEmitter)
            check(visited.add(selected.rows.map { it.localId to it.dayOfWeek })) { "REFLOW_CYCLE" }
            check(immutable(selected.rows)==immutable(initial)) { "REFLOW_IMMUTABLE_MUTATION" }
            check(selected.rows.filter { it.localId in fixed }==initial.filter { it.localId in fixed }) { "REFLOW_SPLIT_CHANGED" }
            check(qcr(selected.rows)==initialQcr) { "REFLOW_QCR_CHANGED" }
            counts.acceptedActions++
            counts.performanceMetrics?.let { it.acceptedMoves++ }
            rows=selected.rows; moves+=selected.move
            execution.moved()
        }
        return finish(plan,week,initial,rows,authority,parents,fixed,primary,moves,rejected,
            initialObjective,objective(rows),metrics(initial),metrics(rows),initialQcr,qcr(rows),tissue(rows),timeRef,ofiRef,"BOUNDED_128_MOVE_LIMIT",execution,
            canonicalFailureEmitter)
    }

    private fun finish(plan: GeneratedProgramSkeleton,week: RepresentativeWeek,initial: List<ProgramSkeletonItem>,rows: List<ProgramSkeletonItem>,
        authority: AuthorizedSchedulingTrace,parents: List<String>,fixed: List<String>,primary: Set<String>,moves: List<PostSplitMove>,rejections: Map<String,Int>,
        initialObjective: PostSplitObjective,finalObjective: PostSplitObjective,initialDays: List<BalanceDay>,finalDays: List<BalanceDay>,
        qBefore: String,qAfter: String,tissue: PlannedTissueWeek,timeRef: Double,ofiRef: Double,diagnostic: String,execution: ReflowProgress,
        canonicalFailureEmitter: ((StimulusCanonicalEvaluationFailureReason, String?) -> Nothing)?): PostSplitReflowResult {
        execution.finalizing()
        val assignment=rows.associateBy { week.atomByLocalId.getValue(it.localId) }
        // Preserve each week's own immutable fields/bindings, not the representative row's copies.
        val result=plan.copy(items=plan.items.map { row ->
            val updated=assignment.getValue(week.atomByLocalId.getValue(row.localId))
            row.copy(dayOfWeek=plan.weekDaySchedule.getValue(row.weekNumber).sorted()[week.days.indexOf(updated.dayOfWeek)],orderIndex=updated.orderIndex)
        })
        check(result.items.filter { it.localId in fixed }==plan.items.filter { it.localId in fixed })
        check(result.personalizedDecision?.authorizedScheduling==authority)
        val priorErrors = ProgramProjectionValidator().errors(plan)
        val finalErrorsPreserved = ProgramProjectionValidator().errors(result).all { it in priorErrors }
        if (!finalErrorsPreserved) {
            canonicalFailureEmitter?.invoke(
                StimulusCanonicalEvaluationFailureReason.FINAL_CANONICAL_VALIDATION,
                null
            )
            check(finalErrorsPreserved) { "FINAL_CANONICAL_VALIDATION" }
        }
        check(PrimaryStrengthAnchorSpacingPolicy.allowedRows(result.items,primary))
        check(qBefore==qAfter)
        val trace=PostSplitReflowTrace(if(moves.isEmpty()) "REVIEWED_NO_BENEFICIAL_LEGAL_MOVE" else "APPLIED",parents,fixed,
            personalizedProgramFingerprint(plan.request,plan.items),personalizedProgramFingerprint(result.request,result.items),initialDays,finalDays,
            initialObjective,finalObjective,moves,rejections,PrimaryStrengthAnchorSpacingPolicy.audit(rows,primary).toString(),qBefore,qAfter,tissue,diagnostic,timeRef,ofiRef)
        return PostSplitReflowResult(result,trace)
    }
}
