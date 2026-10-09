package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Scheduling currency only: units from different domains are not equivalent stimuli. */
data class WeeklyCapacityEnvelope(
    val resolvedWeeklyDays: Int,
    val requestedSessionMinutes: Int,
    val availableWeeklySeconds: Int,
    val recentControllableTrainingDays: Int,
    val historicalSessionObservationCount: Int,
    val historicalSessionUnitMedian: Double,
    val historicalSessionUnitUpperTypical: Double,
    val historicalSessionSecondsMedian: Double,
    val historicalControllableUnits: Double,
    val recoveryConstraint: Double,
    val courtInterferenceContext: Double,
    val usefulDemandUnits: Int,
    val scheduleFeasibleUnits: Int,
    val finalControllableUnits: Int,
    val domainBudget: DomainVolumeBudget = DomainVolumeBudget()
)

data class ExecutionAllocationTrace(
    val capacity: WeeklyCapacityEnvelope,
    val continuityRequestedUnits: Int,
    val continuityAllocatedUnits: Int,
    val materialGapRequestedUnits: Int,
    val materialGapAllocatedUnits: Int,
    val selectedMaterialGaps: List<String>,
    val representedMaterialGaps: List<String>,
    val deferredMaterialGaps: Map<String, String>,
    val optionalDevelopmentalItems: List<String>,
    val candidateAudit: Map<String, String>,
    val representedGapCodesByStableKey: Map<String, Set<String>>,
    val prescriptionSources: Map<String, String>,
    val supportiveGapCodesByStableKey: Map<String, Set<String>> = emptyMap(),
    val scheduleTiers: Map<String, ScheduleTier> = emptyMap(),
    /** Stable keys whose requested builder demand was constrained before final materialization. */
    val constrainedOwnerStableKeys: Set<String> = emptySet(),
    /** Owner/role-local causal events emitted by the mutation stages and carried to B7. */
    val ownerAllocationProvenance: List<OwnerAllocationProvenance> = emptyList(),
    /** Empty unless a source allocator explicitly identified both sides of displacement. */
    val ownerDisplacementEdges: List<OwnerDisplacementEdge> = emptyList(),
    /** Candidate-origin evidence is diagnostic and never grants prescription authority. */
    val materialDemandCandidateOrigins: List<MaterialDemandCandidateOrigin> = emptyList(),
    val unresolvedMaterialDemandGaps: Set<String> = emptySet(),
    /** Exact pre-kernel order and funding; diagnostic only and never consumed as policy. */
    val finiteAllocationPriorityOrder: List<FiniteAllocationPriorityRow> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("capacity", JSONObject()
            .put("resolvedWeeklyDays", capacity.resolvedWeeklyDays)
            .put("sessionMinutes", capacity.requestedSessionMinutes)
            .put("availableWeeklySeconds", capacity.availableWeeklySeconds)
            .put("recentControllableTrainingDays", capacity.recentControllableTrainingDays)
            .put("historicalSessionObservationCount", capacity.historicalSessionObservationCount)
            .put("historicalSessionUnitMedian", capacity.historicalSessionUnitMedian)
            .put("historicalSessionUnitUpperTypical", capacity.historicalSessionUnitUpperTypical)
            .put("historicalSessionSecondsMedian", capacity.historicalSessionSecondsMedian)
            .put("historicalControllableUnits", capacity.historicalControllableUnits)
            .put("recoveryConstraint", capacity.recoveryConstraint)
            .put("courtInterferenceContext", capacity.courtInterferenceContext)
            .put("usefulDemandUnits", capacity.usefulDemandUnits)
            .put("scheduleFeasibleUnits", capacity.scheduleFeasibleUnits)
            .put("finalControllableUnits", capacity.finalControllableUnits)
            .put("domainBudget", JSONObject()
                .put("resistance", JSONObject()
                    .put("normalWeekCount", capacity.domainBudget.resistance.resistanceNormalWeekCount)
                    .put("activeWeekCount", capacity.domainBudget.resistance.resistanceActiveWeekCount)
                    .put("q25", capacity.domainBudget.resistance.resistanceWeeklyQ25)
                    .put("median", capacity.domainBudget.resistance.resistanceWeeklyMedian)
                    .put("q75", capacity.domainBudget.resistance.resistanceWeeklyQ75)
                    .put("baselineSource", capacity.domainBudget.resistance.resistanceBaselineSource)
                    .put("baselineSets", capacity.domainBudget.resistance.resistanceBaselineSets)
                    .put("coreTarget", capacity.domainBudget.resistance.resistanceCoreTarget)
                    .put("dayRelease", capacity.domainBudget.resistance.resistanceDayRelease)
                    .put("timeCeiling", capacity.domainBudget.resistance.resistanceTimeCeiling)
                    .put("usefulDemand", capacity.domainBudget.resistance.resistanceUsefulDemand)
                    .put("targetSets", capacity.domainBudget.resistance.resistanceTargetSets)
                    .put("authorizedBeforeCompletion", capacity.domainBudget.resistance.resistanceAuthorizedBeforeCompletion)
                    .put("completionAddedSets", capacity.domainBudget.resistance.resistanceCompletionAddedSets)
                    .put("finalSets", capacity.domainBudget.resistance.resistanceFinalSets))
                .put("structuredBadmintonBouts", capacity.domainBudget.structuredBadminton.targetBouts)
                .put("athleticPerformanceBouts", capacity.domainBudget.athleticPerformance.targetBouts)))
        .put("continuityRequestedUnits", continuityRequestedUnits)
        .put("continuityAllocatedUnits", continuityAllocatedUnits)
        .put("materialGapRequestedUnits", materialGapRequestedUnits)
        .put("materialGapAllocatedUnits", materialGapAllocatedUnits)
        .put("selectedMaterialGaps", JSONArray(selectedMaterialGaps))
        .put("representedMaterialGaps", JSONArray(representedMaterialGaps))
        .put("deferredMaterialGaps", JSONObject(deferredMaterialGaps))
        .put("optionalDevelopmentalItems", JSONArray(optionalDevelopmentalItems))
        .put("candidateAudit", JSONObject(candidateAudit))
        .put("representedGapCodesByStableKey", JSONObject().apply {
            representedGapCodesByStableKey.forEach { (key, codes) -> put(key, JSONArray(codes.toList())) }
        })
        .put("prescriptionSources", JSONObject(prescriptionSources))
        .put("scheduleTiers",JSONObject(scheduleTiers.mapValues { it.value.name }))
        .put("supportiveGapCodesByStableKey", JSONObject().apply {
            supportiveGapCodesByStableKey.forEach { (key, codes) -> put(key, JSONArray(codes.toList())) }
        })
        .put("constrainedOwnerStableKeys", JSONArray(constrainedOwnerStableKeys.sorted()))
        .put("ownerAllocationProvenance", JSONArray(ownerAllocationProvenance.deterministicOwnerOrder().map { it.toJson() }))
        .put("ownerDisplacementEdges", JSONArray(ownerDisplacementEdges.sortedWith(compareBy(
            { it.causeOwner.stableKey }, { it.causeOwner.selectionRole }, { it.displacedOwner.stableKey },
            { it.displacedOwner.selectionRole }, { it.stage.ordinal }, { it.reason.ordinal }, { it.week ?: 0 }, { it.displacedUnits }
        )).map { it.toJson() }))
        .put("materialDemandCandidateOrigins", JSONArray(materialDemandCandidateOrigins
            .sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.gapCodes.sorted().joinToString("|") }))
            .map { it.toJson() }))
        .put("finiteAllocationPriorityOrder", JSONArray(finiteAllocationPriorityOrder.map { it.toJson() }))
        .put("unresolvedMaterialDemandGaps", JSONArray(unresolvedMaterialDemandGaps.sorted()))
}

