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
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusExperimentalReadinessTest {
    @Test
    fun numericDistanceImprovementIsEligibleWithoutWinnerOrProductionAuthority() {
        val comparison = comparison(
            controlUnits = 2.0,
            experimentalUnits = 4.0,
            controlSessions = 1.0,
            experimentalSessions = 2.0
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, audit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.IMPROVED, audit.targetOutcomes.single().status)
        assertTrue(audit.targetOutcomes.single().controlWeeklyUnitsDistance!! > audit.targetOutcomes.single().experimentalWeeklyUnitsDistance!!)
        assertTrue(audit.shadowOnly)
        assertFalse(audit.productionAuthority)
        assertEquals(null, audit.winner)
    }

    @Test
    fun numericRegressionBlocksReadinessAndCollateralRegressionIsVisible() {
        val comparison = comparison(controlUnits = 4.0, experimentalUnits = 2.0, controlSessions = 2.0, experimentalSessions = 1.0)
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.REGRESSED, audit.targetOutcomes.single().status)
        assertTrue(audit.reasonCodes.contains("TARGET_REGRESSED"))
    }

    @Test
    fun directionOnlyPresenceUsesDirectPresenceSemantics() {
        val target = qualityTarget(StimulusTargetNumericAuthority.DIRECTION_ONLY, null)
        val comparison = comparison(
            target = target,
            controlUnits = 0.0,
            experimentalUnits = 2.0,
            controlSessions = 0.0,
            experimentalSessions = 1.0,
            controlUnitsStatus = StimulusTargetControlStatus.DIRECT_ABSENT,
            experimentalUnitsStatus = StimulusTargetControlStatus.DIRECT_PRESENT,
            controlSessionsStatus = StimulusTargetControlStatus.DIRECT_ABSENT,
            experimentalSessionsStatus = StimulusTargetControlStatus.DIRECT_PRESENT
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.IMPROVED, audit.targetOutcomes.single().status)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, audit.status)
    }

    @Test
    fun b6InvariantFailureAndUnexplainedIdentityFailClosed() {
        val comparison = comparison(
            controlUnits = 2.0,
            experimentalUnits = 4.0,
            controlSessions = 1.0,
            experimentalSessions = 2.0,
            materialization = listOf(StimulusPrescriptionMaterializationAudit(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = null,
                authorizedWeeklySetUnits = 1, materializedWeeklySetUnits = 1, targetCompatibleMaterializedUnits = 1,
                shortfall = 0, overrun = 1, prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
            )),
            selectedCandidate = null
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
        assertFalse(audit.materializationIntegrityPassed)
        assertTrue(audit.reasonCodes.contains("B6_AUTHORIZATION_OVERRUN"))
        assertFalse(audit.changeProvenanceClosed)
    }

    @Test
    fun noMaterialChangeIsExplicitAndUnresolvedAffectedEvidenceIsInconclusive() {
        val unchanged = comparison(controlUnits = 4.0, experimentalUnits = 4.0, controlSessions = 2.0, experimentalSessions = 2.0, sameIdentity = true)
        val unchangedAudit = StimulusExperimentalReadinessAuditEngine().audit(unchanged)
        assertEquals(StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE, unchangedAudit.status)

        val unresolved = comparison(controlUnits = null, experimentalUnits = null, controlSessions = null, experimentalSessions = null)
        val unresolvedAudit = StimulusExperimentalReadinessAuditEngine().audit(unresolved)
        assertEquals(StimulusExperimentalReadinessStatus.INCONCLUSIVE, unresolvedAudit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, unresolvedAudit.targetOutcomes.single().status)
    }

    private fun comparison(
        target: StimulusQualityTarget = qualityTarget(StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, StimulusTargetRange(4.0, 5.0, 6.0)),
        controlUnits: Double?,
        experimentalUnits: Double?,
        controlSessions: Double?,
        experimentalSessions: Double?,
        controlUnitsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.BELOW_BAND,
        experimentalUnitsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.WITHIN_BAND,
        controlSessionsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.BELOW_BAND,
        experimentalSessionsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.WITHIN_BAND,
        materialization: List<StimulusPrescriptionMaterializationAudit> = emptyList(),
        selectedCandidate: StimulusSelectedCandidate? = StimulusSelectedCandidate("candidate", setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", listOf("B5"), "REALIZATION_UNCLASSIFIED", 2, "STRENGTH"),
        sameIdentity: Boolean = false
    ): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("readiness", ProgramGoal.STRENGTH, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 2)
        val controlKey = if (sameIdentity) "candidate" else "control"
        val control = skeleton(request, item(controlKey))
        val experimental = skeleton(request, item("candidate"))
        val targetPlan = StimulusTargetPlan(listOf(target), emptyList(), emptyList())
        val controlAudit = StimulusTargetControlProgramAudit(1, listOf(qualityAudit(target, controlUnits, controlSessions, controlUnitsStatus, controlSessionsStatus)), emptyList())
        val experimentalAudit = StimulusTargetControlProgramAudit(1, listOf(qualityAudit(target, experimentalUnits, experimentalSessions, experimentalUnitsStatus, experimentalSessionsStatus)), emptyList())
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = listOfNotNull(selectedCandidate), traces = emptyList(),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        return StimulusSelectionProgramComparisonEngine().compare(control, experimental, targetPlan, selection, controlAudit, experimentalAudit).copy(
            prescriptionMaterializationAudits = materialization
        )
    }

    private fun qualityTarget(authority: StimulusTargetNumericAuthority, range: StimulusTargetRange?) = StimulusQualityTarget(
        quality = TrainableQuality.STRENGTH, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = authority, baselineSource = null, baselineConfidence = null,
        weeklyDirectUnitsTarget = range, weeklyDirectSessionsTarget = range,
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null, exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun qualityAudit(target: StimulusQualityTarget, units: Double?, sessions: Double?, unitsStatus: StimulusTargetControlStatus, sessionsStatus: StimulusTargetControlStatus) = StimulusQualityControlProgramAudit(
        quality = target.quality, strategy = target.strategy, numericAuthority = target.numericAuthority,
        targetWeeklyDirectUnits = target.weeklyDirectUnitsTarget, plannedWeeklyDirectUnits = units, weeklyDirectUnitsStatus = unitsStatus,
        targetWeeklyDirectSessions = target.weeklyDirectSessionsTarget, plannedWeeklyDirectSessions = sessions, weeklyDirectSessionsStatus = sessionsStatus,
        exposureWeekFrequencyReference = null, plannedExposureWeekFrequency = null, exposureWeekFrequencyDelta = null, reasonCodes = emptyList()
    )

    private fun item(key: String) = ProgramSkeletonItem(
        localId = key, weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key, exerciseName = key,
        category = "STRENGTH", restSeconds = 90, prescription = "8 reps", setCount = 2, reps = 8, weightKg = 0.0,
        seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = "STRENGTH",
        setPrescriptions = List(2) { ProgramSetPrescription(it + 1, 8, 0.0, 0) }
    )

    private fun skeleton(request: ProgramSkeletonRequest, item: ProgramSkeletonItem) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)), items = listOf(item)
    )
}
