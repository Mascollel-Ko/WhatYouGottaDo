package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerAllocationProvenanceTest {
    private val causeOwner = StimulusPrescriptionOwnerIdentity("canonical_fly", "B5_HYPERTROPHY")
    private val displacedOwner = StimulusPrescriptionOwnerIdentity("posterior_chain", "COVERAGE_POSTERIOR_CHAIN")

    private fun state(week: Int, count: Int, role: String = displacedOwner.selectionRole, prescription: String = "8-12") =
        OwnerAllocationState(week, null, null, count, (1..count).map { ProgramSetPrescription(setIndex = it, reps = 8,
            weightKg = 0.0, seconds = 0) },
            prescription, role)

    private fun event(week: Int = 1, role: String = displacedOwner.selectionRole) = OwnerAllocationProvenance(
        displacedOwner.copy(selectionRole = role), OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION,
        OwnerAllocationAction.SET_COUNT_REDUCED, state(week, 3, role), state(week, 2, role),
        OwnerAllocationCause.CAPACITY_LIMIT, evidenceCodes = listOf("ALLOCATOR_EMITTED"))

    private fun edge(weekEvent: OwnerAllocationProvenance = event(), displaced: StimulusPrescriptionOwnerIdentity = displacedOwner) =
        OwnerDisplacementEdge(causeOwner, displaced, weekEvent.stage, weekEvent.cause, 1,
            week = weekEvent.before?.week, targetIds = setOf("target_hypertrophy"), qualities = setOf("HYPERTROPHY"),
            authorizedDemandIds = setOf("authorized_canonical_fly"))

    @Test
    fun exactReductionNeedsExactOwnerStageStatesAndDisplacementEdge() {
        val exact = event()
        assertTrue(hasExactOwnerDisplacementProof(listOf(exact), listOf(edge(exact)), causeOwner, displacedOwner,
            requireNotNull(exact.before), requireNotNull(exact.after)))
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact), emptyList(), causeOwner, displacedOwner,
            requireNotNull(exact.before), requireNotNull(exact.after))) // fabricated capacity claim
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact.copy(owner = displacedOwner.copy(selectionRole = "OTHER"))),
            listOf(edge(exact)), causeOwner, displacedOwner, requireNotNull(exact.before), requireNotNull(exact.after)))
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact), listOf(edge(exact, displacedOwner.copy(stableKey = "other_owner"))),
            causeOwner, displacedOwner, requireNotNull(exact.before), requireNotNull(exact.after)))
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact.copy(stage = OwnerAllocationStage.BOUNDED_DAY_REBALANCER,
            action = OwnerAllocationAction.PLACEMENT_MOVED)), listOf(edge(exact)), causeOwner, displacedOwner,
            requireNotNull(exact.before), requireNotNull(exact.after)))
        val prescriptionMutation = state(1, 2, prescription = "different prescription")
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact), listOf(edge(exact)), causeOwner, displacedOwner,
            requireNotNull(exact.before), prescriptionMutation))
        val restored = exact.copy(stage = OwnerAllocationStage.RESIDUAL_COMPLETION,
            action = OwnerAllocationAction.SET_COUNT_EXPANDED, before = exact.after, after = exact.before, mutationSequence = 1)
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact, restored), listOf(edge(exact)), causeOwner, displacedOwner,
            requireNotNull(exact.before), requireNotNull(exact.after))) // later restoration makes the earlier edge stale
        assertFalse(hasExactOwnerDisplacementProof(listOf(exact.copy(cause = OwnerAllocationCause.UNKNOWN_UNPROVEN)),
            listOf(edge(exact)), causeOwner, displacedOwner, requireNotNull(exact.before), requireNotNull(exact.after)))
    }

    @Test
    fun acceptedMultiWeekEventsAndEdgesSerializeLosslesslyInDeterministicOrder() {
        val weekTwo = event(week = 2)
        val weekOne = event(week = 1)
        val edgeTwo = edge(weekTwo)
        val edgeOne = edge(weekOne)
        val execution = ExecutionAllocationTrace(
            capacity = WeeklyCapacityEnvelope(3, 60, 10800, 0, 0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 5, 5, 5),
            continuityRequestedUnits = 0, continuityAllocatedUnits = 0, materialGapRequestedUnits = 5,
            materialGapAllocatedUnits = 5, selectedMaterialGaps = emptyList(), representedMaterialGaps = emptyList(),
            deferredMaterialGaps = emptyMap(), optionalDevelopmentalItems = emptyList(), candidateAudit = emptyMap(),
            representedGapCodesByStableKey = emptyMap(), prescriptionSources = emptyMap(),
            ownerAllocationProvenance = listOf(weekTwo, weekOne), ownerDisplacementEdges = listOf(edgeTwo, edgeOne)
        )
        val json = execution.toJson()
        val serializedEvents = json.getJSONArray("ownerAllocationProvenance")
        assertEquals(2, serializedEvents.length())
        assertEquals(1, serializedEvents.getJSONObject(0).getJSONObject("before").getInt("week"))
        assertEquals(displacedOwner.selectionRole, serializedEvents.getJSONObject(0).getJSONObject("owner").getString("selectionRole"))
        assertEquals(3, serializedEvents.getJSONObject(0).getJSONObject("before").getJSONArray("setPrescriptions").length())
        assertEquals(2, serializedEvents.getJSONObject(0).getJSONObject("after").getJSONArray("setPrescriptions").length())
        assertEquals(2, json.getJSONArray("ownerDisplacementEdges").length())
        val jsonCopy = JSONObject(json.toString())
        assertEquals(json.toString(), jsonCopy.toString())
    }
}
