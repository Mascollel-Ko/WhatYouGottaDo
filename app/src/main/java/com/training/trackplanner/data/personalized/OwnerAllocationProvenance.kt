package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import org.json.JSONArray
import org.json.JSONObject

/** Pipeline point that emitted an owner-local allocation or placement mutation. */
enum class OwnerAllocationStage {
    MATERIAL_DEMAND,
    FINITE_EXECUTION_ALLOCATION,
    TIMED_EXECUTION_ALLOCATION,
    INITIAL_WEEKLY_PLACEMENT,
    INITIAL_MAIN_PLACEMENT,
    SPLIT_AWARE_CONTINUITY_ALLOCATION,
    MANDATORY_CONTINUITY_PLACEMENT,
    RESIDUAL_COMPLETION,
    PROGRAM_REPAIR,
    BOUNDED_DAY_REBALANCER,
    FREQUENCY_EXPANSION,
    POST_SPLIT_WEEKLY_REFLOW,
    EXACT_PRESCRIPTION_FUNDED_MATERIALIZATION
}

enum class OwnerAllocationAction {
    ADDED,
    REMOVED,
    SET_COUNT_REDUCED,
    SET_COUNT_EXPANDED,
    PLACEMENT_ASSIGNED,
    PLACEMENT_MOVED,
    ORDER_CHANGED,
    FREQUENCY_REPLICATED,
    FREQUENCY_DROPPED,
    PRESCRIPTION_CHANGED
}

/** Typed cause captured where the accepted mutation is made. Unknown remains fail-closed. */
enum class OwnerAllocationCause {
    CAPACITY_LIMIT,
    CAPACITY_SHARE_ALLOCATION,
    SESSION_TIME_LIMIT,
    OWNER_PRIORITY,
    MATERIAL_DEMAND,
    FREQUENCY_EXPANSION,
    HARD_GATE,
    OFI_GATE,
    TISSUE_GATE,
    REBALANCE_OBJECTIVE,
    POST_SPLIT_REFLOW,
    RESIDUAL_COMPLETION,
    INITIAL_PLACEMENT_POLICY,
    INITIAL_MAIN_PLACEMENT_OBJECTIVE,
    MANDATORY_CONTINUITY,
    AUTHORIZED_SET_LIMIT,
    UNKNOWN_UNPROVEN
}

/** Internal accepted layout change emitted by the weekly placement implementation. */
internal data class WeeklyOwnerPlacementMutation(
    val timed: TimedPlannedExercise,
    val beforeDay: Int?,
    val beforeOrder: Int?,
    val afterDay: Int?,
    val afterOrder: Int?,
    val stage: OwnerAllocationStage,
    val cause: OwnerAllocationCause
)

/** Exact state at one weekly skeleton row or at a pre-placement allocation boundary. */
data class OwnerAllocationState(
    val week: Int?,
    val day: Int?,
    val order: Int?,
    val setCount: Int,
    val setPrescriptions: List<ProgramSetPrescription>,
    val prescription: String?,
    val selectionRole: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("week", week)
        .put("day", day)
        .put("order", order)
        .put("setCount", setCount)
        .put("setPrescriptions", auditSets(setPrescriptions))
        .put("prescription", prescription)
        .put("selectionRole", selectionRole)
}

/**
 * One accepted mutation from its source stage. Owner identity always includes the exact role;
 * target/quality links are descriptive authority references, never authority by themselves.
 */
data class OwnerAllocationProvenance(
    val owner: StimulusPrescriptionOwnerIdentity,
    val stage: OwnerAllocationStage,
    val action: OwnerAllocationAction,
    val before: OwnerAllocationState?,
    val after: OwnerAllocationState?,
    val cause: OwnerAllocationCause,
    val targetIds: Set<String> = emptySet(),
    val qualities: Set<String> = emptySet(),
    val authorizedDemandIds: Set<String> = emptySet(),
    val evidenceCodes: List<String> = emptyList(),
    /** Stable order among accepted mutations emitted by the same stage. */
    val mutationSequence: Int = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("owner", JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole))
        .put("stage", stage.name)
        .put("action", action.name)
        .put("before", before?.toJson())
        .put("after", after?.toJson())
        .put("cause", cause.name)
        .put("targetIds", JSONArray(targetIds.sorted()))
        .put("qualities", JSONArray(qualities.sorted()))
        .put("authorizedDemandIds", JSONArray(authorizedDemandIds.sorted()))
        .put("evidenceCodes", JSONArray(evidenceCodes.distinct().sorted()))
        .put("mutationSequence", mutationSequence)
}