data class FiniteAllocationPriorityRow(
    val owner: StimulusPrescriptionOwnerIdentity,
    val priority: Int,
    val resistance: Boolean,
    val styleVariant: String,
    val requestedUnits: Int,
    val fundedUnits: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put("stableKey", owner.stableKey)
        .put("selectionRole", owner.selectionRole)
        .put("priority", priority)
        .put("resistance", resistance)
        .put("styleVariant", styleVariant)
        .put("requestedUnits", requestedUnits)
        .put("fundedUnits", fundedUnits)
}


data class TimedPlannedExercise(val item: PlannedExercise, val prescription: PlannedPrescription) {
    val estimatedSeconds: Int get() = prescription.sets.sumOf { if (it.seconds > 0) it.seconds else 45 } +
        (prescription.sets.size - 1).coerceAtLeast(0) * prescription.restSeconds
}

data class TimedExecutionAllocation(
    val days: Map<Int, List<TimedPlannedExercise>>,
    val deferred: List<TimedPlannedExercise>,
    val ownerAllocationProvenance: List<OwnerAllocationProvenance> = emptyList()
)

/** Funds executable material work before discretionary continuity, using exact prescriptions.
 * The placement trial is a feasibility check; repair does not choose the block's priorities.
 */
internal class TimedExecutionAllocationPlanner(
    private val prescriptions: PersonalizedPrescriptionPlanner,
    private val placementContext: PlacementContext? = null,
    private val performanceMetrics: PlannerPerformanceMetrics? = null,
) {
    fun allocate(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, continuity: List<PlannedExercise>,
                 material: List<PlannedExercise>, optional: List<PlannedExercise>, days: Int, minutes: Int,
                 ownerMutationSink: ((WeeklyOwnerPlacementMutation) -> Unit)? = null): TimedExecutionAllocation {
        val funded = mutableListOf<PlannedExercise>()
        val deferred = mutableListOf<TimedPlannedExercise>()
        val context = placementContext ?: PlacementContext(snapshot, state, days, minutes)
        val timedCache = mutableMapOf<PlannedExercise, TimedPlannedExercise>()
        fun timed(item: PlannedExercise): TimedPlannedExercise = timedCache.getOrPut(item) {
            TimedPlannedExercise(item, prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style))
        }
        fun fits(items: List<PlannedExercise>): Boolean {
            performanceMetrics?.let { it.fundingFitsTrials++ }
            return TimedWeeklyPlacementPlanner().distributeGreedy(items.map(::timed), days, minutes, snapshot,
                state.trainingStateAssessment?.sustainable?.robustSchedule == true, context = context, metrics = performanceMetrics).second.isEmpty()
        }
        // One core set per retained resistance anchor; style variants are rebuilt below.
        val cores = continuity.filter { it.transition != null }.groupBy(PlannedExercise::stableKey).values.map { variants ->
            variants.first().copy(targetSets = 1)
        }
        cores.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }).forEach {
            if (fits(funded + it)) funded += it
        }
        material.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }).forEach { item ->
            if (fits(funded + item)) funded += item else deferred += timed(item)
        }
        // Restore desired continuity a set at a time so a long item cannot be discarded whole.
        continuity.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }.thenBy { it.styleVariant }).forEach { desired ->
            var index = funded.indexOfFirst { it.stableKey == desired.stableKey && it.styleVariant == desired.styleVariant && it.representedGapCodes.isEmpty() }
            var count = if (index >= 0) funded[index].targetSets else 0
            while (count < desired.targetSets) {
                val candidate = desired.copy(targetSets = count + 1)
                val trial = funded.toMutableList().apply { if (index >= 0) set(index, candidate) else add(candidate) }
                if (!fits(trial)) break
                if (index >= 0) funded[index] = candidate else { funded += candidate; index = funded.lastIndex }
                count++
            }
            if (count == 0) deferred += timed(desired)
        }
        optional.forEach { if (fits(funded + it)) funded += it else deferred += timed(it) }
        val placement = TimedWeeklyPlacementPlanner().distribute(funded.map(::timed), days, minutes, snapshot,
            state.trainingStateAssessment?.sustainable?.robustSchedule == true,
            isMain = { MainSchedulingPolicy.role(it, it in continuity) == com.training.trackplanner.data.ProgressionRole.MAIN },
            planningState = state, context = context, metrics = performanceMetrics,
            ownerMutationSink = ownerMutationSink)
        check(placement.second.isEmpty())
        val fundedByDemand = funded.associateBy { Triple(it.stableKey, it.role, it.styleVariant) }
        val ownerEvents = (continuity + material + optional).mapNotNull { requested ->
            val accepted = fundedByDemand[Triple(requested.stableKey, requested.role, requested.styleVariant)]
            if (accepted?.targetSets == requested.targetSets) return@mapNotNull null
            val beforeRx = timed(requested).prescription
            val afterRx = accepted?.let(::timed)?.prescription
            val owner = StimulusPrescriptionOwnerIdentity(requested.stableKey, requested.role)
            OwnerAllocationProvenance(owner, OwnerAllocationStage.TIMED_EXECUTION_ALLOCATION,
                when {
                    accepted == null -> OwnerAllocationAction.REMOVED
                    accepted.targetSets < requested.targetSets -> OwnerAllocationAction.SET_COUNT_REDUCED
                    else -> OwnerAllocationAction.SET_COUNT_EXPANDED
                },
                ownerAllocationState(null, null, null, requested.targetSets, beforeRx.sets, beforeRx.text, requested.role),
                accepted?.let { ownerAllocationState(null, null, null, it.targetSets, requireNotNull(afterRx).sets,
                    afterRx.text, it.role) },
                OwnerAllocationCause.CAPACITY_LIMIT,
                evidenceCodes = listOf("TIMED_ALLOCATOR_ACCEPTED_FUNDING"))
        }.deterministicOwnerOrder()
        return TimedExecutionAllocation(placement.first, deferred, ownerEvents)
    }
}

