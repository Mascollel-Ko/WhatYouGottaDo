package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Engineering release ceiling, not a physiological dose-response relationship. */
internal fun frequencyExpandedTarget(base: Int, algorithmDays: Int, userDays: Int): Int {
    require(base >= 0 && algorithmDays > 0 && userDays >= algorithmDays)
    return (base * (userDays.toDouble() / algorithmDays)).roundToInt()
}

data class FrequencyExpansionAction(val rank: Int, val stableKey: String, val units: Int, val action: String, val reason: String,
    val sourceDay: Int? = null, val destinationDay: Int? = null) {
    fun toJson() = JSONObject().put("originalRank", rank).put("stableKey", stableKey).put("units", units)
        .put("action", action).put("reason", reason).put("sourceDay", sourceDay).put("destinationDay", destinationDay)
}
data class FrequencyUnitOrigin(val localId: String, val setIndex: Int, val authorizedDemandId: String,
    val fundingSource: PlanningFundingSource, val originalRank: Int?) {
    fun toJson() = JSONObject().put("localId", localId).put("setIndex", setIndex).put("authorizedDemandId", authorizedDemandId)
        .put("fundingSource", fundingSource.name).put("originalRank", originalRank)
}
data class FrequencyExpansionTrace(val algorithmRecommendedDays: Int, val userSelectedDays: Int, val baseAuthorizedUnits: Int,
    val mathematicalExpandedTarget: Int, val userDayCapacity: Int, val effectiveExpansionCeiling: Int,
    val capacityRejectedCandidates: List<CapacityCandidateTrace>, val expansionAttempts: List<FrequencyExpansionAction>,
    val initialExpandedUnits: Int, val finalExpandedUnits: Int, val rollbackActions: List<FrequencyExpansionAction>,
    val unitOrigins: List<FrequencyUnitOrigin>, val actualFinalMaterializedUnits: Int,
    val tissueProjection: PlannedTissueWeek?, val dayLoads: List<Pair<Int, StandaloneDayLoad>>, val diagnostic: String) {
    val expansionActivated: Boolean get() = true
    val frequencyRatio: Double get() = userSelectedDays.toDouble() / algorithmRecommendedDays
    fun toJson() = JSONObject().put("algorithmRecommendedDays", algorithmRecommendedDays).put("userSelectedDays", userSelectedDays)
        .put("expansionActivated", expansionActivated).put("frequencyRatio", frequencyRatio).put("baseAuthorizedUnits", baseAuthorizedUnits)
        .put("mathematicalExpandedTarget", mathematicalExpandedTarget).put("requestedExtraUnits", mathematicalExpandedTarget - baseAuthorizedUnits)
        .put("userDayCapacity", userDayCapacity).put("effectiveExpansionCeiling", effectiveExpansionCeiling)
        .put("capacityRejectedCandidates", JSONArray(capacityRejectedCandidates.map { it.toJson() }))
        .put("expansionAttempts", JSONArray(expansionAttempts.map { it.toJson() }))
        .put("initialExpandedUnits", initialExpandedUnits).put("finalExpandedUnits", finalExpandedUnits)
        .put("actualAuthorizedUnits", baseAuthorizedUnits + finalExpandedUnits).put("actualFinalMaterializedUnits", actualFinalMaterializedUnits)
        .put("rollbackActions", JSONArray(rollbackActions.map { it.toJson() })).put("unitOrigins", JSONArray(unitOrigins.map { it.toJson() }))
        .put("tissueProjection", tissueProjection?.toJson()).put("diagnostic", diagnostic)
        .put("days", JSONArray(dayLoads.map { (day, load) -> JSONObject().put("day", day).put("ofi", load.ofi)
            .put("feasible", load.feasible).put("cautionReasons", JSONArray(load.cautionReasons)) }))
}

