package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

data class AuthorizedAtomOrigin(val authorizedDemandId: String, val splitGroupId: String = "", val splitChunkIndex: Int? = null)
data class AuthorizedSchedulingDemand(val id: String, val item: PlannedExercise, val prescription: PlannedPrescription, val continuity: Boolean,
    val fundingSource: PlanningFundingSource = PlanningFundingSource.BASE, val originalRank: Int? = null,
    val sourceReason: CandidateRejectionReason? = null)
data class ContinuitySplitDecision(val authorizedDemandId: String, val eligible: Boolean, val template: List<Int>, val decision: String,
    val failureReasons: Set<SplitPlacementFailure> = emptySet(), val ofiWarnings: List<SplitOfiWarning> = emptyList())
data class AuthorizedSchedulingTrace(val authorized: List<AuthorizedSchedulingDemand>, val decisions: List<ContinuitySplitDecision>,
    val origins: Map<String, AuthorizedAtomOrigin> = emptyMap(), val initialWeek: List<ProgramSkeletonItem> = emptyList(),
    val localOrigins: Map<String, AuthorizedAtomOrigin> = emptyMap(),
    val ownerAllocationProvenance: List<OwnerAllocationProvenance> = emptyList()) {
    fun toJson() = JSONObject().put("demandBoundary", "AUTHORIZED_POST_CAPACITY_PRE_PLACEMENT_DEMAND")
        .put("authorized", JSONArray(authorized.map { demand -> JSONObject().put("authorizedDemandId", demand.id)
            .put("stableKey", demand.item.stableKey).put("continuity", demand.continuity)
            .put("fundingSource", demand.fundingSource.name).put("originalRank", demand.originalRank)
            .put("sourceReason", demand.sourceReason?.name)
            .put("gapCodes", JSONArray(demand.item.representedGapCodes.toList())).put("style", demand.item.style.name)
            .put("variant", demand.item.styleVariant).put("sets", demand.prescription.sets.size)
            .put("prescription", demand.prescription.text).put("prescriptionSource", demand.prescription.weightSource)
            .put("restSeconds", demand.prescription.restSeconds).put("setPrescriptions", auditSets(demand.prescription.sets)) }))
        .put("decisions", JSONArray(decisions.map { JSONObject().put("authorizedDemandId", it.authorizedDemandId)
            .put("splitCapable", it.eligible).put("template", JSONArray(it.template)).put("decision", it.decision)
            .put("failureReasons", JSONArray(it.failureReasons.map { reason -> reason.name }))
            .put("ofiPolicy", if (it.ofiWarnings.isEmpty()) "NO_OVERRIDE_NEEDED" else "ADVISORY_AUTHORIZED_HIGH_SET_SPLIT")
            .put("ofiWarnings", JSONArray(it.ofiWarnings.map { rejection -> JSONObject().put("chunkIndex", rejection.chunkIndex)
                .put("sets", rejection.sets).put("day", rejection.day).put("ofi", rejection.load.ofi)
                .put("axisScores", JSONArray(rejection.load.axisScores)).put("cautionReasons", JSONArray(rejection.load.cautionReasons)) })) }))
        .put("origins", JSONObject().apply { origins.forEach { (atom, origin) -> put(atom, JSONObject()
            .put("authorizedDemandId", origin.authorizedDemandId).put("splitGroupId", origin.splitGroupId).put("splitChunkIndex", origin.splitChunkIndex)) } })
        .put("initialWeek", JSONArray(initialWeek.map(::auditPlannedItem)))
        .put("localOrigins", JSONObject().apply { localOrigins.forEach { (id, origin) -> put(id, JSONObject()
            .put("authorizedDemandId", origin.authorizedDemandId).put("splitGroupId", origin.splitGroupId).put("splitChunkIndex", origin.splitChunkIndex)) } })
        .put("ownerAllocationProvenance", JSONArray(ownerAllocationProvenance.deterministicOwnerOrder().map { it.toJson() }))
}
internal fun auditSets(sets: List<ProgramSetPrescription>) = JSONArray(sets.map { JSONObject().put("index", it.setIndex)
    .put("reps", it.reps).put("weightKg", it.weightKg).put("seconds", it.seconds)
    .put("targetRpeMin", it.targetRpeMin) })