data class MaterialDemand(
    val candidates: List<PlannedExercise>,
    val deferred: Map<String, String>,
    val audit: Map<String, String>,
    val ownerAllocationProvenance: List<OwnerAllocationProvenance> = emptyList(),
    /** Why a candidate was proposed, kept separate from accepted executable mutations. */
    val candidateOrigins: List<MaterialDemandCandidateOrigin> = emptyList(),
    /** Complete finite candidate lists let the builder retry only exact-authorized alternatives. */
    val candidateAlternatives: List<MaterialDemandCandidateAlternative> = emptyList(),
    /** Need codes stay visible when every candidate is rejected for missing execution authority. */
    val unresolvedGapCodes: Set<String> = emptySet()
)

/** Candidate need/source only; this deliberately carries no dose or execution authorization. */
data class MaterialDemandCandidateOrigin(
    val owner: StimulusPrescriptionOwnerIdentity,
    val gapCodes: Set<String>,
    val evidenceCodes: Set<String> = setOf("MATERIAL_DEMAND_SELECTED"),
    val authorityResolution: ExecutionAuthorityResolution? = null
) {
    fun toJson() = JSONObject()
        .put("owner", JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole))
        .put("gapCodes", JSONArray(gapCodes.sorted()))
        .put("evidenceCodes", JSONArray(evidenceCodes.distinct().sorted()))
        .put("authorityResolution", authorityResolution?.let { resolution ->
            JSONObject()
                .put("status", resolution.status.name)
                .put("reason", resolution.reason.name)
                .put("returnTarget", resolution.returnTarget.name)
                .put("originalOwner", resolution.originalOwner?.let {
                    JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                })
                .put("attemptedOwners", JSONArray(resolution.attemptedOwners.map {
                    JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                }))
                .put("finalOwner", resolution.finalOwner?.let {
                    JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                })
        })
}

