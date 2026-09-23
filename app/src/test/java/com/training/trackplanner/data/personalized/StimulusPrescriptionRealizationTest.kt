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
        canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(posteriorMedianKg = 100.0, observationCount = 2))
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

    private fun selection(vararg candidates: StimulusSelectedCandidate, traces: List<StimulusCandidateSelectionTrace> = emptyList()) = StimulusCandidateSelectionPlan(
        selectedCandidates = candidates.toList(), traces = traces, materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
    )

    private fun prescription(reps: Int, weight: Double, source: String = "TEST") = PlannedPrescription(
        text = "test", sets = listOf(ProgramSetPrescription(1, reps, weight, 0), ProgramSetPrescription(2, reps, weight, 0)),
        restSeconds = 120, weightSource = source
    )

    private fun owner(role: String = "B5_ROLE") = StimulusPrescriptionOwnerIdentity(key, role)

    @Test
    fun compatibleStrengthUsesPlannedCompatibilityWithoutMutationAuthority() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(owner() to prescription(5, 80.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE, result.status)
        assertEquals(PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT, result.plannedCompatibility?.status)
        assertFalse(result.mutationAuthority)
        assertEquals("B5_ROLE", result.owner?.selectionRole)
    }

    @Test
    fun strengthEightRepProbeGetsTypedSafeProposalAtExistingSeventyPercentLoad() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(owner() to prescription(8, 80.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED, result.status)
        assertEquals(2, result.proposedPrescription?.sets?.size)
        assertEquals(5, result.proposedPrescription?.sets?.first()?.reps)
        assertEquals(80.0, result.proposedPrescription?.sets?.first()?.weightKg ?: -1.0, .001)
    }

    @Test
    fun strengthBelowSeventyPercentCannotBeAutoIncreased() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(owner() to prescription(8, 60.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION, result.status)
        assertTrue(result.proposedPrescription == null)
        assertEquals(PlannedStimulusCompatibilityStatus.INCOMPATIBLE, result.plannedCompatibility?.status)
    }

    @Test
    fun unresolvedLoadIsNotFabricatedAsRealization() {
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(owner() to prescription(5, 0.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION, result.status)
        assertEquals(PlannedStimulusCompatibilityStatus.UNRESOLVED, result.plannedCompatibility?.status)
    }

    @Test
    fun hypertrophyKeepsEightRepZeroLoadProvisionalPath() {
        val hTarget = target(TrainableQuality.HYPERTROPHY)
        val hCandidate = candidate().copy(coveredTargetIds = setOf("QUALITY:HYPERTROPHY"), primaryTargetId = "QUALITY:HYPERTROPHY")
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(hTarget), emptyList(), emptyList()), selection(hCandidate), snapshot,
            mapOf(owner() to prescription(4, 60.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED, result.status)
        assertEquals("PROVISIONAL_8_REPS_ZERO_LOAD", result.proposedPrescription?.numericAuthority)
        assertTrue(result.proposedPrescription?.sets?.all { it.reps == 8 && it.weightKg == 0.0 } == true)
    }

    @Test
    fun controlExistingDirectIdentityGetsExplicitOwnerProvenance() {
        val trace = StimulusCandidateSelectionTrace(
            targetId = "QUALITY:STRENGTH", strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            priority = TargetPriority.PRIMARY, controlDirectCapabilityIdentities = listOf(key),
            selectionRequired = false, candidatePool = emptyList(), selectedStableKey = null,
            coveredByPreviouslySelectedStableKey = null,
            reasonCodes = listOf("DIRECT_CAPABILITY_IDENTITY_ALREADY_PRESENT")
        )
        val result = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(traces = listOf(trace)), snapshot, mapOf(owner("CONTROL_ROLE") to prescription(5, 80.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE, result.status)
        assertEquals("CONTROL_EXISTING_DIRECT_IDENTITY", result.owner?.source)
        assertEquals("CONTROL_ROLE", result.owner?.selectionRole)
    }

    @Test
    fun proxyAndAmbiguousOwnerAreExplicitlyUnavailable() {
        val power = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.POWER)), emptyList(), emptyList()), selection(candidate()), snapshot
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE, power.status)
        val ambiguous = StimulusPrescriptionRealizationPlanEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate("a"), candidate("b")), snapshot,
            mapOf(owner("a") to prescription(8, 60.0), owner("b") to prescription(8, 60.0))
        ).resolutions.single()
        assertEquals(StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER, ambiguous.status)
        assertNotNull(ambiguous.reasonCodes.singleOrNull { it == "MULTIPLE_B5_IDENTITIES_WITHOUT_EXACT_OWNER" })
    }
}
