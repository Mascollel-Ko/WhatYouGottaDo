package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exact source-stage regression for both owner changes found in the reviewed H fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class C10OriginMutationInvestigationTest {
    @Test
    fun reviewedHypertrophyDeltasHaveTheirActualOriginStages() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            "reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true
        )
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec))
        val comparison = requireNotNull(result.comparison)
        val owner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        val coreOwner = StimulusPrescriptionOwnerIdentity("ex_28347c1f", "COVERAGE_CORE_DIRECT")
        val controlDecision = requireNotNull(comparison.control.personalizedDecision)
        val experimentalDecision = requireNotNull(comparison.experimental.personalizedDecision)
        val controlTrace = requireNotNull(controlDecision.planningBudget?.execution)
        val experimentalTrace = requireNotNull(experimentalDecision.planningBudget?.execution)
        fun exactRows(program: GeneratedProgramSkeleton, identity: StimulusPrescriptionOwnerIdentity) = program.items
            .filter { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) == identity }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
        val controlH = exactRows(comparison.control, owner)
        val experimentalH = exactRows(comparison.experimental, owner)
        val finalWeeks = experimentalH.map { it.weekNumber }.distinct().sorted()

        // Both source planners receive a two-set demand. CONTROL's finite kernel expands it to 3;
        // EXPERIMENTAL retains the demanded 2, so this fixture has no EXP set-reduction event.
        assertTrue(controlDecision.frequencyDemand?.candidates.orEmpty().any {
            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == owner && it.item.targetSets == 2
        })
        assertTrue(experimentalDecision.frequencyDemand?.candidates.orEmpty().any {
            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == owner && it.item.targetSets == 2
        })
        assertEquals(finalWeeks, controlH.map { it.weekNumber }.distinct().sorted())
        assertTrue(controlH.all { it.setCount == 3 && it.setPrescriptions.size == 3 })
        assertTrue(experimentalH.all { it.setCount == 2 && it.setPrescriptions.size == 2 })
        val controlExpansion = controlTrace.ownerAllocationProvenance.filter {
            it.owner == owner && it.stage == OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION &&
                it.action == OwnerAllocationAction.SET_COUNT_EXPANDED
        }
        assertEquals(finalWeeks, controlExpansion.mapNotNull { it.after?.week }.distinct().sorted())
        assertTrue(controlExpansion.all {
            it.before?.setCount == 2 && it.after?.setCount == 3 &&
                it.before?.setPrescriptions?.size == 2 && it.after?.setPrescriptions?.size == 3 &&
                it.cause == OwnerAllocationCause.CAPACITY_SHARE_ALLOCATION &&
                "FINITE_ALLOCATOR_SHARED_UNIT_ALLOCATION" in it.evidenceCodes
        })
        assertFalse(experimentalTrace.ownerAllocationProvenance.any { event -> event.owner == owner && event.action in setOf(
            OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED,
            OwnerAllocationAction.PRESCRIPTION_CHANGED
        ) })
        assertTrue(experimentalTrace.ownerDisplacementEdges.none { it.displacedOwner == owner })

        // The independent core delta is first assigned by the initial weekly placement policy.
        val controlCore = exactRows(comparison.control, coreOwner)
        val experimentalCore = exactRows(comparison.experimental, coreOwner)
        fun assertInitialPlacement(trace: List<OwnerAllocationProvenance>, rows: List<ProgramSkeletonItem>, day: Int, order: Int) {
            val events = trace.filter { it.owner == coreOwner &&
                it.stage == OwnerAllocationStage.INITIAL_WEEKLY_PLACEMENT &&
                it.action == OwnerAllocationAction.PLACEMENT_ASSIGNED }
            assertEquals(rows.map { it.weekNumber }.distinct().sorted(), events.mapNotNull { it.after?.week }.distinct().sorted())
            assertTrue(events.all { it.before?.day == null && it.after?.day == day && it.after?.order == order &&
                it.cause == OwnerAllocationCause.INITIAL_PLACEMENT_POLICY })
            assertEquals(rows.map { it.weekNumber to (it.dayOfWeek to it.orderIndex) }.toSet(),
                events.mapNotNull { event -> event.after?.let { it.week to (it.day to it.order) } }.toSet())
        }
        assertInitialPlacement(controlTrace.ownerAllocationProvenance, controlCore, 5, 1)
        assertInitialPlacement(experimentalTrace.ownerAllocationProvenance, experimentalCore, 1, 2)
        assertFalse((controlTrace.ownerAllocationProvenance + experimentalTrace.ownerAllocationProvenance).any {
            it.owner == coreOwner && it.stage in setOf(OwnerAllocationStage.BOUNDED_DAY_REBALANCER,
                OwnerAllocationStage.POST_SPLIT_WEEKLY_REFLOW)
        })

        assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
        val readiness = requireNotNull(comparison.experimentalReadinessAudit)
        assertFalse(readiness.changeProvenanceClosed)
        assertEquals(listOf("CHANGE_PROVENANCE_UNCLOSED"), readiness.reasonCodes)
        assertNotNull(readiness.changeAttributions.singleOrNull {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED
        })
    }
}
