package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Quantity identity is an owner, not an exercise or a display-role substring. */
data class MaterialDemandOwner(val stableKey: String, val selectionRole: String, val variant: String) {
    fun matches(item: PlannedExercise) = this == of(item)
    companion object {
        fun of(item: PlannedExercise) = MaterialDemandOwner(item.stableKey, item.role, item.styleVariant)
    }
}

data class MaterialDemandBound(val owner: MaterialDemandOwner, val requestedUnits: Int, val maximumUnits: Int,
    val prescriptionUnits: Int, val authority: PrescriptionAuthoritySource, val initiallyFundedUnits: Int,
    val allocatedUnits: Int, val rejection: String? = null, val finalFundedUnits: Int = allocatedUnits,
    val materializedUnits: Int = 0) {
    fun toJson() = JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
        .put("variant", owner.variant).put("requestedUnits", requestedUnits).put("maximumUnits", maximumUnits)
        .put("prescriptionUnits", prescriptionUnits).put("authority", authority.name)
        .put("initiallyFundedUnits", initiallyFundedUnits).put("allocatedUnits", allocatedUnits)
        .put("finalFundedUnits", finalFundedUnits).put("materializedUnits", materializedUnits)
        .put("deferredLegitimateUnits", if (rejection == null) (prescriptionUnits - finalFundedUnits).coerceAtLeast(0) else 0)
        .put("rejection", rejection ?: JSONObject.NULL)
}

/** Observation only: physical unused capacity and unfunded demand are deliberately separate. */
data class BoundedMaterialAllocationTrace(val totalCapacity: Int, val totalLegitimateDemand: Int,
    val owners: List<MaterialDemandBound>, val fundedDemand: Int = 0, val materializedDemand: Int = 0) {
    fun toJson() = JSONObject().put("totalCapacity", totalCapacity).put("totalLegitimateDemand", totalLegitimateDemand)
        .put("totalLegitimateMaterialDemand", owners.filter { it.rejection == null }.sumOf { it.maximumUnits })
        .put("fundedDemand", fundedDemand).put("materializedDemand", materializedDemand)
        .put("unusedCapacityUnits", (totalCapacity - materializedDemand).coerceAtLeast(0))
        .put("capacityOverrunUnits", (materializedDemand - totalCapacity).coerceAtLeast(0))
        .put("unfundedCapacityUnits", (totalCapacity - fundedDemand).coerceAtLeast(0))
        .put("deferredLegitimateDemandRemaining", (totalLegitimateDemand - fundedDemand).coerceAtLeast(0))
        .put("deferredMaterialDemandRemaining", owners.filter { it.rejection == null }
            .sumOf { (it.prescriptionUnits - it.finalFundedUnits).coerceAtLeast(0) })
        .put("candidates", JSONArray(owners.map { it.toJson() }))
}

