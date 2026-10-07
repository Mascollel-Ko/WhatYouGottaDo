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

/** Exact source-stage regression for the reviewed H fixture after C28 authority re-resolution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class C10OriginMutationInvestigationTest {
    @Test
    fun reviewedHypertrophyCoverageNeedStaysVisibleWithoutAnUnauthorizedPrescription() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            "reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true
        )
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec))
        val comparison = requireNotNull(result.comparison)
        val owner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        val controlDecision = requireNotNull(comparison.control.personalizedDecision)
        val experimentalDecision = requireNotNull(comparison.experimental.personalizedDecision)
        val controlTrace = requireNotNull(controlDecision.planningBudget?.execution)
        val experimentalTrace = requireNotNull(experimentalDecision.planningBudget?.execution)
        fun exactRows(program: GeneratedProgramSkeleton, identity: StimulusPrescriptionOwnerIdentity) = program.items
            .filter { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) == identity }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
        val controlH = exactRows(comparison.control, owner)
        val experimentalH = exactRows(comparison.experimental, owner)

        // The late CONTROL comparator retains its legacy 3-set row. The experimental material-
        // demand candidate remains visible as a need, but B6 has no exact authority for it.
        assertTrue(controlDecision.frequencyDemand?.candidates.orEmpty().any {
            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == owner && it.item.targetSets == 2
        })
        assertTrue(experimentalH.isEmpty())
        assertTrue(experimentalTrace.materialDemandCandidateOrigins.any { it.owner == owner })
        assertTrue(experimentalTrace.unresolvedMaterialDemandGaps.isNotEmpty())
        assertFalse(experimentalDecision.frequencyDemand?.candidates.orEmpty().any {
            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == owner
        })
        assertTrue(controlH.isNotEmpty())
        assertTrue(controlH.all { it.setCount == 3 && it.setPrescriptions.size == 3 })
        val controlExpansion = controlTrace.ownerAllocationProvenance.filter {
            it.owner == owner && it.stage == OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION &&
                it.action == OwnerAllocationAction.SET_COUNT_EXPANDED
        }
        assertEquals(controlH.map { it.weekNumber }.distinct().sorted(),
            controlExpansion.mapNotNull { it.after?.week }.distinct().sorted())
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

        assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
        val readiness = requireNotNull(comparison.experimentalReadinessAudit)
        assertFalse(readiness.changeProvenanceClosed)
        assertTrue("C28 removes the unauthorized set-count delta rather than attributing it", readiness.changeAttributions.none {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                "UNEXPLAINED_PRESCRIPTION_CHANGE" in it.reasonCodes
        })
        assertNotNull(readiness.changeAttributions.singleOrNull {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                "UNEXPLAINED_REMOVED_IDENTITY" in it.reasonCodes
        })
    }
}