/** A selector proposal for one need; it contains no executable prescription. */
data class MaterialDemandCandidateAlternative(
    val gapCodes: Set<String>,
    val candidate: PlannedExercise
)

class MaterialDemandResolver {
    fun resolve(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        gaps: List<AdaptationGap>,
        request: ProgramSkeletonRequest,
        includeLegacyComparatorCandidateUnits: Boolean = false
    ): MaterialDemand {
        val anchors = state.anchors.mapTo(mutableSetOf(), UserAnchor::stableKey)
        val alternatives = GapCandidateSelector().select(
            snapshot, state, gaps, anchors, allAlternatives = true,
            includePrescriptionDemand = includeLegacyComparatorCandidateUnits
        )
        val audit = linkedMapOf<String, String>()
        val feasible = alternatives.filter { item ->
            val exercise = snapshot.exercises.getValue(item.stableKey)
            val equipment = exercise.equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
            val reason = when {
                item.stableKey in request.excludedExerciseStableKeys -> "USER_EXCLUDED"
                item.stableKey in snapshot.recoverySignals.tissueRestrictedStableKeys -> "TISSUE_RESTRICTED"
                request.availableEquipment.isNotEmpty() && equipment.any { it !in request.availableEquipment && it != "BODYWEIGHT" } -> "EQUIPMENT_INCOMPATIBLE"
                else -> null
            }
            audit[item.stableKey] = reason ?: "FEASIBLE_ALTERNATIVE"
            reason == null
        }
        val selected = linkedMapOf<String, PlannedExercise>()
        val candidateOrigins = mutableListOf<MaterialDemandCandidateOrigin>()
        val candidateAlternatives = mutableListOf<MaterialDemandCandidateAlternative>()
        val represented = mutableSetOf<String>()
        val selectedQualities = mutableSetOf<String>()
        val deferred = linkedMapOf<String, String>()
        val unresolvedGapCodes = linkedSetOf<String>()
        fun quality(item: PlannedExercise): String {
            val metadata = snapshot.metadata[item.stableKey]
            return listOf(snapshot.activityKind(item.stableKey), metadata?.redundancyGroup?.takeIf(String::isNotBlank)
                ?: metadata?.movementFamily.orEmpty()).joinToString(":")
        }
        fun coveredGaps(choice: PlannedExercise, gap: AdaptationGap): Set<String> = gaps.filter { other ->
            other.contributesTransitionPressure == gap.contributesTransitionPressure &&
                (other.code == gap.code || objectiveFromGap(other.code) in choice.representedObjectives ||
                    objectiveFromGap(other.code) in choice.supportiveObjectives)
        }.mapTo(linkedSetOf(), AdaptationGap::code)
        gaps.sortedWith(compareByDescending<AdaptationGap> { when(it.priority) { "HIGH" -> 3; "MEDIUM", "MODERATE" -> 2; else -> 1 } }
            .thenBy { !it.contributesTransitionPressure }.thenBy { it.code }).forEach { gap ->
            if (gap.code in represented && !gap.code.endsWith("FOUNDATIONAL_ONRAMP")) return@forEach
            // Candidate selection may rank identities, but its legacy targetSets field is not
            // dose authority. A selected row receives set demand only after an exact execution
            // authority is found downstream.
            val pool = feasible.filter { gap.code in it.representedGapCodes }.map { candidate ->
                if (includeLegacyComparatorCandidateUnits) candidate
                else candidate.copy(targetSets = 0)
            }
            // A foundational block can contain distinct canonical objective qualities.
            // Explicit supportive work is eligible; direct candidates lead when feasible.
            val remaining = pool.sortedWith(compareBy<PlannedExercise> {
                objectiveFromGap(gap.code).let { objective -> objective.isNotBlank() && objective !in it.representedObjectives }
            }.thenBy { quality(it) in selectedQualities }
                .thenBy { it.representedObjectives.size }
                .thenByDescending { item -> snapshot.allConfirmedSets.any { it.stableKey == item.stableKey } }
                .thenBy { it.stableKey })
            remaining.forEach { alternative ->
                candidateAlternatives += MaterialDemandCandidateAlternative(
                    gapCodes = coveredGaps(alternative, gap),
                    candidate = alternative.copy(representedGapCodes = coveredGaps(alternative, gap))
                )
            }
            val choices = if (gap.code == "RESISTANCE_FOUNDATIONAL_ONRAMP") remaining
                else if (gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP") {
                    val objectives = mutableSetOf<String>()
                    remaining.filter { item ->
                        val contributes = item.representedObjectives.any { it !in objectives }
                        if (contributes) objectives += item.representedObjectives
                        contributes
                    }
                } else remaining.take(1)
            if (choices.isEmpty()) {
                deferred[gap.code] = "NO_FEASIBLE_PRESCRIPTION_OR_CANDIDATE"
                // Keep the need visible even when candidate selection itself has no result.
                // A missing candidate must not make the underlying material gap disappear.
                unresolvedGapCodes += gap.code
            }
            choices.forEach { choice ->
                val covered = coveredGaps(choice, gap)
                val existing = selected[choice.stableKey]
                val owner = existing?.takeIf { it.material && !choice.material } ?: choice
                val resolvedOwner = owner.copy(representedGapCodes = covered + existing?.representedGapCodes.orEmpty())
                val ownerIdentity = StimulusPrescriptionOwnerIdentity(resolvedOwner.stableKey, resolvedOwner.role)
                candidateOrigins += MaterialDemandCandidateOrigin(
                    owner = ownerIdentity,
                    gapCodes = covered + existing?.representedGapCodes.orEmpty()
                )
                selected[choice.stableKey] = resolvedOwner
                // Supportive exposure remains useful demand, but cannot close another DIRECT gap.
                represented += covered.filter { it == gap.code || objectiveFromGap(it) in choice.representedObjectives }
                selectedQualities += quality(choice)
                audit[choice.stableKey] = if (objectiveFromGap(gap.code) in choice.supportiveObjectives &&
                    objectiveFromGap(gap.code) !in choice.representedObjectives) "SELECTED_SUPPORTIVE_DEMAND" else "SELECTED_MATERIAL_DEMAND"
            }
        }
        snapshot.exercises.keys.filter { snapshot.activityKind(it) in setOf(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL) ||
            snapshot.badmintonSupportiveObjectives[it].orEmpty().isNotEmpty() }.forEach { key ->
            val objectives = snapshot.badmintonDirectObjectives[key].orEmpty() + snapshot.badmintonSupportiveObjectives[key].orEmpty()
            val matches = gaps.any { objectiveFromGap(it.code) in objectives || it.code == "BADMINTON_FOUNDATIONAL_ONRAMP" }
            audit.putIfAbsent(key, when {
                key in request.excludedExerciseStableKeys -> "USER_EXCLUDED"
                snapshot.explicitlyRestricted(key) -> "EXPLICIT_PROFILE_RESTRICTION"
                key in snapshot.recoverySignals.tissueRestrictedStableKeys -> "TISSUE_RESTRICTED"
                !matches -> "NO_MATCHING_CURRENT_OBJECTIVE_DEMAND"
                else -> "REDUNDANT_OR_INELIGIBLE_CANDIDATE"
            })
        }
        return MaterialDemand(
            candidates = selected.values.toList(),
            deferred = deferred,
            audit = audit,
            candidateOrigins = candidateOrigins.distinct().sortedWith(compareBy(
                { it.owner.stableKey }, { it.owner.selectionRole }, { it.gapCodes.sorted().joinToString("|") }
            )),
            candidateAlternatives = candidateAlternatives.distinct(),
            unresolvedGapCodes = unresolvedGapCodes
        )
    }

