package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusPrescriptionRealizationTest {
    private val key = "canonical.exercise"
    private val snapshot = PlanningHistorySnapshot(
        cutoff = LocalDate.of(2026, 9, 23), allConfirmedSets = emptyList(), exercises = emptyMap(),
        metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "MIXED",
        strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences(),
        canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(observationCount = 2))
    )

    private fun target(quality: TrainableQuality, authority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, priority = TargetPriority.PRIMARY,
        numericAuthority = authority, baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        baselineConfidence = PlanningConfidence.HIGH, weeklyDirectUnitsTarget = null, weeklyDirectSessionsTarget = null,
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null, reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun candidate(role: String = "B5_ROLE") = StimulusSelectedCandidate(
        stableKey = key, coveredTargetIds = setOf("QUALITY:STRENGTH"), primaryTargetId = "QUALITY:STRENGTH",
        selectionReasons = emptyList(), currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
        targetSetsFromExistingPrescription = 2, selectionRole = role,
        probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
    )

    private fun selection(vararg candidates: StimulusSelectedCandidate) = StimulusCandidateSelectionPlan(
        selectedCandidates = candidates.toList(), traces = emptyList(), materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
    )

    private fun prescription(reps: Int, weight: Double) = PlannedPrescription(
        text = "test", sets = listOf(ProgramSetPrescription(1, reps, weight, 0), ProgramSetPrescription(2, reps, weight, 0)),
        restSeconds = 120, weightSource = "TEST"
    )

    private val strength = RealizedStimulusClassification(
        RealizedStimulusKind.STRENGTH_LIKE, RealizedStimulusStatus.REALIZED, RealizedStimulusAuthority.REVIEWED,
        resolvedLoadKg = 80.0, reference1RmKg = 100.0, relativeIntensity = .8, observedRpe = 8.0
    )
    private val hypertrophy = strength.copy(kind = RealizedStimulusKind.HYPERTROPHY_LIKE, relativeIntensity = .65)

    @Test
    fun compatibleStrengthIsReportedWithoutMutationAuthority() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(key to prescription(5, 80.0)), mapOf(key to listOf(strength))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE, result.status)
        assertFalse(result.mutationAuthority)
        assertEquals("B5_ROLE", result.owner?.selectionRole)
    }

    @Test
    fun strengthEightRepProbeGetsTypedSafeProposalWithSameSetCount() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(key to prescription(8, 80.0)), mapOf(key to listOf(hypertrophy))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED, result.status)
        assertEquals(2, result.proposedPrescription?.sets?.size)
        assertEquals(5, result.proposedPrescription?.sets?.first()?.reps)
    }

    @Test
    fun unavailableCurrentRealizationFailsClosed() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(key to prescription(5, 0.0)), mapOf(key to listOf(RealizedStimulusClassification.UNCLASSIFIED))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION, result.status)
    }

    @Test
    fun hypertrophyKeepsEightRepZeroLoadProvisionalPath() {
        val hTarget = target(TrainableQuality.HYPERTROPHY)
        val hCandidate = candidate().copy(coveredTargetIds = setOf("QUALITY:HYPERTROPHY"), primaryTargetId = "QUALITY:HYPERTROPHY")
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(hTarget), emptyList(), emptyList()), selection(hCandidate), snapshot,
            mapOf(key to prescription(4, 60.0)), mapOf(key to listOf(RealizedStimulusClassification.UNCLASSIFIED))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED, result.status)
        assertEquals("PROVISIONAL_8_REPS_ZERO_LOAD", result.proposedPrescription?.numericAuthority)
        assertTrue(result.proposedPrescription?.sets?.all { it.reps == 8 && it.weightKg == 0.0 } == true)
    }

    @Test
    fun proxyAndAmbiguousOwnerAreExplicitlyUnavailable() {
        val power = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.POWER)), emptyList(), emptyList()), selection(candidate()), snapshot
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE, power.status)
        val ambiguous = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()), selection(candidate("a"), candidate("b")), snapshot
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER, ambiguous.status)
        assertNotNull(ambiguous.reasonCodes.singleOrNull())
    }
}
