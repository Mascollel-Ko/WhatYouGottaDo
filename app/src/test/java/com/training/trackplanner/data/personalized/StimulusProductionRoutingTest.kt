package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusProductionRoutingTest {
    @Test
    fun authorizedActiveRoutesExperimentalByIdentity() {
        val comparison = comparison()
        val result = StimulusProductionRouter().route(comparison, authority(), StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertSame(comparison.experimental, result.program)
        assertEquals(StimulusProductionProgramSource.B8_STRENGTH_V1, result.decision.selectedSource)
        assertTrue(result.decision.productionRoutingActive)
    }

    @Test
    fun singleQualityModeRoutesHypertrophyByLosslessAuthorityIdentity() {
        val comparison = comparison()
        val authority = authority().copy(
            scope = StimulusProductionCutoverScope.HYPERTROPHY_V1,
            reasonCodes = listOf("B8_HYPERTROPHY_V1_AUTHORIZED"),
            authorizedAuthorityIdentities = listOf(
                StimulusPrescriptionAuthorityIdentity("squat", "PRIMARY", com.training.trackplanner.data.TrainableQuality.HYPERTROPHY)
            )
        )
        val result = StimulusProductionRouter().route(
            comparison, authority,
            StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.experimental, result.program)
        assertEquals(StimulusProductionProgramSource.B8_HYPERTROPHY_V1, result.decision.selectedSource)
        assertTrue(result.decision.productionRoutingActive)
        assertEquals(listOf("B9_B8_HYPERTROPHY_V1_ROUTED"), result.decision.reasonCodes)
    }

    @Test
    fun singleQualityModeKeepsCombinedAuthorityAtControl() {
        val comparison = comparison()
        val authority = authority().copy(scope = StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1)
        val result = StimulusProductionRouter().route(
            comparison, authority,
            StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.control, result.program)
        assertEquals(StimulusProductionProgramSource.CONTROL, result.decision.selectedSource)
        assertEquals(listOf("B9_B8_COMBINED_SCOPE_NOT_ACTIVE"), result.decision.reasonCodes)
        assertFalse(result.decision.productionRoutingActive)
    }

    @Test
    fun missingLosslessAuthorityIdentityFailsClosed() {
        val comparison = comparison()
        val malformed = authority()
        val result = StimulusProductionRouter().route(
            comparison, malformed.copy(authorizedAuthorityIdentities = emptyList()),
            StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_AUTHORITY_IDENTITY_MISMATCH"), result.decision.reasonCodes)
    }

    @Test
    fun malformedB8RoutingFlagsFailClosed() {
        val comparison = comparison()
        val malformed = authority().copy(routingActive = true)
        val result = StimulusProductionRouter().route(
            comparison, malformed, StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_CONTRACT_INCONSISTENCY"), result.decision.reasonCodes)
    }

    @Test
    fun controlOnlyRoutesControlAsEmergencyRollback() {
        val comparison = comparison()
        val result = StimulusProductionRouter().route(comparison, authority(), StimulusProductionRoutingMode.CONTROL_ONLY)
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_CONTROL_ONLY_POLICY"), result.decision.reasonCodes)
        assertFalse(result.decision.productionRoutingActive)
    }

    @Test
    fun missingOrNonAuthorizedB8AlwaysFallsBackToControl() {
        val comparison = comparison()
        assertSame(
            comparison.control,
            StimulusProductionRouter().route(comparison, null, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE).program
        )
        val required = authority().copy(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            authorizedOwnerIdentities = emptyList()
        )
        val result = StimulusProductionRouter().route(comparison, required, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_CONTROL_REQUIRED"), result.decision.reasonCodes)
    }

    @Test
    fun malformedAuthorizedOwnerSetFailsClosed() {
        val comparison = comparison()
        val malformed = authority().copy(authorizedOwnerIdentities = emptyList())
        val result = StimulusProductionRouter().route(comparison, malformed, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_EMPTY_AUTHORIZED_OWNER_SET"), result.decision.reasonCodes)
    }

    @Test
    fun exactPowerJumpB8AuthorityRoutesOnlyThroughAnEnabledMode() {
        val base = comparison()
        val owner = StimulusPrescriptionOwnerIdentity("squat", "PRIMARY")
        val powerJumpComparison = base.copy(experimentalReadinessAudit =
            StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                changeAttributions = listOf(StimulusExperimentalChangeAttribution(
                    stableKey = owner.stableKey,
                    selectionRole = owner.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B6_APPROVED_POWER_JUMP_POLICY,
                    targetIds = listOf("QUALITY:POWER")
                )),
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true
            )
        )
        val b8 = authority().copy(
            scope = StimulusProductionCutoverScope.POWER_JUMP_V1,
            reasonCodes = listOf("B8_POWER_JUMP_V1_AUTHORIZED"),
            authorizedAuthorityIdentities = listOf(
                StimulusPrescriptionAuthorityIdentity("squat", "PRIMARY", TrainableQuality.POWER)
            )
        )

        val routed = StimulusProductionRouter().route(
            powerJumpComparison, b8, StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(powerJumpComparison.experimental, routed.program)
        assertEquals(StimulusProductionProgramSource.B8_POWER_JUMP_V1, routed.decision.selectedSource)
        assertEquals(listOf("B9_B8_POWER_JUMP_V1_ROUTED"), routed.decision.reasonCodes)

        val disabled = StimulusProductionRouter().route(
            powerJumpComparison, b8, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertSame(powerJumpComparison.control, disabled.program)
        assertEquals(listOf("B9_B8_POWER_JUMP_SCOPE_NOT_ACTIVE"), disabled.decision.reasonCodes)
    }

    private fun authority() = StimulusProductionCutoverAuthorityDecision(
        status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
        scope = StimulusProductionCutoverScope.STRENGTH_V1,
        authorizedOwnerIdentities = listOf(StimulusPrescriptionOwnerIdentity("squat", "PRIMARY")),
        reasonCodes = listOf("B8_STRENGTH_V1_AUTHORIZED"),
        b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
        authorizedAuthorityIdentities = listOf(
            StimulusPrescriptionAuthorityIdentity("squat", "PRIMARY", com.training.trackplanner.data.TrainableQuality.STRENGTH)
        )
    )

    private fun comparison(): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("B9", ProgramGoal.STRENGTH, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        val control = skeleton(request, 8)
        val experimental = skeleton(request, 5)
        return StimulusSelectionProgramComparisonEngine().compare(
            control = control,
            experimental = experimental,
            targetPlan = StimulusTargetPlan(emptyList(), emptyList(), emptyList()),
            selectionPlan = StimulusCandidateSelectionPlan(emptyList(), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap())),
            controlAudit = null,
            experimentalAudit = null
        )
    }

    private fun skeleton(request: ProgramSkeletonRequest, reps: Int) = GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = 7,
        request = request,
        periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 1, 8.0, 1, 0, false)),
        items = listOf(
            ProgramSkeletonItem(
                localId = "squat-$reps",
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseStableKey = "squat",
                exerciseName = "squat",
                category = "STRENGTH",
                restSeconds = 90,
                prescription = "$reps reps",
                setCount = 1,
                reps = reps,
                weightKg = 80.0,
                seconds = 0,
                selectionReason = "test",
                weightSource = "TEST",
                selectionRole = "PRIMARY",
                setPrescriptions = listOf(ProgramSetPrescription(1, reps, 80.0, 0))
            )
        ),
        weekDaySchedule = mapOf(1 to setOf(1))
    )
}
