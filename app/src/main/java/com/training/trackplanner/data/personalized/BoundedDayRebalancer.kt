package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.fatigue.FatigueThresholds
import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

internal const val LOWER_BALANCE_RATIO = .70
internal const val UPPER_BALANCE_RATIO = 1.30
internal fun bandDistance(ratio: Double): Double = when {
    ratio < LOWER_BALANCE_RATIO -> LOWER_BALANCE_RATIO - ratio
    ratio > UPPER_BALANCE_RATIO -> ratio - UPPER_BALANCE_RATIO
    else -> 0.0
}

data class BalanceObjective(val bandViolationCount: Int, val maxBandDistance: Double, val totalBandDistance: Double) : Comparable<BalanceObjective> {
    override fun compareTo(other: BalanceObjective): Int = compareValuesBy(this, other,
        BalanceObjective::bandViolationCount, BalanceObjective::maxBandDistance, BalanceObjective::totalBandDistance)
    fun toJson(): JSONObject = JSONObject().put("bandViolationCount", bandViolationCount)
        .put("maxBandDistance", maxBandDistance).put("totalBandDistance", totalBandDistance)
}
data class BalanceDay(val day: Int, val seconds: Int, val standaloneOfi: Int, val timeRatio: Double, val ofiRatio: Double?) {
    val timeDistance: Double get() = bandDistance(timeRatio)
    val ofiDistance: Double get() = ofiRatio?.let(::bandDistance) ?: 0.0
    val needsBalance: Boolean get() = timeDistance > 0 || ofiDistance > 0
    val source: Boolean get() = timeRatio > UPPER_BALANCE_RATIO || (ofiRatio ?: 0.0) > UPPER_BALANCE_RATIO ||
        standaloneOfi >= FatigueThresholds.OFI_CAUTION_START
    val destination: Boolean get() = timeRatio < LOWER_BALANCE_RATIO || (ofiRatio != null && ofiRatio < LOWER_BALANCE_RATIO)
    fun toJson(): JSONObject = JSONObject().put("day", day).put("seconds", seconds).put("standaloneOfi", standaloneOfi)
        .put("timeRatio", timeRatio).put("ofiRatio", ofiRatio)
}
data class BalanceAction(val actionType: String, val atomIds: List<String>, val stableKeys: List<String>, val sourceDay: Int,
    val destinationDay: Int, val before: List<BalanceDay>, val after: List<BalanceDay>, val beforeObjective: BalanceObjective,
    val afterObjective: BalanceObjective, val ofiGate: String = "PASS", val tissueGate: String = "CANONICAL_CURRENT_RESTRICTIONS_PASS",
    val lowerStressDispersion: String = "NON_INCREASING", val route: String = if (actionType == "SWAP") "SWAP" else "PRIMARY_MOVE") {
    fun toJson(): JSONObject = JSONObject().put("actionType", actionType).put("atomIds", JSONArray(atomIds))
        .put("stableKeys", JSONArray(stableKeys)).put("sourceDay", sourceDay).put("destinationDay", destinationDay)
        .put("before", JSONArray(before.map { it.toJson() })).put("after", JSONArray(after.map { it.toJson() }))
        .put("beforeObjective", beforeObjective.toJson()).put("afterObjective", afterObjective.toJson())
        .put("ofiGate", ofiGate).put("tissueGate", tissueGate).put("lowerStressDispersion", lowerStressDispersion)
        .put("route", route)
}
data class DayRebalancingTrace(val balanceState: String, val preRebalanceFingerprint: String, val finalFingerprint: String,
    val timeReference: Double, val ofiReference: Double, val initialDays: List<BalanceDay>, val finalDays: List<BalanceDay>,
    val initialObjective: BalanceObjective, val finalObjective: BalanceObjective, val actions: List<BalanceAction>,
    val diagnostic: String = "", val primaryBefore: StrengthPrimaryObjective? = null,
    val primaryAfter: StrengthPrimaryObjective? = null, val primaryRejections: Map<String, Int> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().put("balanceState", balanceState).put("preRebalanceFingerprint", preRebalanceFingerprint)
        .put("finalFingerprint", finalFingerprint).put("timeReference", timeReference).put("ofiReference", ofiReference)
        .put("ofiRatioState", if (ofiReference > 0) "ENABLED" else "OFI_RATIO_BALANCING_DISABLED")
        .put("initialDays", JSONArray(initialDays.map { it.toJson() })).put("finalDays", JSONArray(finalDays.map { it.toJson() }))
        .put("initialObjective", initialObjective.toJson()).put("finalObjective", finalObjective.toJson())
        .put("actions", JSONArray(actions.map { it.toJson() })).put("diagnostic", diagnostic)
        .put("primaryOverlapBefore",primaryBefore?.overlap).put("primaryOverlapAfter",primaryAfter?.overlap)
        .put("primaryMaximumBefore",primaryBefore?.maximumPerDay).put("primaryMaximumAfter",primaryAfter?.maximumPerDay)
        .put("primaryRejections",JSONObject(primaryRejections))
}

