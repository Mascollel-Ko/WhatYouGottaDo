package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** Observation-only distribution for the actual final set prescriptions. */
data class FinalStimulusNeedEvidence(
    val directUnits: Int = 0,
    val supportiveUnits: Int = 0,
    val directSessions: Int = 0,
    val supportiveSessions: Int = 0,
    val directExposureWeeks: Int = 0,
    val supportiveExposureWeeks: Int = 0,
    val incompatibleDirectCapabilityUnits: Int = 0,
    val incompatibleSupportiveCapabilityUnits: Int = 0,
    val reasonCodes: List<String> = emptyList()
) {
    val directPlannedUnits: Int get() = directUnits
    val supportivePlannedUnits: Int get() = supportiveUnits
    val directPlannedSessions: Int get() = directSessions
    val supportivePlannedSessions: Int get() = supportiveSessions
    val directPlannedExposureWeeks: Int get() = directExposureWeeks
    val supportivePlannedExposureWeeks: Int get() = supportiveExposureWeeks
}

data class FinalStimulusNeedDelta(
    val directUnitsBefore: Int,
    val directUnitsAfter: Int,
    val supportiveUnitsBefore: Int,
    val supportiveUnitsAfter: Int,
    val directSessionsBefore: Int,
    val directSessionsAfter: Int,
    val supportiveSessionsBefore: Int,
    val supportiveSessionsAfter: Int
)

/** Distribution comparison at the exact pre/post PostSplitWeeklyReflow boundary. */
data class FinalStimulusReflowDistributionAudit(
    val status: String,
    val qualityBefore: Map<TrainableQuality, FinalStimulusNeedEvidence> = emptyMap(),
    val qualityAfter: Map<TrainableQuality, FinalStimulusNeedEvidence> = emptyMap(),
    val taskBefore: Map<String, FinalStimulusNeedEvidence> = emptyMap(),
    val taskAfter: Map<String, FinalStimulusNeedEvidence> = emptyMap(),
    val qualityDeltas: Map<TrainableQuality, FinalStimulusNeedDelta> = emptyMap(),
    val taskDeltas: Map<String, FinalStimulusNeedDelta> = emptyMap()
)

data class FinalStimulusNeedAuditResult(
    /** Full final generated program coverage across every week. */
    val finalQualityCoverage: Map<TrainableQuality, FinalStimulusNeedEvidence> = emptyMap(),
    val finalTaskCoverage: Map<String, FinalStimulusNeedEvidence> = emptyMap(),
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val finalReflowDistribution: FinalStimulusReflowDistributionAudit? = null
) {
    val reflowAuditStatus: String? get() = finalReflowDistribution?.status
}

/** One linear traversal of actual final set prescriptions; no OFI/tissue projection is invoked. */
class FinalStimulusNeedAudit {
    fun audit(
        finalPlan: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY,
        beforePlan: GeneratedProgramSkeleton? = null
    ): FinalStimulusNeedAuditResult {
        val finalCoverage = summarize(finalPlan.items, snapshot, physicalQualityCatalog)
        val reflow = when {
            beforePlan != null -> {
                val before = summarize(beforePlan.items, snapshot, physicalQualityCatalog)
                distribution("PRE_REFLOW_PLAN_PROVIDED", before, finalCoverage)
            }
            finalPlan.personalizedDecision?.postSplitReflow == null -> {
                val representative = summarize(finalPlan.items.filter { it.weekNumber == 1 }, snapshot, physicalQualityCatalog)
                distribution("NO_FINAL_REFLOW_MOVES", representative, representative)
            }
            finalPlan.personalizedDecision.postSplitReflow.moves.isEmpty() ||
                finalPlan.personalizedDecision.postSplitReflow.state == "NOT_APPLICABLE_NO_MANDATORY_SPLIT" -> {
                val representative = summarize(finalPlan.items.filter { it.weekNumber == 1 }, snapshot, physicalQualityCatalog)
                distribution("NO_FINAL_REFLOW_MOVES", representative, representative)
            }
            else -> {
                val trace = finalPlan.personalizedDecision.postSplitReflow
                val after = summarize(finalPlan.items.filter { it.weekNumber == 1 }, snapshot, physicalQualityCatalog)
                val beforeRows = reconstructBeforeRepresentative(finalPlan, trace.moves)
                if (beforeRows == null) {
                    FinalStimulusReflowDistributionAudit("PRE_REFLOW_RECONSTRUCTION_UNAVAILABLE")
                } else {
                    distribution(
                        "RECONSTRUCTED_FROM_POST_SPLIT_TRACE",
                        summarize(beforeRows, snapshot, physicalQualityCatalog),
                        after
                    )
                }
            }
        }
        return FinalStimulusNeedAuditResult(
            finalQualityCoverage = finalCoverage.first,
            finalTaskCoverage = finalCoverage.second,
            finalReflowDistribution = reflow
        )
    }