/** Only the original candidate's existing flexible prescription may release an unfunded portion. */
internal fun frequencyPortion(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, candidate: CapacityCandidateTrace,
    limit: Int, prescriptions: PersonalizedPrescriptionPlanner): PlannedPrescription? {
    val remainder = candidate.prescription.sets.drop(candidate.fundedBaseUnits)
    if (remainder.isEmpty() || limit <= 0) return null
    if (candidate.fundedBaseUnits == 0 && remainder.size <= limit) return candidate.prescription
    if (snapshot.activityKind(candidate.item.stableKey) != PlannedActivityKind.RESISTANCE || candidate.item.styleVariant.isNotBlank() ||
        candidate.item.style !in setOf(StrengthProgrammingStyle.NONE, StrengthProgrammingStyle.STRAIGHT_5X5, StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS) ||
        candidate.prescription.sets.map { it.copy(setIndex = 0) }.distinct().size != 1) return null
    for (count in minOf(limit, remainder.size) downTo 1) {
        val rx = prescriptions.prescribe(snapshot, state.strengthIntent, candidate.item.copy(targetSets = count), candidate.item.style)
        if (rx.sets.size == count && rx.restSeconds == candidate.prescription.restSeconds && rx.weightSource == candidate.prescription.weightSource &&
            rx.sets.map { it.copy(setIndex = 0) } == remainder.take(count).map { it.copy(setIndex = 0) }) return rx
    }
    return null
}