internal fun auditPlannedItem(item: ProgramSkeletonItem) = JSONObject().put("localId", item.localId).put("stableKey", item.exerciseStableKey)
    .put("name", item.exerciseName).put("day", item.dayOfWeek).put("sets", item.setCount).put("prescription", item.prescription)
    .put("prescriptionSource", item.weightSource).put("restSeconds", item.restSeconds).put("seconds", plannedSeconds(item))
    .put("setPrescriptions", auditSets(item.setPrescriptions))

internal object ContinuitySplitPolicy {
    fun template(count: Int, days: Int = 3): List<Int> = when (count) {
        4 -> listOf(2, 2); 5 -> listOf(3, 2); 6 -> listOf(3, 3); 7 -> listOf(3, 4); 8 -> listOf(4, 4)
        9 -> if (days == 2) listOf(4, 5) else listOf(3, 3, 3)
        else -> listOf(count)
    }
    fun mandatory(snapshot: PlanningHistorySnapshot, demand: AuthorizedSchedulingDemand) =
        eligible(snapshot, demand) && demand.prescription.sets.size in 6..9
    fun eligible(snapshot: PlanningHistorySnapshot, demand: AuthorizedSchedulingDemand): Boolean = demand.continuity &&
        snapshot.activityKind(demand.item.stableKey) == PlannedActivityKind.RESISTANCE && demand.prescription.sets.size in 4..9 &&
        demand.item.style in setOf(StrengthProgrammingStyle.NONE, StrengthProgrammingStyle.STRAIGHT_5X5, StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS) &&
        demand.item.styleVariant.isBlank() && demand.prescription.sets.map { it.copy(setIndex = 0) }.distinct().size == 1

    fun chunks(demand: AuthorizedSchedulingDemand, days: Int = 3, textForCount: (Int) -> String = { demand.prescription.text }): List<AuthorizedTimedAtom> {
        var offset = 0
        return template(demand.prescription.sets.size, days).mapIndexed { index, count ->
            val sets = demand.prescription.sets.subList(offset, offset + count).mapIndexed { local, set -> set.copy(setIndex = local + 1) }
            offset += count
            AuthorizedTimedAtom(TimedPlannedExercise(demand.item.copy(targetSets = count), demand.prescription.copy(text = textForCount(count), sets = sets)),
                AuthorizedAtomOrigin(demand.id, demand.id, index))
        }
    }
}
internal data class AuthorizedTimedAtom(val timed: TimedPlannedExercise, val origin: AuthorizedAtomOrigin)
internal data class SplitAwareAllocation(val days: Map<Int, List<AuthorizedTimedAtom>>, val deferred: List<TimedPlannedExercise>,
    val trace: AuthorizedSchedulingTrace)

