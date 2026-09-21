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

data class FinalStimulusNeedAuditResult(
    val qualityBefore: Map<TrainableQuality, FinalStimulusNeedEvidence> = emptyMap(),
    val qualityAfter: Map<TrainableQuality, FinalStimulusNeedEvidence> = emptyMap(),
    val taskBefore: Map<String, FinalStimulusNeedEvidence> = emptyMap(),
    val taskAfter: Map<String, FinalStimulusNeedEvidence> = emptyMap(),
    val qualityDeltas: Map<TrainableQuality, FinalStimulusNeedDelta> = emptyMap(),
    val taskDeltas: Map<String, FinalStimulusNeedDelta> = emptyMap(),
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

/** One linear traversal of actual final set prescriptions; no OFI/tissue projection is invoked. */
class FinalStimulusNeedAudit {
    fun audit(
        finalPlan: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY,
        beforePlan: GeneratedProgramSkeleton? = null
    ): FinalStimulusNeedAuditResult {
        val after = summarize(finalPlan.items, snapshot, physicalQualityCatalog)
        val before = beforePlan?.let { summarize(it.items, snapshot, physicalQualityCatalog) }
        val qualityDeltas = if (before == null) emptyMap() else qualityDelta(before.first, after.first)
        val taskDeltas = if (before == null) emptyMap() else taskDelta(before.second, after.second)
        return FinalStimulusNeedAuditResult(
            qualityBefore = before?.first.orEmpty(), qualityAfter = after.first,
            taskBefore = before?.second.orEmpty(), taskAfter = after.second,
            qualityDeltas = qualityDeltas, taskDeltas = taskDeltas
        )
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
                val realized = provisionalRealizedStimulusClass(set.reps)
                physical.groupBy(ExercisePhysicalQualityRelation::qualityId).forEach { (quality, relations) ->
                    val direct = relations.any { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                    val supportive = !direct && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
                    if (!direct && !supportive) return@forEach
                    val compatible = stimulusPrescriptionCompatible(quality, realized)
                    qualities.getValue(quality).add(session, direct, supportive, compatible, quality)
                }
                if (structured) {
                    if (profile != null) badminton.filter { it.objective.name in tasks }.forEach { relation ->
                        when (relation.transferLevel) {
                            BadmintonObjectiveTransferLevel.DIRECT -> tasks.getValue(relation.objective.name).add(session, true, false, true, null)
                            BadmintonObjectiveTransferLevel.SUPPORTIVE -> tasks.getValue(relation.objective.name).add(session, false, true, true, null)
                            else -> Unit
                        }
                    }
                    else {
                        // The snapshot keeps the reviewed transfer maps even for planned exercises
                        // that were absent from historical ledger profiles.
                        snapshot.badmintonDirectObjectives[item.exerciseStableKey].orEmpty().filter { it in tasks }
                            .forEach { tasks.getValue(it).add(session, true, false, true, null) }
                        snapshot.badmintonSupportiveObjectives[item.exerciseStableKey].orEmpty()
                            .filter { it in tasks && it !in snapshot.badmintonDirectObjectives[item.exerciseStableKey].orEmpty() }
                            .forEach { tasks.getValue(it).add(session, false, true, true, null) }
                    }
                }
            }
        }
        return qualities.mapValues { (_, accumulator) -> accumulator.evidence() } to tasks.mapValues { (_, accumulator) -> accumulator.evidence() }
    }

    private class Accumulator {
        var direct = 0
        var supportive = 0
        var incompatibleDirect = 0
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
            }
        }

        fun evidence(): FinalStimulusNeedEvidence {
            val reasons = buildList {
                if (direct == 0 && supportive > 0) add("SUPPORTIVE_ONLY_FINAL_COVERAGE")
                if (direct == 0 && supportive == 0 && incompatibleDirect == 0) add("NO_DIRECT_FINAL_COVERAGE")
                if (incompatibleDirect > 0) add("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE")
                if (direct > 0) add("DIRECT_FINAL_COVERAGE_PRESENT")
                if (proxyCoverage) add("CAPABILITY_PROXY_FINAL_COVERAGE")
            }
            return FinalStimulusNeedEvidence(direct, supportive, directSessions.size, supportiveSessions.size,
                directWeeks.size, supportiveWeeks.size, incompatibleDirect, reasons)
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

internal fun FinalStimulusNeedAuditResult.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("qualityAfter", JSONArray(qualityAfter.map { (quality, evidence) -> JSONObject()
        .put("quality", quality.name).put("directUnits", evidence.directUnits)
        .put("supportiveUnits", evidence.supportiveUnits).put("directSessions", evidence.directSessions)
        .put("supportiveSessions", evidence.supportiveSessions)
        .put("directExposureWeeks", evidence.directExposureWeeks)
        .put("supportiveExposureWeeks", evidence.supportiveExposureWeeks)
        .put("incompatibleDirectCapabilityUnits", evidence.incompatibleDirectCapabilityUnits)
        .put("reasonCodes", JSONArray(evidence.reasonCodes))
    }))
    .put("taskAfter", JSONArray(taskAfter.map { (task, evidence) -> JSONObject()
        .put("task", task).put("directUnits", evidence.directUnits).put("supportiveUnits", evidence.supportiveUnits)
        .put("directSessions", evidence.directSessions).put("supportiveSessions", evidence.supportiveSessions)
        .put("directExposureWeeks", evidence.directExposureWeeks)
        .put("supportiveExposureWeeks", evidence.supportiveExposureWeeks)
        .put("reasonCodes", JSONArray(evidence.reasonCodes))
    }))
    .put("qualityDeltas", JSONArray(qualityDeltas.map { (quality, delta) -> JSONObject()
        .put("quality", quality.name).put("directUnitsBefore", delta.directUnitsBefore).put("directUnitsAfter", delta.directUnitsAfter)
        .put("directSessionsBefore", delta.directSessionsBefore).put("directSessionsAfter", delta.directSessionsAfter)
    }))
    .put("taskDeltas", JSONArray(taskDeltas.map { (task, delta) -> JSONObject()
        .put("task", task).put("directUnitsBefore", delta.directUnitsBefore).put("directUnitsAfter", delta.directUnitsAfter)
        .put("directSessionsBefore", delta.directSessionsBefore).put("directSessionsAfter", delta.directSessionsAfter)
    }))