internal class FrequencyExpansionPlanner(private val prescriptions: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()) {
    fun expand(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, request: ProgramSkeletonRequest,
        base: GeneratedProgramSkeleton, frequency: PlanningFrequencyProvenance,
        place: (List<AuthorizedSchedulingDemand>, WeeklyCapacityEnvelope) -> CompletionResult): GeneratedProgramSkeleton {
        require(frequency.explicitIncrease)
        val baseDecision = requireNotNull(base.personalizedDecision)
        val provenance = requireNotNull(baseDecision.frequencyDemand)
        val baseDemand = provenance.baseAuthorized
        val b = baseDemand.sumOf { it.prescription.sets.size }
        val queue = provenance.expansionSupply
        val target = frequencyExpandedTarget(b, frequency.algorithmRecommendedDays, frequency.resolvedUserDays)
        val budget = requireNotNull(baseDecision.planningBudget)
        val capacity = ExecutionCapacityPlanner().envelope(snapshot, state, request, budget.baselineResistanceSets,
            b + queue.sumOf { it.remainingUnits }, budget.systemicDoseFactor)
        val ceiling = minOf(target, capacity.finalControllableUnits, b + queue.sumOf { it.remainingUnits })
        val attempts = mutableListOf<FrequencyExpansionAction>()
        val rollbacks = mutableListOf<FrequencyExpansionAction>()
        val expansion = mutableListOf<AuthorizedSchedulingDemand>()
        for (candidate in queue) {
            val key = candidate.item.stableKey
            val equipment = snapshot.exercises[key]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
            val eligible = key in snapshot.exercises && key !in request.excludedExerciseStableKeys && !snapshot.explicitlyRestricted(key) &&
                snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") &&
                postProcessTissueAllowed(snapshot, state, key) &&
                (request.availableEquipment.isEmpty() || equipment.all { it == "BODYWEIGHT" || it in request.availableEquipment })
            val remaining = ceiling - b - expansion.sumOf { it.prescription.sets.size }
            val rx = if (eligible) frequencyPortion(snapshot, state, candidate, remaining, prescriptions) else null
            val reason = when {
                !eligible -> "OTHER_EXISTING_HARD_GATE"
                remaining <= 0 -> if (capacity.finalControllableUnits <= target) "USER_DAY_CAPACITY_LIMIT" else "EXPANSION_CEILING"
                rx == null -> "PRESCRIPTION_ATOMICITY"
                else -> "${candidate.rejectionReason.name}_RECOVERED"
            }
            attempts += FrequencyExpansionAction(candidate.originalRank, key, rx?.sets?.size ?: 0, if (rx == null) "REJECTED" else "AUTHORIZED", reason)
            if (rx != null) expansion += AuthorizedSchedulingDemand("frequency_${candidate.originalRank}", candidate.item.copy(targetSets = rx.sets.size),
                rx, candidate.continuity, PlanningFundingSource.USER_FREQUENCY_EXPANSION, candidate.originalRank, candidate.rejectionReason)
        }
        val initialExtra = expansion.sumOf { it.prescription.sets.size }
        var diagnostic = when {
            queue.isEmpty() -> "NO_CAPACITY_REJECTED_USEFUL_DEMAND"
            ceiling <= b -> "USER_DAY_CAPACITY_LIMIT"
            else -> "EXPANSION_FEASIBLE"
        }
        var result: GeneratedProgramSkeleton
        var finalOrigins: List<FrequencyUnitOrigin> = emptyList()
        var finalTissue: PlannedTissueWeek? = null
        var finalLoads: List<Pair<Int, StandaloneDayLoad>> = emptyList()
        val tissueCache = mutableMapOf<Pair<List<ProgramSkeletonItem>, Double>, PlannedTissueWeek>()
        val loadCache = mutableMapOf<List<ProgramSkeletonItem>, StandaloneDayLoad>()
        // Every failed pass removes at least one expansion unit; no recursive repair.
        while (true) {
            val authorized = baseDemand + expansion
            var completion = place(authorized, capacity)
            val targetRpe = completion.skeleton.weekPlans.firstOrNull { it.weekIndex == 1 }?.targetRpeMax ?: Double.NaN
            fun tissue(rows: List<ProgramSkeletonItem>) = snapshot.planWeekTissueProjection?.let { projection ->
                val key = rows.sortedWith(compareBy({ it.dayOfWeek }, { it.exerciseStableKey }, { it.localId })).map { it.copy(orderIndex = 0) } to targetRpe
                tissueCache.getOrPut(key) { projection.evaluate(rows, targetRpe) }
            }
            fun loads(rows: List<ProgramSkeletonItem>) = rows.groupBy { it.dayOfWeek }.toSortedMap().mapNotNull { (day, items) ->
                snapshot.planDayProjection?.let { projection ->
                    val key = items.sortedBy { it.localId }.map { it.copy(dayOfWeek = 1, orderIndex = 0) }
                    day to loadCache.getOrPut(key) { projection.evaluate(items) }
                } }
            fun failure(rows: List<ProgramSkeletonItem>): String? {
                if (rows.groupBy { it.dayOfWeek }.any { (_, items) -> items.sumOf(::plannedSeconds) > request.sessionMinutes * 60 ||
                        items.map { it.exerciseStableKey }.distinct().size != items.size }) return "SESSION_TIME_OR_COLLISION"
                if (snapshot.planDayProjection == null || loads(rows).any { !it.second.feasible }) return "OFI_CONSTRAINT"
                if (tissue(rows)?.feasible != true) return "TISSUE_RECOVERY_CONSTRAINT"
                return null
            }
            var week = completion.week
            var rows = week?.items ?: completion.skeleton.items.filter { it.weekNumber == 1 }
            fun origins(items: List<ProgramSkeletonItem>) = frequencyUnitOrigins(items, completion, authorized)
            var origin = origins(rows)
            // Only wholly expansion-owned flexible atoms may move. MAIN and structured anchors remain protected.
            if (week != null && origin != null && failure(rows) != null) {
                val originalRows = rows
                val seen = mutableSetOf(rows.map { it.localId to it.dayOfWeek })
                var moved = true
                while (moved && failure(rows) != null) {
                    moved = false
                    for (row in rows) {
                        val units = origin.orEmpty().filter { it.localId == row.localId }
                        val source = completion.sourceByAtom[week.atomByLocalId[row.localId] ?: row.localId] ?: continue
                        val session = completion.skeleton.progressionSessions.firstOrNull { it.key == row.progressionBinding?.sessionKey }
                        if (units.isEmpty() || units.any { it.fundingSource != PlanningFundingSource.USER_FREQUENCY_EXPANSION } ||
                            source.scheduleTier() == ScheduleTier.CORE_MUST_DO || row.progressionRole == ProgressionRole.MAIN ||
                            session?.track?.role == ProgressionRole.MAIN || row.progressionVariant.isNotBlank() || source.styleVariant.isNotBlank() || row.requiredTemplateAnchor) continue
                        for (day in week.days.filter { it != row.dayOfWeek }) {
                            val trial = rows.map { if (it.localId == row.localId) it.copy(dayOfWeek = day) else it }
                                .groupBy { it.dayOfWeek }.values.flatMap { items -> items.sortedWith(compareBy({ it.orderIndex }, { it.localId }))
                                    .mapIndexed { index, item -> item.copy(orderIndex = index + 1) } }
                            if (!seen.add(trial.map { it.localId to it.dayOfWeek })) continue
                            val rejected = failure(trial) ?: if (origins(trial) == null) "PRESCRIPTION_INTEGRITY" else null
                            attempts += FrequencyExpansionAction(units.first().originalRank ?: 0, row.exerciseStableKey, units.size,
                                if (rejected == null) "RELOCATED" else "RELOCATION_REJECTED", rejected ?: "ALL_CANONICAL_GATES_PASS", row.dayOfWeek, day)
                            if (rejected == null) { rows = trial; moved = true; break }
                        }
                        if (moved) break
                    }
                }
                check(originalRows.associate { it.localId to it.copy(dayOfWeek = 1, orderIndex = 0) } ==
                    rows.associate { it.localId to it.copy(dayOfWeek = 1, orderIndex = 0) })
                completion = completion.copy(week = week.copy(items = rows), skeleton = week.mirror(completion.skeleton, rows, completion.skeleton.weekDaySchedule))
            }
            val rebalanced = BoundedDayRebalancer { trial -> failure(trial) == null && origins(trial) != null }
                .rebalance(completion, snapshot, state, snapshot.planDayProjection)
            result = rebalanced.skeleton.copy(personalizedDecision = rebalanced.skeleton.personalizedDecision?.copy(dayRebalancing = rebalanced.trace))
            rows = result.items.filter { it.weekNumber == 1 }
            origin = origins(rows)
            val placedByOwner = origin.orEmpty().groupingBy { it.authorizedDemandId }.eachCount()
            val exact = origin != null && authorized.all { placedByOwner[it.id] == it.prescription.sets.size }
            val rejected = if (!exact) "BASE_OR_EXPANSION_EXACT_SHORTFALL" else failure(rows)
            if (rejected == null || expansion.isEmpty()) {
                if (!exact || rows.sumOf { it.setPrescriptions.size } < provenance.actualMaterializedUnits) {
                    // Independent BASE shortfalls are not repaired by deleting BASE to make expansion appear feasible.
                    // Preserve the original completed BASE content if fresh placement could not retain it.
                    val userSchedule = RecordBasedReviewedPolicy.defaultSchedule(request.durationWeeks, request.weeklyTrainingDays)
                    val retainedSchedule = base.weekDaySchedule.mapValues { (weekNumber, days) ->
                        days + userSchedule.getValue(weekNumber).filter { it !in days }.take((request.weeklyTrainingDays - days.size).coerceAtLeast(0))
                    }
                    result = base.copy(request = request, weekDaySchedule = retainedSchedule,
                        personalizedDecision = baseDecision.copy(weeklyFrequency = request.weeklyTrainingDays))
                    rows = base.items.filter { it.weekNumber == 1 }
                    diagnostic = "BASE_PRESERVED_USER_PLACEMENT_CONSTRAINED"
                    finalOrigins = frequencyUnitOrigins(rows, completion, baseDemand).orEmpty()
                } else {
                    finalOrigins = origin.orEmpty()
                    if (rejected != null) diagnostic = "BASE_INDEPENDENT_$rejected"
                }
                finalTissue = tissue(rows); finalLoads = loads(rows)
                break
            }
            diagnostic = rejected
            val last = expansion.last()
            val candidate = queue.first { it.originalRank == last.originalRank }
            val reduced = frequencyPortion(snapshot, state, candidate, last.prescription.sets.size - 1, prescriptions)
            expansion.removeAt(expansion.lastIndex)
            if (reduced != null) expansion += last.copy(item = last.item.copy(targetSets = reduced.sets.size), prescription = reduced)
            rollbacks += FrequencyExpansionAction(requireNotNull(last.originalRank), last.item.stableKey,
                last.prescription.sets.size - (reduced?.sets?.size ?: 0), if (reduced == null) "DROPPED" else "REDUCED", rejected)
            check(expansion.sumOf { it.prescription.sets.size } < authorized.drop(baseDemand.size).sumOf { it.prescription.sets.size })
        }
        val finalRows = result.items.filter { it.weekNumber == 1 }
        val actual = finalRows.sumOf { it.setPrescriptions.size }
        check(actual >= provenance.actualMaterializedUnits) { "EXPANSION_MUST_NOT_REDUCE_BASE" }
        val trace = FrequencyExpansionTrace(frequency.algorithmRecommendedDays, frequency.resolvedUserDays, b, target,
            capacity.finalControllableUnits, ceiling, queue, attempts, initialExtra, expansion.sumOf { it.prescription.sets.size },
            rollbacks, finalOrigins, actual, finalTissue, finalLoads, diagnostic)
        val decision = requireNotNull(result.personalizedDecision)
        fun count(kind: PlannedActivityKind) = finalRows.filter { snapshot.activityKind(it.exerciseStableKey) == kind }.sumOf { it.setPrescriptions.size }
        return result.copy(personalizedDecision = decision.copy(frequencyDemand = provenance.copy(actualMaterializedUnits = actual,
            expansionSelectedKeys = expansion.mapTo(linkedSetOf()) { it.item.stableKey }),
            frequencyExpansion = trace, originalGenerationFingerprint = personalizedProgramFingerprint(result.request, result.items),
            planningBudget = decision.planningBudget?.let { it.copy(plannedResistanceSets = count(PlannedActivityKind.RESISTANCE),
                plannedStructuredBadmintonBouts = count(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL),
                plannedAthleticPerformanceBouts = count(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL),
                execution = it.execution?.copy(capacity = capacity)) }))
    }
}

