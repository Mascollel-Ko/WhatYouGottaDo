package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Read-only follow-up requested by the user. Does not change the general rebalancer's entry policy. */
internal fun reviewFixedSplitRelocations(plan: GeneratedProgramSkeleton, snapshot: PlanningHistorySnapshot): JSONObject {
    val rows = plan.items.filter { it.weekNumber == 1 }
    val days = plan.weekDaySchedule.getValue(1).sorted()
    val authority = requireNotNull(plan.personalizedDecision?.authorizedScheduling)
    val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers(plan.personalizedDecision!!.userAnswers))
    val projection = requireNotNull(snapshot.planDayProjection)
    val loads = mutableMapOf<List<ProgramSkeletonItem>, StandaloneDayLoad>()
    fun load(items: List<ProgramSkeletonItem>) = loads.getOrPut(items.map { it.copy(dayOfWeek = 1, orderIndex = 0) }) { projection.evaluate(items) }
    val timeReference = planningMedian(days.map { day -> rows.filter { it.dayOfWeek == day }.sumOf(::plannedSeconds).toDouble() }.filter { it > 0 })
    val ofiReference = planningMedian(days.filter { day -> rows.any { it.dayOfWeek == day } }.map { day -> load(rows.filter { it.dayOfWeek == day }).ofi.toDouble() })
    fun metrics(items: List<ProgramSkeletonItem>) = days.map { day ->
        val onDay = items.filter { it.dayOfWeek == day }
        val seconds = onDay.sumOf(::plannedSeconds)
        val ofi = load(onDay).ofi
        BalanceDay(day, seconds, ofi, seconds / timeReference, if (ofiReference > 0) ofi / ofiReference else null)
    }
    fun lower(item: ProgramSkeletonItem) = snapshot.movementCoverage(item.exerciseStableKey) in
        setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES) ||
        snapshot.metadata[item.exerciseStableKey]?.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
    fun maxLower(items: List<ProgramSkeletonItem>) = days.maxOf { day -> items.filter { it.dayOfWeek == day && lower(it) }.sumOf(::plannedSeconds) }
    val before = metrics(rows)
    val attempts = JSONArray()
    val protected = JSONArray()
    for (row in rows) {
        val origin = authority.localOrigins[row.localId]
        val source = authority.authorized.firstOrNull { it.id == origin?.authorizedDemandId }?.item
        val session = plan.progressionSessions.firstOrNull { it.key == row.progressionBinding?.sessionKey }
        if (origin?.splitGroupId?.isNotBlank() == true || source == null || source.scheduleTier() == ScheduleTier.CORE_MUST_DO ||
            row.progressionRole == ProgressionRole.MAIN || session?.track?.role == ProgressionRole.MAIN ||
            row.progressionVariant.isNotBlank() || source.styleVariant.isNotBlank() || row.requiredTemplateAnchor) {
            protected.put(JSONObject().put("stableKey", row.exerciseStableKey).put("day", row.dayOfWeek)
                .put("reason", if (origin?.splitGroupId?.isNotBlank() == true) "FIXED_SPLIT_PARENT" else "EXISTING_MAIN_CORE_OR_STRUCTURE_PROTECTION"))
            continue
        }
        for (day in days.filter { it != row.dayOfWeek }) {
            val trial = rows.map { if (it.localId == row.localId) it.copy(dayOfWeek = day) else it }
            val destination = trial.filter { it.dayOfWeek == day }
            val after = metrics(trial)
            val affected = setOf(row.dayOfWeek, day)
            val reason = when {
                !postProcessTissueAllowed(snapshot, state, row.exerciseStableKey) -> "CURRENT_TISSUE_RESTRICTION"
                destination.map { it.exerciseStableKey }.distinct().size != destination.size -> "SAME_KEY"
                destination.sumOf(::plannedSeconds) > plan.request.sessionMinutes * 60 -> "SESSION_TIME"
                !load(destination).feasible -> "DESTINATION_OFI"
                maxLower(trial) > maxLower(rows) -> "LOWER_STRESS_DISPERSION"
                !balanceNonWorsening(before.filter { it.day in affected }, after.filter { it.day in affected }) -> "BAND_WORSENING"
                balanceObjective(after) >= balanceObjective(before) -> "NO_STRICT_IMPROVEMENT"
                !splitTissueAllowed(snapshot, trial, row.exerciseStableKey, plan.weekPlans.first().targetRpeMax) -> "CHRONOLOGICAL_TISSUE"
                else -> "BENEFICIAL_LEGAL_MOVE"
            }
            attempts.put(JSONObject().put("stableKey", row.exerciseStableKey).put("from", row.dayOfWeek).put("to", day)
                .put("reason", reason).put("before", JSONArray(before.map { it.toJson() })).put("after", JSONArray(after.map { it.toJson() })))
        }
    }
    return JSONObject().put("scope", "READ_ONLY_SINGLE_MOVE_REVIEW_SPLIT_FIXED").put("applied", false)
        .put("existingRebalancing", plan.personalizedDecision!!.dayRebalancing?.toJson()).put("protected", protected).put("attempts", attempts)
}
