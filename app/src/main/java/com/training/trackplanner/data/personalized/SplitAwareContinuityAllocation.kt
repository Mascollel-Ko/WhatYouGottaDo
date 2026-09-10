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
    val localOrigins: Map<String, AuthorizedAtomOrigin> = emptyMap()) {
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
}
internal fun auditSets(sets: List<ProgramSetPrescription>) = JSONArray(sets.map { JSONObject().put("index", it.setIndex)
    .put("reps", it.reps).put("weightKg", it.weightKg).put("seconds", it.seconds) })
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
    private val progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE) {
    fun allocateAuthorized(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, authorized: List<AuthorizedSchedulingDemand>,
        days: Int, minutes: Int, request: ProgramSkeletonRequest): SplitAwareAllocation {
        val result = TimedWeeklyPlacementPlanner().distribute(authorized.map { TimedPlannedExercise(it.item, it.prescription) },
            days, minutes, snapshot, state.trainingStateAssessment?.sustainable?.robustSchedule == true)
        return improve(snapshot, state, authorized, TimedExecutionAllocation(result.first, result.second), days, minutes, request)
    }
    fun allocate(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, continuity: List<PlannedExercise>,
        material: List<PlannedExercise>, optional: List<PlannedExercise>, days: Int, minutes: Int, request: ProgramSkeletonRequest? = null): SplitAwareAllocation {
        val authorized = (continuity + material + optional).mapIndexed { index, item -> AuthorizedSchedulingDemand("authorized_$index", item,
            prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style), index < continuity.size) }
        val baseline = TimedExecutionAllocationPlanner(prescriptions).allocate(snapshot, state, continuity, material, optional, days, minutes)
        return improve(snapshot, state, authorized, baseline, days, minutes, request)
    }

    internal fun improve(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, authorized: List<AuthorizedSchedulingDemand>,
        baseline: TimedExecutionAllocation, days: Int, minutes: Int, request: ProgramSkeletonRequest? = null): SplitAwareAllocation {
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
                state.trainingStateAssessment?.sustainable?.robustSchedule == true)
            if (result.second.isNotEmpty()) return null
            // Equal chunks are distinguished by occurrence, never by stableKey-keyed maps.
            val remaining = atoms.toMutableList()
            val layout = result.first.mapValues { (_, rows) -> rows.map { row ->
                val index = remaining.indexOfFirst { it.timed === row }
                check(index >= 0); remaining.removeAt(index)
            } }
            if (layout.values.any { rows -> snapshot.planDayProjection?.evaluate(rows.mapIndexed { index, row ->
                residualItem(snapshot, row.timed.item, row.timed.prescription, "split_trial_$index", 1, index + 1)
            })?.feasible == false }) return null
            return layout
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
                val result = MandatoryContinuityPlacement(snapshot, state, days, minutes).place(parent, placed, prescriptions)
                placed = result.days
                val materialized = result.days.values.flatten().filter { it.origin.authorizedDemandId == parent.id }.sumOf { it.timed.prescription.sets.size }
                decisions += ContinuitySplitDecision(parent.id, true, ContinuitySplitPolicy.template(parent.prescription.sets.size, days),
                    if (materialized == parent.prescription.sets.size) "CANONICAL_HIGH_SET_PARTITION" else "CANONICAL_PARTITION_HARD_PLACEMENT_SHORTFALL",
                    result.failures, result.ofiWarnings)
                continue
            }
            val current = placed.values.flatten()
            val others = current.filter { it.origin.authorizedDemandId != parent.id }
            val full = trial(others + AuthorizedTimedAtom(TimedPlannedExercise(parent.item, parent.prescription), AuthorizedAtomOrigin(parent.id)))
            val split = trial(others + ContinuitySplitPolicy.chunks(parent, days) { count ->
                prescriptions.prescribe(snapshot, state.strengthIntent, parent.item.copy(targetSets = count), parent.item.style).text
            })
            val chooseSplit = split != null && (full == null || maximum(split) < maximum(full) && maxLower(split) <= maxLower(full))
            // An infeasible split never replaces the existing safe allocation; ties prefer full unsplit.
            placed = when { chooseSplit -> split!!; full != null -> full; else -> placed }
            decisions += ContinuitySplitDecision(parent.id, true, ContinuitySplitPolicy.template(parent.prescription.sets.size),
                when { chooseSplit && full == null -> "SPLIT_FULL_AUTHORIZED_COVERAGE"; chooseSplit -> "SPLIT_LOWER_MAX_DAY_SECONDS"
                    full != null -> "UNSPLIT_PREFERRED"; else -> "EXISTING_SAFE_REDUCTION_OR_DEFER" })
        }
        val totals = placed.values.flatten().groupBy { it.origin.authorizedDemandId }.mapValues { (_, rows) -> rows.sumOf { it.timed.prescription.sets.size } }
        check(authorized.all { (totals[it.id] ?: 0) <= it.prescription.sets.size }) { "SPLIT_AUTHORIZED_SET_INFLATION" }
        val deferred = baseline.deferred.filter { row -> val id = origin(row).authorizedDemandId
            (totals[id] ?: 0) < authorized.single { it.id == id }.prescription.sets.size }
        return SplitAwareAllocation(placed, deferred, AuthorizedSchedulingTrace(authorized, decisions))
    }
}
