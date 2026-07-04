package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBuilderFatigueAndSelectionV041106Test {
    @Test
    fun planningFatigueKeepsFoundationAnchorWhileTodayExecutionCanBlockIt() {
        val redGate = ProgramFatigueGate(
            band = ProgramFatigueBand.RED,
            volumeFactor = 0.25,
            rpeCap = 7,
            allowsHeavyLower = false,
            allowsHighImpact = false,
            allowsHighIntensityCod = false,
            lowerBodyRestricted = true
        )
        val hinge = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN)

        assertFalse(FatigueSlotPolicy.DEFAULT.allows(hinge, redGate, ProgramFatigueUseCase.TODAY_EXECUTION))
        assertTrue(FatigueSlotPolicy.DEFAULT.allows(hinge, redGate, ProgramFatigueUseCase.PROGRAM_PLANNING))
    }

    @Test
    fun currentRequestHasOnlyFreeTextExerciseAvoidance() {
        val fieldNames = ProgramSkeletonRequest::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("free-text memo field still exists", "excludedExerciseText" in fieldNames)
        assertFalse("selected excluded stableKey field is not wired yet", "excludedExerciseStableKeys" in fieldNames)
        assertFalse("selected preferred stableKey field is not wired yet", "preferredExerciseStableKeys" in fieldNames)
    }

    private fun candidate(slot: ProgramSlotId): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = 1,
                name = "Fixture hinge",
                category = "strength",
                stableKey = "fixture_hinge",
                planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
            ),
            metadata = RuntimeExerciseMetadataDefaults.forExercise(
                Exercise(
                    id = 1,
                    name = "Fixture hinge",
                    category = "strength",
                    stableKey = "fixture_hinge",
                    planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
                )
            ).copy(
                movementFamily = "HEAVY_HINGE",
                movementSubtype = "DEADLIFT",
                programSlot = slot.name,
                stressMagnitudeHint = "HIGH"
            ),
            canonical = true,
            slotCapabilities = SlotCapabilityProfile(
                primary = setOf(slot),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )
}
