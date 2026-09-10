package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Scheduling/product policy, not an injury probability or a progression MAIN definition. */
internal object PrimaryStrengthAnchorSpacingPolicy {
    val majorPatterns = setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN,
        MovementCoverage.HORIZONTAL_PUSH, MovementCoverage.VERTICAL_PUSH, MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL)
    fun protects(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, key: String, continuity: Boolean): Boolean =
        continuity && state.anchors.any { it.stableKey == key } && snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
            snapshot.movementCoverage(key) in majorPatterns
    fun distance(a: Int, b: Int): Int {
        require(a in 1..7 && b in 1..7)
        val difference = abs(a-b)
        return minOf(difference, 7-difference)
    }
    fun allowed(days: List<Int>): Boolean = days.indices.all { a -> (a+1 until days.size).all { b -> distance(days[a],days[b]) >= 2 } }
    fun keys(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, continuityKeys: Set<String>): Set<String> =
        continuityKeys.filterTo(sortedSetOf()) { protects(snapshot,state,it,true) }
    fun allowedRows(rows: List<ProgramSkeletonItem>, keys: Set<String>): Boolean =
        rows.groupBy { it.weekNumber }.values.all { week -> keys.all { key -> allowed(week.filter { it.exerciseStableKey == key }.map { it.dayOfWeek }) } }
    fun audit(rows: List<ProgramSkeletonItem>, keys: Set<String>): JSONArray = JSONArray(keys.sorted().map { key ->
        val days=rows.filter { it.weekNumber==1 && it.exerciseStableKey==key }.map { it.dayOfWeek }.sorted()
        JSONObject().put("stableKey",key).put("days",JSONArray(days)).put("passes",allowed(days))
            .put("cyclicDistances",JSONArray(days.indices.flatMap { a -> (a+1 until days.size).map { b -> distance(days[a],days[b]) } }))
    })
}