/** Exact directed edge, emitted only when an allocator itself names both owner identities. */
data class OwnerDisplacementEdge(
    val causeOwner: StimulusPrescriptionOwnerIdentity,
    val displacedOwner: StimulusPrescriptionOwnerIdentity,
    val stage: OwnerAllocationStage,
    val reason: OwnerAllocationCause,
    val displacedUnits: Int,
    val week: Int? = null,
    val targetIds: Set<String> = emptySet(),
    val qualities: Set<String> = emptySet(),
    val authorizedDemandIds: Set<String> = emptySet(),
    val mutationSequence: Int = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("causeOwner", JSONObject().put("stableKey", causeOwner.stableKey).put("selectionRole", causeOwner.selectionRole))
        .put("displacedOwner", JSONObject().put("stableKey", displacedOwner.stableKey).put("selectionRole", displacedOwner.selectionRole))
        .put("stage", stage.name)
        .put("reason", reason.name)
        .put("displacedUnits", displacedUnits)
        .put("week", week)
        .put("targetIds", JSONArray(targetIds.sorted()))
        .put("qualities", JSONArray(qualities.sorted()))
        .put("authorizedDemandIds", JSONArray(authorizedDemandIds.sorted()))
        .put("mutationSequence", mutationSequence)
}

internal fun ownerAllocationState(
    week: Int?, day: Int?, order: Int?, setCount: Int,
    setPrescriptions: List<ProgramSetPrescription>, prescription: String?, selectionRole: String
) = OwnerAllocationState(week, day, order, setCount, setPrescriptions.toList(), prescription, selectionRole)

internal fun ownerAllocationState(row: com.training.trackplanner.data.ProgramSkeletonItem) = OwnerAllocationState(
    row.weekNumber, row.dayOfWeek, row.orderIndex, row.setCount, row.setPrescriptions.toList(), row.prescription, row.selectionRole
)

internal fun List<OwnerAllocationProvenance>.deterministicOwnerOrder(): List<OwnerAllocationProvenance> =
    sortedWith(compareBy<OwnerAllocationProvenance>({ it.owner.stableKey }, { it.owner.selectionRole }, { it.stage.ordinal },
        { it.mutationSequence },
        { it.before?.week ?: it.after?.week ?: 0 }, { it.before?.day ?: it.after?.day ?: 0 },
        { it.before?.order ?: it.after?.order ?: 0 }, { it.action.ordinal }, { it.cause.ordinal },
        { it.before?.setCount ?: -1 }, { it.after?.setCount ?: -1 }))

internal fun expandLogicalPlacementProvenance(
    events: List<OwnerAllocationProvenance>,
    weekDaySchedule: Map<Int, Set<Int>>,
    emittedWeeks: Set<Int> = weekDaySchedule.keys
): List<OwnerAllocationProvenance> = events.flatMap { event ->
    val alreadyWeekly = event.before?.week != null || event.after?.week != null
    val weeks = if (alreadyWeekly) weekDaySchedule.toSortedMap().filterKeys { it in emittedWeeks &&
        it == (event.after?.week ?: event.before?.week) } else weekDaySchedule.toSortedMap().filterKeys { it in emittedWeeks }
    weeks.map { (week, days) ->
        val actualDays = days.sorted()
        fun actual(state: OwnerAllocationState?): OwnerAllocationState? = state?.copy(
            week = week,
            day = if (event.stage in setOf(OwnerAllocationStage.INITIAL_WEEKLY_PLACEMENT,
                    OwnerAllocationStage.INITIAL_MAIN_PLACEMENT, OwnerAllocationStage.SPLIT_AWARE_CONTINUITY_ALLOCATION,
                    OwnerAllocationStage.MANDATORY_CONTINUITY_PLACEMENT)) state.day?.let { actualDays.getOrNull(it - 1) } else state.day
        )
        event.copy(before = actual(event.before), after = actual(event.after))
    }
}.deterministicOwnerOrder()