internal fun balanceObjective(days: List<BalanceDay>): BalanceObjective {
    val distances = days.flatMap { listOf(it.timeDistance, it.ofiDistance) }
    return BalanceObjective(distances.count { it > 0 }, distances.maxOrNull() ?: 0.0, distances.sum())
}
internal fun balanceNonWorsening(before: List<BalanceDay>, after: List<BalanceDay>): Boolean =
    after.all { day -> before.first { it.day == day.day }.let { prior -> day.timeDistance <= prior.timeDistance && day.ofiDistance <= prior.ofiDistance } } &&
        after.any { day -> before.first { it.day == day.day }.let { prior -> day.timeDistance < prior.timeDistance || day.ofiDistance < prior.ofiDistance } }

internal data class RebalancingResult(val skeleton: GeneratedProgramSkeleton, val trace: DayRebalancingTrace)
private data class RebalanceCandidate(val rows: List<ProgramSkeletonItem>, val metrics: List<BalanceDay>, val action: BalanceAction,
    val movementCost: Int, val priority: Int, val keyOrder: String, val identityOrder: String)

/** Whole-item placement only. References freeze once, and every accepted action strictly improves a finite objective. */
internal class BoundedDayRebalancer(private val additionalGate: (List<ProgramSkeletonItem>) -> Boolean = { true }) {
    fun rebalance(completed: CompletionResult, snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
        projection: PlanDayProjection?): RebalancingResult {
        val skeleton = completed.skeleton
        val fingerprint = personalizedProgramFingerprint(skeleton.request, skeleton.items)
        fun unchanged(reason: String): RebalancingResult = RebalancingResult(skeleton, DayRebalancingTrace(
            "UNRESOLVED_BALANCE_CONSTRAINT", fingerprint, fingerprint, 0.0, 0.0, emptyList(), emptyList(),
            BalanceObjective(0, 0.0, 0.0), BalanceObjective(0, 0.0, 0.0), emptyList(), reason))
        val week = completed.week ?: return unchanged("POST_PROCESS_SKIPPED_NON_ISOMORPHIC_OR_UNAVAILABLE_COMPLETION")
        if (projection == null) return unchanged("POST_PROCESS_SKIPPED_MISSING_CANONICAL_PROJECTION")
        return try { run(completed, week, snapshot, state, projection) } catch (failure: Exception) {
            if (failure is java.util.concurrent.CancellationException) throw failure
            unchanged("FINAL_REBALANCE_FAILED_SAFE_COMPLETED_PLAN")
        }
    }