    private fun distribution(
        status: String,
        before: Pair<Map<TrainableQuality, FinalStimulusNeedEvidence>, Map<String, FinalStimulusNeedEvidence>>,
        after: Pair<Map<TrainableQuality, FinalStimulusNeedEvidence>, Map<String, FinalStimulusNeedEvidence>>
    ): FinalStimulusReflowDistributionAudit = FinalStimulusReflowDistributionAudit(
        status = status,
        qualityBefore = before.first,
        qualityAfter = after.first,
        taskBefore = before.second,
        taskAfter = after.second,
        qualityDeltas = qualityDelta(before.first, after.first),
        taskDeltas = taskDelta(before.second, after.second)
    )

    /** Reverse-applies the exact moves recorded by PostSplitWeeklyReflow. */
    private fun reconstructBeforeRepresentative(
        finalPlan: GeneratedProgramSkeleton,
        moves: List<PostSplitMove>
    ): List<ProgramSkeletonItem>? {
        val rows = finalPlan.items.filter { it.weekNumber == 1 }
        if (rows.isEmpty() || rows.groupingBy { it.localId }.eachCount().values.any { it != 1 }) return null
        val reconstructed = rows.toMutableList()
        for (move in moves.asReversed()) {
            if (move.from == move.to) return null
            val matches = reconstructed.withIndex().filter { it.value.localId == move.localId }
            if (matches.size != 1) return null
            val index = matches.single().index
            val row = reconstructed[index]
            if (row.exerciseStableKey != move.stableKey || row.dayOfWeek != move.to) return null
            reconstructed[index] = row.copy(dayOfWeek = move.from)
        }
        return reconstructed.toList()
    }