/** The accepted representative-week residual is mirrored into these exact output weeks. */
internal fun expandMirroredOwnerProvenance(
    events: List<OwnerAllocationProvenance>,
    weekDaySchedule: Map<Int, Set<Int>>
): List<OwnerAllocationProvenance> {
    val firstDays = weekDaySchedule[1]?.sorted().orEmpty()
    if (firstDays.isEmpty()) return events.deterministicOwnerOrder()
    return events.flatMap { event ->
        val sourceWeek = event.after?.week ?: event.before?.week
        if (sourceWeek != 1) listOf(event) else weekDaySchedule.toSortedMap().map { (week, days) ->
            val targetDays = days.sorted()
            fun mirrored(state: OwnerAllocationState?) = state?.copy(week = week,
                day = state.day?.let { logicalDay -> firstDays.indexOf(logicalDay).takeIf { it >= 0 }?.let(targetDays::getOrNull) })
            event.copy(before = mirrored(event.before), after = mirrored(event.after))
        }
    }.deterministicOwnerOrder()
}

/** Exact typed displacement proof used by B7. Capacity language and identity fragments cannot satisfy it. */
internal fun hasExactOwnerDisplacementProof(
    events: List<OwnerAllocationProvenance>,
    edges: List<OwnerDisplacementEdge>,
    causeOwner: StimulusPrescriptionOwnerIdentity,
    displacedOwner: StimulusPrescriptionOwnerIdentity,
    before: OwnerAllocationState,
    after: OwnerAllocationState
): Boolean {
    if (before.selectionRole != displacedOwner.selectionRole || after.selectionRole != displacedOwner.selectionRole ||
        before.week == null || before.week != after.week || before.setCount != before.setPrescriptions.size ||
        after.setCount != after.setPrescriptions.size || after.setCount >= before.setCount ||
        before.prescription != after.prescription ||
        before.setPrescriptions.take(after.setPrescriptions.size) != after.setPrescriptions) return false
    val ownerMutations = events.filter { event -> event.owner == displacedOwner &&
        event.before?.week == before.week && event.action in setOf(OwnerAllocationAction.REMOVED,
            OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED,
            OwnerAllocationAction.PRESCRIPTION_CHANGED) }
        .sortedWith(compareBy({ it.stage.ordinal }, { it.mutationSequence }, { it.action.ordinal }))
    val finalMutation = ownerMutations.lastOrNull() ?: return false
    if (finalMutation.action != OwnerAllocationAction.SET_COUNT_REDUCED || finalMutation.before != before ||
        finalMutation.after != after || finalMutation.cause == OwnerAllocationCause.UNKNOWN_UNPROVEN) return false
    return edges.any { edge -> edge.causeOwner == causeOwner && edge.displacedOwner == displacedOwner &&
        edge.stage == finalMutation.stage && edge.reason == finalMutation.cause &&
        edge.displacedUnits == before.setCount - after.setCount && edge.week == before.week }
}

internal fun PlanningBudget.withOwnerAllocationProvenance(
    additional: List<OwnerAllocationProvenance>,
    exactAuthority: ExactPrescriptionAuthorizationProvider? = null
): PlanningBudget = copy(execution = execution?.let { trace ->
    trace.copy(ownerAllocationProvenance = (trace.ownerAllocationProvenance + additional)
        .map { it.withExactAuthority(exactAuthority) }.deterministicOwnerOrder())
})

internal fun OwnerAllocationProvenance.withExactAuthority(
    exactAuthority: ExactPrescriptionAuthorizationProvider?
): OwnerAllocationProvenance {
    val authorities = exactAuthority?.authorizedPrescriptions?.keys.orEmpty().filter { it.owner == owner }
    val authorityQualities = authorities.mapTo(sortedSetOf()) { it.quality.name }
    return if (authorityQualities.isEmpty()) this else copy(
        targetIds = targetIds + authorityQualities.map { "QUALITY:$it" },
        qualities = qualities + authorityQualities
    )
}