/** Experimental orchestration only. The frozen finite kernel performs the unchanged atomic first pass. */
internal class BoundedMaterialDemandAllocation(snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
    request: ProgramSkeletonRequest, items: List<PlannedExercise>, regional: RegionalExperimentalTargetPlan,
    prescriptions: PersonalizedPrescriptionPlanner, capacity: Int, continuityDemand: Int, coreReserve: Int) {
    private val originals = items.mapIndexed { index, item ->
        val regionalRx = regional.authorizedPrescriptionBySelectionRole[RegionalSelectionIdentity(item.stableKey, item.role)]
        val rx = regionalRx ?: prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style)
        val maximum = regionalRx?.sets?.size ?: item.targetSets.coerceAtLeast(0)
        val equipment = snapshot.exercises[item.stableKey]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
        val eligible = item.stableKey in snapshot.exercises && item.stableKey !in request.excludedExerciseStableKeys &&
            !snapshot.explicitlyRestricted(item.stableKey) && postProcessTissueAllowed(snapshot, state, item.stableKey) &&
            snapshot.metadata[item.stableKey]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") &&
            (request.availableEquipment.isEmpty() || equipment.all { it == "BODYWEIGHT" || it in request.availableEquipment })
        val rejection = when {
            !eligible -> "EXISTING_HARD_GATE"
            rx.sets.isEmpty() || rx.sets.size > maximum -> "PRESCRIPTION_EXCEEDS_DEMAND_MAXIMUM"
            else -> null
        }
        val authority = if (regionalRx != null) PrescriptionAuthoritySource.REGIONAL_TARGET_AUTHORIZED
            else PrescriptionAuthoritySource.EXPERIMENTAL_MATERIAL_AUTHORIZED
        Triple(CapacityCandidateTrace(index + 1, item, rx, 0, false,
            if (rejection == null) CandidateRejectionReason.FINITE_CAPACITY else CandidateRejectionReason.SAFETY_OR_SEMANTIC_REJECTION,
            authority), maximum, rejection)
    }
    private val first = FiniteExecutionAllocator.allocate(capacity, continuityDemand,
        originals.map { (candidate, _, rejection) -> if (rejection == null) candidate.requestedUnits else 0 },
        0.0, coreReserve, emptySet())
    private val funded = first.material.toMutableList()
    private val selectedPrescriptions = mutableMapOf<MaterialDemandOwner, PlannedPrescription>()
    val finite: FiniteAllocation
    val bounds: List<MaterialDemandBound>
    val candidates: List<CapacityCandidateTrace>
    init {
        val materialCapacity = capacity - minOf(coreReserve, continuityDemand, capacity)
        // Revisit unmet owners in the original priority order. No selected owner grows past its request.
        originals.forEachIndexed { index, (candidate, _, rejection) ->
            val rx = when {
                rejection != null -> null
                funded[index] > 0 -> candidate.prescription
                else -> frequencyPortion(snapshot, state, candidate, materialCapacity - funded.sum(), prescriptions)
            }
            if (rx != null) {
                funded[index] = rx.sets.size
                selectedPrescriptions[MaterialDemandOwner.of(candidate.item)] = rx
            }
        }
        finite = FiniteAllocation(minOf(continuityDemand, (capacity - funded.sum()).coerceAtLeast(0)), funded.toList(),
            originals.indices.filter { funded[it] < originals[it].first.requestedUnits })
        candidates = originals.mapIndexed { index, (candidate, _, rejection) -> candidate.copy(fundedBaseUnits = funded[index],
            rejectionReason = if (rejection != null) candidate.rejectionReason
                else if (funded[index] < candidate.requestedUnits) CandidateRejectionReason.FINITE_CAPACITY else CandidateRejectionReason.FUNDED) }
        bounds = originals.mapIndexed { index, (candidate, maximum, rejection) ->
            MaterialDemandBound(MaterialDemandOwner.of(candidate.item), candidate.item.targetSets, maximum,
                candidate.requestedUnits, candidate.prescriptionAuthority, first.material[index], funded[index], rejection)
        }
    }
    fun prescriptionFor(item: PlannedExercise): PlannedPrescription = requireNotNull(selectedPrescriptions[MaterialDemandOwner.of(item)]).also {
        require(it.sets.size == item.targetSets) { "MATERIAL_AUTHORIZATION_QUANTITY_MISMATCH" }
    }
}

/** Re-observe after completion and after frequency/reflow, retaining original maxima across all stages. */
internal fun observeBoundedMaterialDemand(plan: GeneratedProgramSkeleton): GeneratedProgramSkeleton {
    val decision = plan.personalizedDecision ?: return plan
    val provenance = decision.frequencyDemand ?: return plan
    val bounded = provenance.boundedMaterialAllocation ?: return plan
    val trace = requireNotNull(decision.authorizedScheduling)
    fun owner(row: ProgramSkeletonItem): MaterialDemandOwner? {
        val origin = trace.localOrigins[row.localId]
        if (origin != null) {
            val parent = trace.authorized.singleOrNull { it.id == origin.authorizedDemandId } ?: return null
            return if (parent.continuity) null else MaterialDemandOwner.of(parent.item)
        }
        return MaterialDemandOwner(row.exerciseStableKey, row.selectionRole, row.progressionVariant)
    }
    val owners = bounded.owners.map { bound ->
        val funded = trace.authorized.filter { !it.continuity && bound.owner.matches(it.item) }.sumOf { it.prescription.sets.size }
        check(funded <= bound.maximumUnits) { "MATERIAL_AUTHORIZATION_OVERRUN: ${bound.owner} funded=$funded max=${bound.maximumUnits}" }
        plan.items.groupBy { it.weekNumber }.forEach { (week, rows) ->
            val units = rows.filter { owner(it) == bound.owner }.sumOf { it.setPrescriptions.size }
            check(units <= funded && units <= bound.maximumUnits) {
                "MATERIAL_MATERIALIZATION_OVERRUN: ${bound.owner} week=$week units=$units funded=$funded max=${bound.maximumUnits}"
            }
        }
        bound.copy(finalFundedUnits = funded, materializedUnits = plan.items.filter { it.weekNumber == 1 && owner(it) == bound.owner }.sumOf { it.setPrescriptions.size })
    }
    val observed = bounded.copy(totalCapacity = decision.frequencyExpansion?.userDayCapacity ?: bounded.totalCapacity,
        owners = owners, fundedDemand = trace.authorized.sumOf { it.prescription.sets.size },
        materializedDemand = plan.items.filter { it.weekNumber == 1 }.sumOf { it.setPrescriptions.size })
    return plan.copy(personalizedDecision = decision.copy(frequencyDemand = provenance.copy(boundedMaterialAllocation = observed)))
}