    private fun run(completed: CompletionResult, week: RepresentativeWeek, snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState, projection: PlanDayProjection): RebalancingResult {
        val skeleton = completed.skeleton
        val original = week.items
        val primaryKeys = PrimaryStrengthAnchorSpacingPolicy.keys(snapshot,state,
            completed.demand?.authorized.orEmpty().filter { it.continuity }.mapTo(mutableSetOf()) { it.item.stableKey })
        var rows = original
        fun atom(row: ProgramSkeletonItem) = week.atomByLocalId.getValue(row.localId)
        val originalOrder = original.associate { atom(it) to it.orderIndex }
        // Reuse equal day projections within this run, without a second OFI formula or additional future days.
        val loadCache = mutableMapOf<List<ProgramSkeletonItem>, StandaloneDayLoad>()
        fun load(dayRows: List<ProgramSkeletonItem>): StandaloneDayLoad {
            val key = dayRows.sortedBy(::atom).map { it.copy(dayOfWeek = 1, orderIndex = 0) }
            return loadCache.getOrPut(key) { projection.evaluate(dayRows) }
        }
        val referenceDays = week.days.filter { day -> original.filter { it.dayOfWeek == day }.sumOf(::plannedSeconds) > 0 }
        val timeReference = planningMedian(referenceDays.map { day -> original.filter { it.dayOfWeek == day }.sumOf(::plannedSeconds).toDouble() })
        val ofiReference = planningMedian(referenceDays.map { day -> load(original.filter { it.dayOfWeek == day }).ofi.toDouble() })
        require(timeReference > 0 && timeReference.isFinite() && ofiReference.isFinite())
        fun metrics(items: List<ProgramSkeletonItem>) = week.days.map { day ->
            val dayRows = items.filter { it.dayOfWeek == day }
            val seconds = dayRows.sumOf(::plannedSeconds)
            val ofi = load(dayRows).ofi
            BalanceDay(day, seconds, ofi, seconds / timeReference, if (ofiReference > 0) ofi / ofiReference else null)
        }
        fun movable(row: ProgramSkeletonItem): Boolean {
            val source = completed.sourceByAtom[atom(row)] ?: return false
            val session = skeleton.progressionSessions.firstOrNull { it.key == row.progressionBinding?.sessionKey }
            val origin = skeleton.personalizedDecision?.authorizedScheduling?.localOrigins?.get(row.localId)
            if (origin?.splitGroupId?.isNotBlank() == true && skeleton.personalizedDecision?.authorizedScheduling?.authorized.orEmpty()
                    .any { it.id == origin.authorizedDemandId && ContinuitySplitPolicy.mandatory(snapshot,it) }) return false
            if (MainSchedulingPolicy.isMain(row,skeleton)) return MainSchedulingPolicy.ordinaryMain(row,source,skeleton)
            return source.scheduleTier() != ScheduleTier.CORE_MUST_DO && row.progressionRole != ProgressionRole.MAIN &&
                session?.track?.role != ProgressionRole.MAIN && row.progressionVariant.isBlank() &&
                source.styleVariant.isBlank() && !row.requiredTemplateAnchor
        }
        fun cost(row: ProgramSkeletonItem) = if (completed.sourceByAtom.getValue(atom(row)).scheduleTier() == ScheduleTier.OPTIONAL_CAPACITY) 0 else 1
        fun priority(row: ProgramSkeletonItem) = completed.sourceByAtom.getValue(atom(row)).priority
        fun lowerStress(row: ProgramSkeletonItem): Boolean = snapshot.metadata[row.exerciseStableKey]?.let { metadata ->
            snapshot.movementCoverage(row.exerciseStableKey) in setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES) ||
                metadata.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
        } ?: false
        fun maxLower(items: List<ProgramSkeletonItem>) = week.days.maxOf { day -> items.filter { it.dayOfWeek == day && lowerStress(it) }.sumOf(::plannedSeconds) }
        fun order(items: List<ProgramSkeletonItem>, affected: Set<Int>): List<ProgramSkeletonItem> {
            val updated = affected.flatMap { day -> items.filter { it.dayOfWeek == day }
                .sortedWith(compareBy<ProgramSkeletonItem> { originalOrder.getValue(atom(it)) }.thenBy { it.exerciseStableKey }.thenBy(::atom))
                .mapIndexed { index, item -> item.copy(orderIndex = index + 1) } }.associateBy { it.localId }
            // Keep the summation order as well as the item multiset: weighted C/R stay bit-identical.
            return items.map { updated[it.localId] ?: it }
        }
        fun immutable(items: List<ProgramSkeletonItem>) = items.associate { atom(it) to it.copy(dayOfWeek = 1, orderIndex = 0) }
        var currentMetrics = metrics(rows)
        fun strengthPrimary(items: List<ProgramSkeletonItem>) = StrengthPrimaryMainPolicy.objective(items,week.days)
        val primaryCount = original.count { StrengthPrimaryMainPolicy.isPrimary(it.exerciseStableKey) }
        val primaryMinimum = StrengthPrimaryObjective((primaryCount-week.days.size).coerceAtLeast(0),
            (primaryCount+week.days.size-1)/week.days.size)
        val initialMetrics = currentMetrics
        val initialObjective = balanceObjective(initialMetrics)
        val actions = mutableListOf<BalanceAction>()
        val primaryRejections = sortedMapOf<String,Int>()
        val visited = mutableSetOf(rows.associate { atom(it) to it.dayOfWeek })
        val candidateOrder = compareBy<RebalanceCandidate> { strengthPrimary(it.rows) }.thenBy { it.action.afterObjective }.thenBy { it.movementCost }
            .thenBy { it.priority }.thenBy { it.keyOrder }.thenBy { it.action.sourceDay }.thenBy { it.action.destinationDay }.thenBy { it.identityOrder }
        while (currentMetrics.any(BalanceDay::needsBalance) || strengthPrimary(rows) > primaryMinimum) {
            val beforeObjective = balanceObjective(currentMetrics)
            val lowerBefore = maxLower(rows)
            fun evaluate(source: ProgramSkeletonItem, destination: Int, reverse: ProgramSkeletonItem? = null,
                route: String = if (reverse == null) "PRIMARY_MOVE" else "SWAP"): RebalanceCandidate? {
                fun rejected(reason: String): RebalanceCandidate? {
                    if (route.startsWith("STRENGTH_PRIMARY")) primaryRejections[reason] = primaryRejections.getOrDefault(reason,0)+1
                    return null
                }
                if (!movable(source) || reverse?.let { !movable(it) } == true) return rejected("PROTECTED_STRUCTURE_OR_SPLIT")
                val moved = listOfNotNull(source, reverse)
                if (moved.any { !postProcessTissueAllowed(snapshot, state, it.exerciseStableKey) }) return rejected("CURRENT_TISSUE_RESTRICTION")
                val affected = setOf(source.dayOfWeek, destination)
                val tentative = order(rows.map { row -> when (row.localId) {
                    source.localId -> row.copy(dayOfWeek = destination)
                    reverse?.localId -> row.copy(dayOfWeek = source.dayOfWeek)
                    else -> row
                } }, affected)
                for (day in affected) {
                    val items = tentative.filter { it.dayOfWeek == day }
                    if (items.map { it.exerciseStableKey }.distinct().size != items.size) return rejected("SAME_KEY")
                    // The one-way source may remain hard-constrained; both swap destinations must pass.
                    if (day == destination || reverse != null) {
                        if (items.sumOf(::plannedSeconds) > skeleton.request.sessionMinutes * 60) return rejected("SESSION_TIME")
                        if (!load(items).feasible) return rejected("DESTINATION_OFI_OR_AXIS")
                    }
                }
                if (maxLower(tentative) > lowerBefore) return rejected("LOWER_STRESS_CONCENTRATION")
                if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(tentative,primaryKeys)) return rejected("PRIMARY_ANCHOR_CALENDAR_SPACING")
                if (!additionalGate(tentative)) return rejected("ADDITIONAL_PLACEMENT_GATE")
                val nextMetrics = metrics(tentative)
                val beforeAffected = currentMetrics.filter { it.day in affected }
                val afterAffected = nextMetrics.filter { it.day in affected }
                val primaryChange = strengthPrimary(tentative).compareTo(strengthPrimary(rows))
                if (primaryChange > 0) return null
                if (primaryChange == 0 && !balanceNonWorsening(beforeAffected, afterAffected)) return null
                val objective = balanceObjective(nextMetrics)
                if (primaryChange == 0 && objective >= beforeObjective) return null
                check(immutable(tentative) == immutable(rows))
                val action = BalanceAction(if (reverse == null) "MOVE" else "SWAP", moved.map(::atom), moved.map { it.exerciseStableKey },
                    source.dayOfWeek, destination, beforeAffected, afterAffected, beforeObjective, objective, route = route)
                return RebalanceCandidate(tentative, nextMetrics, action, moved.sumOf(::cost), moved.sumOf(::priority),
                    moved.map { it.exerciseStableKey }.sorted().joinToString("|"), moved.map(::atom).sorted().joinToString("|"))
            }
            val sourceDays = currentMetrics.filter(BalanceDay::source).map { it.day }
            val destinationDays = currentMetrics.filter(BalanceDay::destination).map { it.day }
            var best: RebalanceCandidate? = null
            fun consider(candidate: RebalanceCandidate?) {
                if (candidate != null && (best == null || candidateOrder.compare(candidate, best!!) < 0)) best = candidate
            }
            if (strengthPrimary(rows) > primaryMinimum) {
                for (source in rows.filter { StrengthPrimaryMainPolicy.isPrimary(it.exerciseStableKey) }) {
                    for (day in week.days.filter { it != source.dayOfWeek }) {
                        consider(evaluate(source,day,route="STRENGTH_PRIMARY_MOVE"))
                        for (reverse in rows.filter { it.dayOfWeek==day })
                            consider(evaluate(source,day,reverse,"STRENGTH_PRIMARY_SWAP"))
                    }
                }
            }
            val useTimeDestinationFallback = currentMetrics.none { it.timeRatio < LOWER_BALANCE_RATIO } &&
                currentMetrics.any { it.timeRatio > UPPER_BALANCE_RATIO }
            if (useTimeDestinationFallback) {
                val fallbackSources = currentMetrics.filter { it.timeRatio > UPPER_BALANCE_RATIO }
                    .sortedWith(compareByDescending<BalanceDay> { it.timeRatio }.thenBy { it.day })
                val fallbackDestinations = currentMetrics.sortedWith(compareBy<BalanceDay> { it.timeRatio }.thenBy { it.day })
                // First feasible source/destination pair wins; compare every whole atom within that pair.
                fallback@ for (sourceDay in fallbackSources) for (destination in fallbackDestinations) {
                    if (sourceDay.day == destination.day) continue
                    for (source in rows.filter { it.dayOfWeek == sourceDay.day }) consider(evaluate(source, destination.day, route = "TIME_OVERLOAD_FALLBACK"))
                    if (best != null) break@fallback
                }
            } else {
                // Preserve the primary search and comparator, including existing OFI-underloaded destinations.
                for (source in rows.filter { it.dayOfWeek in sourceDays }) for (destination in destinationDays) {
                    if (source.dayOfWeek != destination) consider(evaluate(source, destination))
                }
            }
            if (best == null && currentMetrics.any { it.timeRatio < LOWER_BALANCE_RATIO } &&
                currentMetrics.none { it.timeRatio > UPPER_BALANCE_RATIO }) {
                val underloaded = currentMetrics.filter { it.timeRatio < LOWER_BALANCE_RATIO }
                    .sortedWith(compareBy<BalanceDay> { it.timeRatio }.thenBy { it.day })
                val donors = currentMetrics.filter { day -> rows.any { it.dayOfWeek == day.day && movable(it) } }
                    .sortedWith(compareByDescending<BalanceDay> { it.timeRatio }.thenBy { it.day })
                underload@ for (destination in underloaded) for (donor in donors) {
                    if (donor.day == destination.day) continue
                    for (source in rows.filter { it.dayOfWeek == donor.day })
                        consider(evaluate(source, destination.day, route = "TIME_UNDERLOAD_FALLBACK"))
                    if (best != null) break@underload
                }
            }
            // SWAP still uses only the original source/destination predicates, never fallback destinations.
            if (best == null) {
                for (source in rows.filter { it.dayOfWeek in sourceDays }) for (destination in rows.filter { it.dayOfWeek in destinationDays }) {
                    if (source.dayOfWeek != destination.dayOfWeek) consider(evaluate(source, destination.dayOfWeek, destination))
                }
            }
            val accepted = best ?: break
            check(visited.add(accepted.rows.associate { atom(it) to it.dayOfWeek })) { "REBALANCE_CYCLE" }
            rows = accepted.rows; currentMetrics = accepted.metrics; actions += accepted.action
        }
        check(immutable(original) == immutable(rows))
        completed.demand?.let { demand ->
            check(demand.coverage(original) == demand.coverage(rows))
            check(demand.residuals(original) == demand.residuals(rows))
        }
        val result = week.mirror(skeleton, rows, skeleton.weekDaySchedule)
        require(ProgramProjectionValidator().errors(result, state.genericCourtLoad).isEmpty())
        val finalObjective = balanceObjective(currentMetrics)
        val status = when {
            initialObjective.bandViolationCount == 0 -> "ALREADY_BALANCED"
            finalObjective.bandViolationCount == 0 -> "WITHIN_TARGET_BAND"
            actions.isNotEmpty() -> "IMPROVED_BUT_CONSTRAINED"
            else -> "UNRESOLVED_BALANCE_CONSTRAINT"
        }
        return RebalancingResult(result, DayRebalancingTrace(status, personalizedProgramFingerprint(skeleton.request, skeleton.items),
            personalizedProgramFingerprint(result.request, result.items), timeReference, ofiReference, initialMetrics, currentMetrics,
            initialObjective, finalObjective, actions, if (finalObjective.bandViolationCount > 0) "UNRESOLVED_BALANCE_CONSTRAINT" else "",
            strengthPrimary(original),strengthPrimary(rows),primaryRejections))
    }
}