    private fun summarize(
        items: List<ProgramSkeletonItem>,
        snapshot: PlanningHistorySnapshot,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog
    ): Pair<Map<TrainableQuality, FinalStimulusNeedEvidence>, Map<String, FinalStimulusNeedEvidence>> {
        val qualities = TrainableQuality.entries.associateWith { Accumulator() }.toMutableMap()
        val tasks = CanonicalPerformanceTaskQualityRequirements.rows.map { it.task }.distinct()
            .associateWith { Accumulator() }.toMutableMap()
        items.forEach { item ->
            val sets = item.setPrescriptions
            if (sets.isEmpty()) return@forEach
            val profile = snapshot.stimulusExposureLedger.facetProfilesByStableKey[item.exerciseStableKey]
            val physical = profile?.physicalQualities ?: physicalQualityCatalog.relations(item.exerciseStableKey)
            val badminton = profile?.badmintonObjectives.orEmpty()
            val structured = snapshot.activityKind(item.exerciseStableKey) in STRUCTURED_TASK_KINDS
            sets.forEach { set ->
                val session = item.weekNumber to item.dayOfWeek
                physical.groupBy(ExercisePhysicalQualityRelation::qualityId).forEach { (quality, relations) ->
                    val direct = relations.any { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                    val supportive = !direct && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
                    if (direct || supportive) {
                        qualities.getValue(quality).add(session, direct, supportive,
                            prescriptionShapeCompatible(quality, set.reps), quality)
                    }
                }
                if (structured) {
                    if (profile != null) {
                        // Resolve one tier per actual set/objective before crediting the set.
                        badminton.filter { it.objective.name in tasks }
                            .groupBy { it.objective.name }
                            .forEach { (objective, relations) ->
                                val direct = relations.any { it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT }
                                val supportive = !direct && relations.any { it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE }
                                if (direct || supportive) tasks.getValue(objective).add(session, direct, supportive, true, null)
                            }
                    } else {
                        // The fallback maps already encode direct precedence; preserve it per set.
                        val directObjectives = snapshot.badmintonDirectObjectives[item.exerciseStableKey].orEmpty()
                        directObjectives.filter { it in tasks }.forEach { tasks.getValue(it).add(session, true, false, true, null) }
                        snapshot.badmintonSupportiveObjectives[item.exerciseStableKey].orEmpty()
                            .filter { it in tasks && it !in directObjectives }
                            .forEach { tasks.getValue(it).add(session, false, true, true, null) }
                    }
                }
            }
        }
        return qualities.mapValues { (_, accumulator) -> accumulator.evidence() } to
            tasks.mapValues { (_, accumulator) -> accumulator.evidence() }
    }

    private class Accumulator {
        var direct = 0
        var supportive = 0
        var incompatibleDirect = 0
        var incompatibleSupportive = 0
        var proxyCoverage = false
        val directSessions = linkedSetOf<Pair<Int, Int>>()
        val supportiveSessions = linkedSetOf<Pair<Int, Int>>()
        val directWeeks = linkedSetOf<Int>()
        val supportiveWeeks = linkedSetOf<Int>()

        fun add(session: Pair<Int, Int>, directRelation: Boolean, supportiveRelation: Boolean, compatible: Boolean, quality: TrainableQuality?) {
            when {
                directRelation && compatible -> {
                    direct++
                    directSessions += session
                    directWeeks += session.first
                    if (quality !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)) proxyCoverage = true
                }
                directRelation -> incompatibleDirect++
                supportiveRelation && compatible -> {
                    supportive++
                    supportiveSessions += session
                    supportiveWeeks += session.first
                }
                supportiveRelation -> incompatibleSupportive++
            }
        }

        fun evidence(): FinalStimulusNeedEvidence {
            val reasons = buildList {
                if (direct == 0 && supportive > 0) add("SUPPORTIVE_ONLY_FINAL_COVERAGE")
                if (direct == 0 && supportive == 0 && incompatibleDirect == 0 && incompatibleSupportive == 0) add("NO_DIRECT_FINAL_COVERAGE")
                if (incompatibleDirect > 0) add("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE")
                if (incompatibleSupportive > 0) add("SUPPORTIVE_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE")
                if (direct > 0) add("DIRECT_FINAL_COVERAGE_PRESENT")
                if (proxyCoverage) add("CAPABILITY_PROXY_FINAL_COVERAGE")
            }
            return FinalStimulusNeedEvidence(direct, supportive, directSessions.size, supportiveSessions.size,
                directWeeks.size, supportiveWeeks.size, incompatibleDirect, incompatibleSupportive, reasons)
        }
    }

    private fun qualityDelta(
        before: Map<TrainableQuality, FinalStimulusNeedEvidence>,
        after: Map<TrainableQuality, FinalStimulusNeedEvidence>
    ): Map<TrainableQuality, FinalStimulusNeedDelta> = before.keys.union(after.keys).associateWith { key ->
        val a = before[key] ?: FinalStimulusNeedEvidence()
        val b = after[key] ?: FinalStimulusNeedEvidence()
        FinalStimulusNeedDelta(a.directUnits, b.directUnits, a.supportiveUnits, b.supportiveUnits,
            a.directSessions, b.directSessions, a.supportiveSessions, b.supportiveSessions)
    }