    private fun objectiveFromGap(code: String): String = listOf("BADMINTON_DROP_", "BADMINTON_UNDERREPRESENTED_", "BADMINTON_DEVELOP_")
        .firstOrNull(code::startsWith)?.let(code::removePrefix).orEmpty()
}

/** Pure finite allocation kernel shared by all activity domains; independently golden-tested. */
data class FiniteAllocation(
    val continuity: Int,
    val material: List<Int>,
    val deferred: List<Int>,
    val ownerAllocationProvenance: List<OwnerAllocationProvenance> = emptyList()
)
object FiniteExecutionAllocator {
    fun allocate(capacity: Int, continuityDemand: Int, minimums: List<Int>, share: Double, coreReserve: Int,
                 flexible: Set<Int> = minimums.indices.toSet(),
                 ownerIdentities: List<StimulusPrescriptionOwnerIdentity> = emptyList()): FiniteAllocation {
        require(ownerIdentities.isEmpty() || ownerIdentities.size == minimums.size)
        val reserve = minOf(coreReserve, continuityDemand, capacity)
        val selected = mutableListOf<Int>()
        var spent = 0
        minimums.forEachIndexed { index, minimum ->
            if (minimum > 0 && spent + minimum <= capacity - reserve) { selected += index; spent += minimum }
        }
        // Fund meaningful material units first; an indivisible prescription is never rounded down.
        val material = minimums.indices.map { if (it in selected) minimums[it] else 0 }.toMutableList()
        val gapTarget = maxOf(spent, (capacity * share).roundToInt()).coerceAtMost(capacity - reserve)
        val expandable = selected.filter { it in flexible }
        var cursor = 0
        while (material.sum() < gapTarget && expandable.isNotEmpty()) {
            material[expandable[cursor++ % expandable.size]]++
        }
        val continuity = minOf(continuityDemand, (capacity - material.sum()).coerceAtLeast(0))
        val ownerEvents = if (ownerIdentities.isEmpty()) emptyList() else minimums.mapIndexedNotNull { index, requested ->
            val allocated = material[index]
            if (allocated == requested) return@mapIndexedNotNull null
            val owner = ownerIdentities[index]
            OwnerAllocationProvenance(owner, OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION,
                when {
                    allocated == 0 -> OwnerAllocationAction.REMOVED
                    allocated < requested -> OwnerAllocationAction.SET_COUNT_REDUCED
                    else -> OwnerAllocationAction.SET_COUNT_EXPANDED
                },
                ownerAllocationState(null, null, null, requested, emptyList(), null, owner.selectionRole),
                allocated.takeIf { it > 0 }?.let { ownerAllocationState(null, null, null, it, emptyList(), null, owner.selectionRole) },
                if (allocated > requested) OwnerAllocationCause.CAPACITY_SHARE_ALLOCATION else OwnerAllocationCause.CAPACITY_LIMIT,
                evidenceCodes = listOf(if (allocated > requested) "FINITE_ALLOCATOR_SHARED_UNIT_ALLOCATION" else "FINITE_ALLOCATOR_CAPACITY_BOUNDARY"))
        }.deterministicOwnerOrder()
        return FiniteAllocation(continuity, material, minimums.indices.filter { material[it] == 0 }, ownerEvents)
    }
}