/** Wrapper around the unchanged finite/timed allocator. No prescription is re-authored by splitting. */
internal class SplitAwareContinuityAllocation(private val prescriptions: PersonalizedPrescriptionPlanner,
    private val progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
    private val placementContext: PlacementContext? = null,
    private val performanceMetrics: PlannerPerformanceMetrics? = null) {
    private fun context(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, days: Int, minutes: Int): PlacementContext =
        placementContext ?: PlacementContext(snapshot, state, days, minutes)

    private fun placementProvenanceSink(
        authorized: List<AuthorizedSchedulingDemand>,
        events: MutableList<OwnerAllocationProvenance>
    ): (WeeklyOwnerPlacementMutation) -> Unit = { mutation ->
        val item = mutation.timed.item
        val demand = authorized.firstOrNull { it.item === item }
            ?: authorized.singleOrNull { it.item.stableKey == item.stableKey && it.item.role == item.role &&
                it.item.styleVariant == item.styleVariant && it.item.representedGapCodes == item.representedGapCodes }
        if (demand != null) {
            val before = ownerAllocationState(null, mutation.beforeDay, mutation.beforeOrder,
                demand.prescription.sets.size, demand.prescription.sets, demand.prescription.text, item.role)
            val after = mutation.afterDay?.let { ownerAllocationState(null, it, mutation.afterOrder,
                demand.prescription.sets.size, demand.prescription.sets, demand.prescription.text, item.role) }
            val action = when {
                after == null -> OwnerAllocationAction.REMOVED
                mutation.beforeDay == null -> OwnerAllocationAction.PLACEMENT_ASSIGNED
                mutation.beforeDay != mutation.afterDay -> OwnerAllocationAction.PLACEMENT_MOVED
                else -> OwnerAllocationAction.ORDER_CHANGED
            }
            events += OwnerAllocationProvenance(StimulusPrescriptionOwnerIdentity(item.stableKey, item.role),
                mutation.stage, action, before, after, mutation.cause,
                authorizedDemandIds = setOf(demand.id), evidenceCodes = listOf("ACCEPTED_WEEKLY_PLACEMENT"),
                mutationSequence = events.size)
        }
    }

    fun allocateAuthorized(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, authorized: List<AuthorizedSchedulingDemand>,
        days: Int, minutes: Int, request: ProgramSkeletonRequest): SplitAwareAllocation {
        val placement = context(snapshot, state, days, minutes)
        val ownerProvenance = mutableListOf<OwnerAllocationProvenance>()
        val capture = placementProvenanceSink(authorized, ownerProvenance)
        val result = TimedWeeklyPlacementPlanner().distribute(authorized.map { TimedPlannedExercise(it.item, it.prescription) },
            days, minutes, snapshot, state.trainingStateAssessment?.sustainable?.robustSchedule == true,
            isMain = { item -> MainSchedulingPolicy.role(item, authorized.any { it.item == item && it.continuity }) == com.training.trackplanner.data.ProgressionRole.MAIN },
            planningState = state, context = placement, metrics = performanceMetrics, ownerMutationSink = capture)
        return improve(snapshot, state, authorized, TimedExecutionAllocation(result.first, result.second), days, minutes, request,
            ownerProvenance)
    }
    fun allocate(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, continuity: List<PlannedExercise>,
        material: List<PlannedExercise>, optional: List<PlannedExercise>, days: Int, minutes: Int, request: ProgramSkeletonRequest? = null): SplitAwareAllocation {
        val authorized = (continuity + material + optional).mapIndexed { index, item -> AuthorizedSchedulingDemand("authorized_$index", item,
            prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style), index < continuity.size) }
        val placementProvenance = mutableListOf<OwnerAllocationProvenance>()
        val baseline = TimedExecutionAllocationPlanner(prescriptions, context(snapshot, state, days, minutes), performanceMetrics)
            .allocate(snapshot, state, continuity, material, optional, days, minutes,
                placementProvenanceSink(authorized, placementProvenance))
        return improve(snapshot, state, authorized, baseline, days, minutes, request,
            baseline.ownerAllocationProvenance + placementProvenance)
    }

    internal fun improve(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, authorized: List<AuthorizedSchedulingDemand>,
        baseline: TimedExecutionAllocation, days: Int, minutes: Int, request: ProgramSkeletonRequest? = null,
        ownerProvenance: List<OwnerAllocationProvenance> = emptyList()): SplitAwareAllocation {
        val ownerProvenanceEvents = ownerProvenance.toMutableList()
        progress.report(PersonalizedPlannerStage.DISTRIBUTION)
        fun origin(row: TimedPlannedExercise): AuthorizedAtomOrigin {
            val parent = authorized.firstOrNull { it.item === row.item }
                ?: authorized.single { it.item.copy(targetSets = row.item.targetSets) == row.item }
            return AuthorizedAtomOrigin(parent.id)
        }
        var placed = baseline.days.mapValues { (_, rows) -> rows.map { AuthorizedTimedAtom(it, origin(it)) } }
        val decisions = mutableListOf<ContinuitySplitDecision>()
        fun lower(row: AuthorizedTimedAtom) = snapshot.movementCoverage(row.timed.item.stableKey) in
            setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES) ||
            snapshot.metadata[row.timed.item.stableKey]?.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
        fun maximum(layout: Map<Int, List<AuthorizedTimedAtom>>) = layout.values.maxOfOrNull { it.sumOf { row -> row.timed.estimatedSeconds } } ?: 0
        fun maxLower(layout: Map<Int, List<AuthorizedTimedAtom>>) = layout.values.maxOfOrNull { it.filter(::lower).sumOf { row -> row.timed.estimatedSeconds } } ?: 0
        fun trial(atoms: List<AuthorizedTimedAtom>): Map<Int, List<AuthorizedTimedAtom>>? {
            val result = TimedWeeklyPlacementPlanner().distribute(atoms.map { it.timed }, days, minutes, snapshot,
                state.trainingStateAssessment?.sustainable?.robustSchedule == true,
                isMain = { item -> MainSchedulingPolicy.role(item, authorized.any { it.item == item && it.continuity }) == com.training.trackplanner.data.ProgressionRole.MAIN },
                planningState = state, context = context(snapshot, state, days, minutes), metrics = performanceMetrics)
            if (result.second.isNotEmpty()) return null
            // Equal chunks are distinguished by occurrence, never by stableKey-keyed maps.
            val remaining = atoms.toMutableList()
            val layout = result.first.mapValues { (_, rows) -> rows.map { row ->
                val index = remaining.indexOfFirst { it.timed === row }
                check(index >= 0); remaining.removeAt(index)
            } }
            val actualDays = RecordBasedReviewedPolicy.defaultSchedule(1, days).getValue(1).sorted()
            val primaryKeys = PrimaryStrengthAnchorSpacingPolicy.keys(snapshot, state,
                authorized.filter { it.continuity }.mapTo(mutableSetOf()) { it.item.stableKey })
            if (primaryKeys.any { key -> !PrimaryStrengthAnchorSpacingPolicy.allowed(layout.entries.flatMap { (day, rows) ->
                rows.filter { it.timed.item.stableKey == key }.map { actualDays[day - 1] }
            }) }) return null
            if (layout.values.any { rows -> snapshot.planDayProjection?.let { projection ->
                performanceMetrics?.let { it.dayProjectionCalls++ }
                projection.evaluate(rows.mapIndexed { index, row ->
                    residualItem(snapshot, row.timed.item, row.timed.prescription, "split_trial_$index", 1, index + 1)
                })
            }?.feasible == false }) return null
            return layout
        }
        data class PlacedRow(val day: Int, val order: Int, val atom: AuthorizedTimedAtom)
        fun acceptedLayoutProvenance(
            before: Map<Int, List<AuthorizedTimedAtom>>,
            after: Map<Int, List<AuthorizedTimedAtom>>,
            stage: OwnerAllocationStage,
            cause: OwnerAllocationCause
        ): List<OwnerAllocationProvenance> {
            fun rowsByDemand(layout: Map<Int, List<AuthorizedTimedAtom>>) = layout.flatMap { (day, rows) ->
                rows.mapIndexed { index, atom -> PlacedRow(day, index + 1, atom) }
            }.groupBy { it.atom.origin.authorizedDemandId }
            fun mergedState(demand: AuthorizedSchedulingDemand, rows: List<PlacedRow>, week: Int? = null): OwnerAllocationState {
                val sets = rows.sortedWith(compareBy({ it.atom.origin.splitChunkIndex ?: -1 }, { it.order }))
                    .flatMap { it.atom.timed.prescription.sets }.mapIndexed { index, set -> set.copy(setIndex = index + 1) }
                return ownerAllocationState(week, null, null, sets.size, sets,
                    rows.sortedBy { it.order }.joinToString(" | ") { it.atom.timed.prescription.text }.ifBlank { demand.prescription.text },
                    demand.item.role)
            }
            fun rowState(row: PlacedRow): OwnerAllocationState = ownerAllocationState(null, row.day, row.order,
                row.atom.timed.prescription.sets.size, row.atom.timed.prescription.sets,
                row.atom.timed.prescription.text, row.atom.timed.item.role)
            val oldByDemand = rowsByDemand(before)
            val newByDemand = rowsByDemand(after)
            val byId = authorized.associateBy { it.id }
            val events = mutableListOf<OwnerAllocationProvenance>()
            (oldByDemand.keys + newByDemand.keys).distinct().sorted().forEach { demandId ->
                val demand = byId[demandId] ?: return@forEach
                val oldRows = oldByDemand[demandId].orEmpty().sortedWith(compareBy({ it.day }, { it.order }))
                val newRows = newByDemand[demandId].orEmpty().sortedWith(compareBy({ it.day }, { it.order }))
                if (oldRows.map { it.day to (it.order to it.atom.timed.prescription) } ==
                    newRows.map { it.day to (it.order to it.atom.timed.prescription) }) return@forEach
                val identity = StimulusPrescriptionOwnerIdentity(demand.item.stableKey, demand.item.role)
                val oldState = mergedState(demand, oldRows)
                val newState = mergedState(demand, newRows)
                if (oldRows.isEmpty() && newRows.isNotEmpty()) events += OwnerAllocationProvenance(identity, stage,
                    OwnerAllocationAction.ADDED, null, newState, cause, authorizedDemandIds = setOf(demandId),
                    evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"), mutationSequence = events.size)
                else if (newRows.isEmpty() && oldRows.isNotEmpty()) events += OwnerAllocationProvenance(identity, stage,
                    OwnerAllocationAction.REMOVED, oldState, null, cause, authorizedDemandIds = setOf(demandId),
                    evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"), mutationSequence = events.size)
                else if (oldState.setCount != newState.setCount) events += OwnerAllocationProvenance(identity, stage,
                    if (newState.setCount < oldState.setCount) OwnerAllocationAction.SET_COUNT_REDUCED
                    else OwnerAllocationAction.SET_COUNT_EXPANDED, oldState, newState, cause,
                    authorizedDemandIds = setOf(demandId), evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"),
                    mutationSequence = events.size)
                else if (oldState.setPrescriptions != newState.setPrescriptions || oldState.prescription != newState.prescription) {
                    events += OwnerAllocationProvenance(identity, stage, OwnerAllocationAction.PRESCRIPTION_CHANGED,
                        oldState, newState, cause, authorizedDemandIds = setOf(demandId),
                        evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"), mutationSequence = events.size)
                }
                val unmatchedOld = oldRows.toMutableList()
                newRows.forEach { next ->
                    val match = unmatchedOld.indexOfFirst { old ->
                        old.atom.timed.item.styleVariant == next.atom.timed.item.styleVariant &&
                            old.atom.origin.splitChunkIndex == next.atom.origin.splitChunkIndex &&
                            old.atom.timed.prescription.sets == next.atom.timed.prescription.sets
                    }
                    if (match >= 0) {
                        val old = unmatchedOld.removeAt(match)
                        if (old.day != next.day || old.order != next.order) events += OwnerAllocationProvenance(identity, stage,
                            if (old.day != next.day) OwnerAllocationAction.PLACEMENT_MOVED else OwnerAllocationAction.ORDER_CHANGED,
                            rowState(old), rowState(next), cause, authorizedDemandIds = setOf(demandId),
                            evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"), mutationSequence = events.size)
                    } else events += OwnerAllocationProvenance(identity, stage, OwnerAllocationAction.PLACEMENT_ASSIGNED,
                        oldState.takeIf { oldRows.isNotEmpty() }, rowState(next), cause,
                        authorizedDemandIds = setOf(demandId), evidenceCodes = listOf("ACCEPTED_SPLIT_AWARE_OWNER_LAYOUT"),
                        mutationSequence = events.size)
                }
            }
            return events
        }
        for (parent in authorized.filter { it.continuity }.sortedWith(compareByDescending<AuthorizedSchedulingDemand> { it.item.priority }.thenBy { it.id })) {
            val eligible = ContinuitySplitPolicy.eligible(snapshot, parent)
            val equipment = snapshot.exercises[parent.item.stableKey]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
            val permitted = parent.item.stableKey !in request?.excludedExerciseStableKeys.orEmpty() &&
                snapshot.metadata[parent.item.stableKey]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") &&
                (request?.availableEquipment.isNullOrEmpty() || equipment.all { it == "BODYWEIGHT" || it in request!!.availableEquipment })
            if (!eligible || !permitted || days < 2 || !postProcessTissueAllowed(snapshot, state, parent.item.stableKey) || snapshot.explicitlyRestricted(parent.item.stableKey)) {
                decisions += ContinuitySplitDecision(parent.id, eligible, ContinuitySplitPolicy.template(parent.prescription.sets.size, days),
                    "UNSPLIT_INELIGIBLE_OR_RESTRICTED")
                continue
            }
            if (ContinuitySplitPolicy.mandatory(snapshot, parent)) {
                val beforePlacement = placed
                val result = MandatoryContinuityPlacement(snapshot, state, days, minutes, performanceMetrics)
                    .place(parent, placed, prescriptions)
                ownerProvenanceEvents += acceptedLayoutProvenance(beforePlacement, result.days,
                    OwnerAllocationStage.MANDATORY_CONTINUITY_PLACEMENT, OwnerAllocationCause.MANDATORY_CONTINUITY)
                placed = result.days
                val materialized = result.days.values.flatten().filter { it.origin.authorizedDemandId == parent.id }.sumOf { it.timed.prescription.sets.size }
                decisions += ContinuitySplitDecision(parent.id, true, ContinuitySplitPolicy.template(parent.prescription.sets.size, days),
                    if (materialized == parent.prescription.sets.size) "CANONICAL_HIGH_SET_PARTITION" else "CANONICAL_PARTITION_HARD_PLACEMENT_SHORTFALL",
                    result.failures, result.ofiWarnings)
                continue
            }
            performanceMetrics?.let { it.conditionalSplitParents++ }
            val current = placed.values.flatten()
            val others = current.filter { it.origin.authorizedDemandId != parent.id }
            val full = trial(others + AuthorizedTimedAtom(TimedPlannedExercise(parent.item, parent.prescription), AuthorizedAtomOrigin(parent.id)))
            performanceMetrics?.let { it.fullTrials++ }
            // Four/five-set parents are split only when the unsplit placement is
            // infeasible or leaves a day unused while concentrating the whole
            // parent. This is the explicit need gate; the canonical split still
            // runs through the same OFI/tissue checks when requested.
            val needsSplit = full == null || full.values.count { it.isNotEmpty() } < minOf(days, 2)
            val split = if (needsSplit) {
                performanceMetrics?.let { it.splitTrials++ }
                trial(others + ContinuitySplitPolicy.chunks(parent, days) { count ->
                    prescriptions.prescribe(snapshot, state.strengthIntent, parent.item.copy(targetSets = count), parent.item.style).text
                })
            } else null
            val chooseSplit = split != null && (full == null || maximum(split) < maximum(full) && maxLower(split) <= maxLower(full))
            // An infeasible split never replaces the existing safe allocation; ties prefer full unsplit.
            val acceptedLayout = when { chooseSplit -> split!!; full != null -> full; else -> placed }
            if (acceptedLayout !== placed) ownerProvenanceEvents += acceptedLayoutProvenance(placed, acceptedLayout,
                OwnerAllocationStage.SPLIT_AWARE_CONTINUITY_ALLOCATION, OwnerAllocationCause.OWNER_PRIORITY)
            placed = acceptedLayout
            decisions += ContinuitySplitDecision(parent.id, true, ContinuitySplitPolicy.template(parent.prescription.sets.size),
                when { chooseSplit && full == null -> "SPLIT_FULL_AUTHORIZED_COVERAGE"; chooseSplit -> "SPLIT_LOWER_MAX_DAY_SECONDS"
                    full != null -> "UNSPLIT_PREFERRED"; else -> "EXISTING_SAFE_REDUCTION_OR_DEFER" })
        }
        val totals = placed.values.flatten().groupBy { it.origin.authorizedDemandId }.mapValues { (_, rows) -> rows.sumOf { it.timed.prescription.sets.size } }
        check(authorized.all { (totals[it.id] ?: 0) <= it.prescription.sets.size }) { "SPLIT_AUTHORIZED_SET_INFLATION" }
        val deferred = baseline.deferred.filter { row -> val id = origin(row).authorizedDemandId
            (totals[id] ?: 0) < authorized.single { it.id == id }.prescription.sets.size }
        return SplitAwareAllocation(placed, deferred, AuthorizedSchedulingTrace(authorized, decisions,
            ownerAllocationProvenance = ownerProvenanceEvents.deterministicOwnerOrder()))
    }
}