    private fun taskDelta(
        before: Map<String, FinalStimulusNeedEvidence>,
        after: Map<String, FinalStimulusNeedEvidence>
    ): Map<String, FinalStimulusNeedDelta> = before.keys.union(after.keys).associateWith { key ->
        val a = before[key] ?: FinalStimulusNeedEvidence()
        val b = after[key] ?: FinalStimulusNeedEvidence()
        FinalStimulusNeedDelta(a.directUnits, b.directUnits, a.supportiveUnits, b.supportiveUnits,
            a.directSessions, b.directSessions, a.supportiveSessions, b.supportiveSessions)
    }

    private companion object {
        val STRUCTURED_TASK_KINDS = setOf(
            PlannedActivityKind.RESISTANCE,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        )
    }
}

internal fun FinalStimulusNeedAuditResult.toCompactJson(): JSONObject {
    fun evidenceJson(evidence: FinalStimulusNeedEvidence) = JSONObject()
        .put("directUnits", evidence.directUnits)
        .put("supportiveUnits", evidence.supportiveUnits)
        .put("directSessions", evidence.directSessions)
        .put("supportiveSessions", evidence.supportiveSessions)
        .put("directExposureWeeks", evidence.directExposureWeeks)
        .put("supportiveExposureWeeks", evidence.supportiveExposureWeeks)
        .put("incompatibleDirectCapabilityUnits", evidence.incompatibleDirectCapabilityUnits)
        .put("incompatibleSupportiveCapabilityUnits", evidence.incompatibleSupportiveCapabilityUnits)
        .put("reasonCodes", JSONArray(evidence.reasonCodes))
    fun deltaJson(delta: FinalStimulusNeedDelta) = JSONObject()
        .put("directUnitsBefore", delta.directUnitsBefore).put("directUnitsAfter", delta.directUnitsAfter)
        .put("supportiveUnitsBefore", delta.supportiveUnitsBefore).put("supportiveUnitsAfter", delta.supportiveUnitsAfter)
        .put("directSessionsBefore", delta.directSessionsBefore).put("directSessionsAfter", delta.directSessionsAfter)
        .put("supportiveSessionsBefore", delta.supportiveSessionsBefore).put("supportiveSessionsAfter", delta.supportiveSessionsAfter)
    fun qualityMap(map: Map<TrainableQuality, FinalStimulusNeedEvidence>) = JSONArray(map.map { (quality, evidence) ->
        evidenceJson(evidence).put("quality", quality.name)
    })
    fun taskMap(map: Map<String, FinalStimulusNeedEvidence>) = JSONArray(map.map { (task, evidence) ->
        evidenceJson(evidence).put("task", task)
    })
    fun qualityDeltasJson(map: Map<TrainableQuality, FinalStimulusNeedDelta>) = JSONArray(map.map { (quality, delta) ->
        deltaJson(delta).put("quality", quality.name)
    })
    fun taskDeltasJson(map: Map<String, FinalStimulusNeedDelta>) = JSONArray(map.map { (task, delta) ->
        deltaJson(delta).put("task", task)
    })
    val reflow = finalReflowDistribution
    fun reflowJson(value: FinalStimulusReflowDistributionAudit?) = value?.let {
        JSONObject()
            .put("status", it.status)
            .put("qualityBefore", qualityMap(it.qualityBefore))
            .put("qualityAfter", qualityMap(it.qualityAfter))
            .put("taskBefore", taskMap(it.taskBefore))
            .put("taskAfter", taskMap(it.taskAfter))
            .put("qualityDeltas", qualityDeltasJson(it.qualityDeltas))
            .put("taskDeltas", taskDeltasJson(it.taskDeltas))
    }
    return JSONObject()
        .put("shadowOnly", shadowOnly)
        .put("prescriptionAuthority", prescriptionAuthority)
        .put("finalQualityCoverage", qualityMap(finalQualityCoverage))
        .put("finalTaskCoverage", taskMap(finalTaskCoverage))
        .put("finalReflowDistribution", reflowJson(reflow))
}