/** Match exact typed ownership and set contents, never display strings or stableKey fragments. BASE wins identical units. */
internal fun frequencyUnitOrigins(rows: List<ProgramSkeletonItem>, completed: CompletionResult,
    authorized: List<AuthorizedSchedulingDemand>): List<FrequencyUnitOrigin>? {
    val remaining = authorized.associate { it.id to it.prescription.sets.map { set -> set.copy(setIndex = 0) }.toMutableList() }
    val result = mutableListOf<FrequencyUnitOrigin>()
    val restoredParents = completed.trace.restorations.flatMap { action -> action.rows.map { it.localId to action.authorizedDemandId } }.toMap()
    for (row in rows.sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))) {
        val atom = completed.week?.atomByLocalId?.get(row.localId) ?: row.localId
        val source = completed.sourceByAtom[atom]
        val explicitParent = restoredParents[atom] ?: restoredParents[row.localId] ?:
            completed.skeleton.personalizedDecision?.authorizedScheduling?.origins?.get(atom)?.authorizedDemandId
        for (set in row.setPrescriptions) {
            val parent = authorized.firstOrNull { (explicitParent == null || it.id == explicitParent) &&
                it.item.stableKey == row.exerciseStableKey && it.item.styleVariant == row.progressionVariant &&
                (source == null || (it.item.representedGapCodes == source.representedGapCodes && it.item.representedObjectives == source.representedObjectives &&
                    it.item.supportiveObjectives == source.supportiveObjectives)) && it.prescription.restSeconds == row.restSeconds &&
                it.prescription.weightSource == row.weightSource && set.copy(setIndex = 0) in remaining.getValue(it.id) } ?: return null
            remaining.getValue(parent.id).remove(set.copy(setIndex = 0))
            result += FrequencyUnitOrigin(row.localId, set.setIndex, parent.id, parent.fundingSource, parent.originalRank)
        }
    }
    return result
}
