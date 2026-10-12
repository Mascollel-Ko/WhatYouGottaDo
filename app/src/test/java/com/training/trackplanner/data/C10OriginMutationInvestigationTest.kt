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
    fun reviewedRearDeltShoulderHistoryUsesExactRegionalHypertrophyAuthority() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            "reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true
        )
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec))
        val comparison = requireNotNull(result.comparison)
        val owner = StimulusPrescriptionOwnerIdentity("cable_rear_delt_fly", "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY")
        val authorization = requireNotNull(comparison.prescriptionAuthorizationPlan).authorizations.single {
            it.targetId == "QUALITY:HYPERTROPHY" && it.quality == TrainableQuality.HYPERTROPHY &&
                it.owner?.let { exact -> StimulusPrescriptionOwnerIdentity(exact.stableKey, exact.selectionRole) } == owner
        }
        assertTrue(authorization.status in setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        ))
        assertNotNull("the exact B6 prescription must be executable", authorization.authorizedPrescription)
        val rows = comparison.experimental.items.filter {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) == owner
        }
        assertTrue("the exact regional HYP owner must materialize", rows.isNotEmpty())
        assertEquals(setOf(1, 2), rows.map { it.weekNumber }.toSet())
        assertTrue(rows.all { it.setCount == 3 && it.setPrescriptions.size == 3 })
        assertTrue(rows.flatMap { it.setPrescriptions }.all { it.reps in 7..15 && it.weightKg == 40.0 })
        assertFalse("regional shoulder HYP must not become direct pull movement coverage",
            comparison.experimental.items.any {
                it.exerciseStableKey == owner.stableKey && it.selectionRole.startsWith("CANONICAL_STIMULUS_MOVEMENT_")
            })
        assertTrue(requireNotNull(comparison.experimentalReadinessAudit).changeProvenanceClosed)
        assertEquals(StimulusProductionCutoverScope.HYPERTROPHY_V1, comparison.productionCutoverAuthority?.scope)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            comparison.productionCutoverAuthority?.status)
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
    }
}