class ExecutionCapacityPlanner {
    fun envelope(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, request: ProgramSkeletonRequest,
                 baseline: Double, usefulDemand: Int, doseFactor: Double,
                 domains: DomainVolumeBudget? = null): WeeklyCapacityEnvelope {
        val assessment = state.trainingStateAssessment
        val excludedWeeks = assessment?.weeklyContext.orEmpty().filter { it.excludedFromTolerance }
        val recent = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(55)) && !it.date.isAfter(snapshot.cutoff) &&
                snapshot.activityKind(it.stableKey) in setOf(PlannedActivityKind.RESISTANCE, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL) &&
                excludedWeeks.none { week -> it.date in week.start..week.end }
        }
        val sessions = recent.groupBy(PlanningSetRecord::date).values
        val counts = sessions.map { it.size.toDouble() }.sorted()
        val seconds = sessions.map { rows -> rows.groupBy(PlanningSetRecord::stableKey).values.sumOf { group ->
            group.sumOf { if (it.seconds > 0) it.seconds else 45 } +
                (group.size - 1).coerceAtLeast(0) * (snapshot.exercises[group.first().stableKey]?.defaultRestSeconds ?: 60)
        }.toDouble() }.sorted()
        fun quantile(values: List<Double>, q: Double): Double = if (values.isEmpty()) 0.0 else values[((values.size - 1) * q).roundToInt()]
        val median = quantile(counts, .5)
        val typical = if (sessions.size >= 4) quantile(counts, .75) else median
        val medianSeconds = quantile(seconds, .5)
        val observedWeeks = recent.map { it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.distinct().size.coerceAtLeast(1)
        val historical = recent.size.toDouble() / observedWeeks
        val historicalDays = sessions.size.toDouble() / observedWeeks
        // Extra availability releases deferred useful demand, never invents new demand.
        val dayScale = if (historicalDays > 0) maxOf(1.0, request.weeklyTrainingDays / historicalDays) else 1.0
        val timeScale = if (medianSeconds > 0) maxOf(1.0, request.sessionMinutes * 60.0 / medianSeconds) else 1.0
        val demonstrated = maxOf(baseline, historical)
        val sustainable = assessment?.sustainable
        val release = assessment?.permitsSustainableRelease == true &&
            request.weeklyTrainingDays >= (sustainable?.sustainableDaysPerWeek ?: Double.MAX_VALUE) &&
            request.weeklyTrainingDays * request.sessionMinutes >= (sustainable?.sustainableWeeklyMinutes ?: Double.MAX_VALUE)
        val densityBound = if (release) requireNotNull(sustainable?.sustainableWeeklyControllableUnits).toInt()
            else if (sessions.isEmpty()) usefulDemand else ceil(demonstrated * dayScale * timeScale).toInt()
        val available = request.weeklyTrainingDays * request.sessionMinutes * 60
        val scheduleUnits = (available / maxOf(45.0, if (median > 0) medianSeconds / median else 135.0)).toInt()
        // Exactly one global soft/hard factor, after availability and useful demand bounds.
        val final = (minOf(usefulDemand, densityBound, scheduleUnits) * doseFactor).roundToInt().coerceAtLeast(0)
        val domainBudget = domains ?: DomainVolumeBudget(
            resistance = ResistanceVolumePlanner.plan(snapshot, state, request, usefulDemand, baseline)
        )
        return WeeklyCapacityEnvelope(request.weeklyTrainingDays, request.sessionMinutes, available, sessions.size,
            sessions.size, median, typical, medianSeconds, historical, 1.0 - doseFactor, state.courtInterference,
            usefulDemand, scheduleUnits, final, domainBudget)
    }
}

