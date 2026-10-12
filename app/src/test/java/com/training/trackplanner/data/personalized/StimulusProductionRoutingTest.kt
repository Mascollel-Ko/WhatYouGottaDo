package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusProductionRoutingTest {
    @Test
    fun b7AndB8DenialsAreRecordedButDoNotReplaceTheValidatedNewGenerator() {
        val comparison = comparison().copy(experimentalReadinessAudit = StimulusExperimentalReadinessAudit(
            status = StimulusExperimentalReadinessStatus.NOT_ELIGIBLE,
            changeProvenanceClosed = false,
            reasonCodes = listOf("CHANGE_PROVENANCE_UNCLOSED", "UNEXPLAINED_REMOVED_IDENTITY")
        ))
        val authority = authority().copy(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf("B8_B7_NOT_ELIGIBLE")
        )

        val result = StimulusProductionRouter().route(
            comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )

        assertSame(comparison.experimental, result.program)
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.decision.selectedSource)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, result.decision.b7Status)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, result.decision.b8Status)
        assertEquals(listOf("B7_DIAGNOSTIC_NOT_ELIGIBLE", "B8_DIAGNOSTIC_CONTROL_REQUIRED", "B9_NEW_GENERATOR_SELECTED"),
            result.decision.reasonCodes)
        assertTrue(result.decision.productionRoutingActive)
    }

    @Test
    fun absentB8DoesNotBlockNewGenerator() {
        val comparison = comparison()
        val result = StimulusProductionRouter().route(
            comparison, null, StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )

        assertSame(comparison.experimental, result.program)
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.decision.selectedSource)
        assertTrue(result.decision.productionRoutingActive)
        assertEquals(listOf("B9_NEW_GENERATOR_SELECTED"), result.decision.reasonCodes)
    }

    @Test
    fun controlOnlyRequiresExplicitLegacyMode() {
        val comparison = comparison()
        val result = StimulusProductionRouter().route(
            comparison, authority(), StimulusProductionRoutingMode.CONTROL_ONLY
        )

        assertSame(comparison.control, result.program)
        assertEquals(StimulusProductionProgramSource.CONTROL, result.decision.selectedSource)
        assertFalse(result.decision.productionRoutingActive)
    }

    @Test
    fun independentExecutionGateRejectsUnsafeNewGeneratorOutputBeforeRouting() {
        val unsafe = StimulusProductionExecutionValidation(
            executable = false,
            reasonCodes = listOf("B6_MATERIALIZED_DOSE_NOT_EXECUTABLE")
        )
        val failure = runCatching {
            StimulusProductionRouter().selectExecutable(comparison().experimental, unsafe)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(requireNotNull(failure).message.orEmpty().contains("B6_MATERIALIZED_DOSE_NOT_EXECUTABLE"))
    }

    private fun authority() = StimulusProductionCutoverAuthorityDecision(
        status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
        scope = StimulusProductionCutoverScope.STRENGTH_V1,
        authorizedOwnerIdentities = listOf(StimulusPrescriptionOwnerIdentity("squat", "PRIMARY")),
        reasonCodes = listOf("B8_STRENGTH_V1_AUTHORIZED"),
        b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
    )

    private fun comparison(): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("B9", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", .5, "AUTO",
            ProgramPeriodizationType.AUTO, 1)
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
                selectionReason = "B5 selected for B4 demand",
                weightSource = "TEST",
                selectionRole = "PRIMARY",
                setPrescriptions = listOf(ProgramSetPrescription(1, reps, 80.0, 0))
            )
        ),
        weekDaySchedule = mapOf(1 to setOf(1))
    )
}