/** Prescriptions precede placement. No item-count or generic-court-count capacity rule. */
internal fun placementSessionFits(currentSeconds: Int, incomingSeconds: Int, sessionMinutes: Int): Boolean =
    currentSeconds + incomingSeconds <= sessionMinutes * 60

internal class TimedWeeklyPlacementPlanner {
    fun distribute(
        items: List<TimedPlannedExercise>,
        days: Int,
        sessionMinutes: Int,
        snapshot: PlanningHistorySnapshot? = null,
        robustSchedule: Boolean = false,
        isMain: (PlannedExercise) -> Boolean = { false },
        planningState: AthletePlanningState? = null,
        context: PlacementContext? = null,
        metrics: PlannerPerformanceMetrics? = null,
        ownerMutationSink: ((WeeklyOwnerPlacementMutation) -> Unit)? = null,
    ): Pair<Map<Int, List<TimedPlannedExercise>>, List<TimedPlannedExercise>> {
        val greedy = distributeGreedy(items, days, sessionMinutes, snapshot, robustSchedule, context, metrics,
            ownerMutationSink)
        val reviewed = InitialMainPlacement.review(
            greedy.first,
            sessionMinutes,
            snapshot,
            robustSchedule,
            planningState,
            isMain = isMain,
            context = context,
            performanceMetrics = metrics,
        )
        if (ownerMutationSink != null) {
            val before = java.util.IdentityHashMap<TimedPlannedExercise, Pair<Int, Int>>()
            greedy.first.forEach { (day, rows) -> rows.forEachIndexed { index, row -> before[row] = day to index + 1 } }
            reviewed.forEach { (day, rows) -> rows.forEachIndexed { index, row ->
                val old = before[row] ?: return@forEachIndexed
                val next = day to index + 1
                if (old != next) ownerMutationSink(WeeklyOwnerPlacementMutation(row, old.first, old.second,
                    next.first, next.second, OwnerAllocationStage.INITIAL_MAIN_PLACEMENT,
                    OwnerAllocationCause.INITIAL_MAIN_PLACEMENT_OBJECTIVE))
            } }
        }
        return reviewed to greedy.second
    }

    /**
     * Greedy placement is the exact funding feasibility kernel. It has no
     * canonical projection or post-authorization review, so funding trials can
     * reuse this cheap structural pass without rebuilding a whole-week review.
     */
    internal fun distributeGreedy(
        items: List<TimedPlannedExercise>,
        days: Int,
        sessionMinutes: Int,
        snapshot: PlanningHistorySnapshot? = null,
        robustSchedule: Boolean = false,
        context: PlacementContext? = null,
        metrics: PlannerPerformanceMetrics? = null,
        ownerMutationSink: ((WeeklyOwnerPlacementMutation) -> Unit)? = null,
    ): Pair<Map<Int, List<TimedPlannedExercise>>, List<TimedPlannedExercise>> {
        metrics?.let {
            it.weeklyPlacementCalls++
            it.placementAtomEvaluations += items.size
        }
        val buckets = (1..days).associateWith { mutableListOf<TimedPlannedExercise>() }
        val deferred = mutableListOf<TimedPlannedExercise>()
        val placementContext = context ?: snapshot?.let { PlacementContext(it, null, days, sessionMinutes) }
        val atomContexts = java.util.IdentityHashMap<TimedPlannedExercise, PlacementAtomContext>()
        fun atom(row: TimedPlannedExercise): PlacementAtomContext = atomContexts[row] ?:
            PlacementAtomContext(
                stableKey = row.item.stableKey,
                estimatedSeconds = row.estimatedSeconds,
                priority = row.item.priority,
                scheduleTier = row.item.scheduleTier(),
                lowerStress = placementContext?.lowerStress(row.item.stableKey) == true,
                primary = StrengthPrimaryMainPolicy.isPrimary(row.item.stableKey),
                main = false,
                protectedPrimary = false,
            ).also { atomContexts[row] = it }
        val ordered = items.sortedWith(compareByDescending<TimedPlannedExercise> { it.item.priority }
            .thenByDescending { it.item.representedGapCodes.isNotEmpty() }
            .thenByDescending { it.item.styleVariant.isNotBlank() }
            .thenByDescending { it.estimatedSeconds }
            .thenBy { it.item.stableKey }
            .thenBy { it.item.styleVariant })
        ordered.forEach { row ->
            val rowContext = atom(row)
            val sameKeyDays = buckets.filterValues { list -> list.any { it.item.stableKey == rowContext.stableKey } }.keys
            val target = buckets.entries.filter { entry ->
                metrics?.let { it.candidateDayChecks++ }
                entry.key !in sameKeyDays && placementSessionFits(
                    entry.value.sumOf { it.estimatedSeconds }, rowContext.estimatedSeconds, sessionMinutes
                )
            }.minWithOrNull(compareBy<Map.Entry<Int, MutableList<TimedPlannedExercise>>> {
                if (rowContext.lowerStress) it.value.filter { atom(it).lowerStress }.sumOf { atom(it).estimatedSeconds } else 0
            }.thenBy {
                if (!robustSchedule) 0 else when (rowContext.scheduleTier) {
                    ScheduleTier.CORE_MUST_DO -> if (it.key <= (days + 1) / 2) 0 else 1
                    ScheduleTier.IMPORTANT -> 0
                    ScheduleTier.OPTIONAL_CAPACITY -> days - it.key
                }
            }.thenBy { it.value.sumOf { atom(it).estimatedSeconds } }
                .thenBy { it.key })
            if (target == null) {
                deferred += row
                ownerMutationSink?.invoke(WeeklyOwnerPlacementMutation(row, null, null, null, null,
                    OwnerAllocationStage.TIMED_EXECUTION_ALLOCATION, OwnerAllocationCause.SESSION_TIME_LIMIT))
            } else {
                val order = target.value.size + 1
                target.value += row
                ownerMutationSink?.invoke(WeeklyOwnerPlacementMutation(row, null, null, target.key, order,
                    OwnerAllocationStage.INITIAL_WEEKLY_PLACEMENT, OwnerAllocationCause.INITIAL_PLACEMENT_POLICY))
            }
        }
        return buckets to deferred
    }
}

internal fun PlannedExercise.scheduleTier(): ScheduleTier = when {
    transition != null && priority >= 75 || material && priority >= 100 -> ScheduleTier.CORE_MUST_DO
    transition != null || material || role == "PERFORMANCE_CONTINUITY" -> ScheduleTier.IMPORTANT
    else -> ScheduleTier.OPTIONAL_CAPACITY
}
