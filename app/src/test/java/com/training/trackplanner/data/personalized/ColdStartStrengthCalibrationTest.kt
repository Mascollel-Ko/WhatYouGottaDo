package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.curve.CurveMatchLevel
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ColdStartStrengthCalibrationTest {
    private val owner = StimulusPrescriptionOwnerIdentity("barbell_bench_press", CANONICAL_STRENGTH_SELECTION_ROLE)
    private val target = StimulusQualityTarget(
        quality = TrainableQuality.STRENGTH,
        strategy = StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
        priority = TargetPriority.PRIMARY,
        numericAuthority = StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
        baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        baselineConfidence = PlanningConfidence.MODERATE,
        weeklyDirectUnitsTarget = null,
        weeklyDirectSessionsTarget = null,
        exposureWeekDirectUnitsReference = null,
        exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(),
        evidence = emptyList()
    )
    private val candidate = StimulusSelectedCandidate(
        stableKey = owner.stableKey,
        coveredTargetIds = setOf("QUALITY:STRENGTH"),
        primaryTargetId = "QUALITY:STRENGTH",
        selectionReasons = listOf("B4_TARGET_REQUESTED_IDENTITY"),
        currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
        targetSetsFromExistingPrescription = 2,
        selectionRole = owner.selectionRole
    )

    private fun missingHistory(
        semantics: StrengthLoadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD,
        role: String = owner.selectionRole
    ) = StrengthTrainingLoadResolution(
        owner = StimulusPrescriptionOwnerIdentity(owner.stableKey, role),
        evidenceTier = StrengthCapacityEvidenceTier.WEAK_INSUFFICIENT,
        ownerLoadSemantics = semantics,
        unavailableReasons = listOf(StrengthTrainingLoadUnavailableReason.EXACT_OWNER_STRENGTH_SIGNAL_MISSING)
    )

    @Test
    fun numericDoseAndExactOwnerWithKnownLoadSemanticsCreatesBlankWeightShape() {
        val result = ColdStartStrengthCalibrationResolver().resolve(
            target, candidate, b4AuthorizedSetCount = 2, currentProbeSetCount = 2, restSeconds = 120, c14 = missingHistory()
        )

        assertTrue(result.available)
        val proposal = requireNotNull(result.proposal)
        assertEquals(owner, proposal.owner)
        assertEquals(2, proposal.setCount)
        assertEquals(6, proposal.repetitions)
        assertEquals(6.5, proposal.targetRpe, 0.0)
        assertEquals(ProgramLoadState.USER_CALIBRATION_REQUIRED, proposal.loadState)
        assertTrue(proposal.sets.all { it.weightKg == 0.0 && it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED })
        assertTrue(proposal.reasonCodes.contains("COLD_START_STRENGTH_6_REP_CALIBRATION"))
    }

    @Test
    fun directionOnlyCannotInventSetDemand() {
        val result = ColdStartStrengthCalibrationResolver().resolve(
            target.copy(numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY),
            candidate,
            b4AuthorizedSetCount = 0,
            currentProbeSetCount = 0,
            restSeconds = 120,
            c14 = missingHistory()
        )
        assertFalse(result.available)
        assertTrue(result.unavailableReasons.contains(ColdStartStrengthCalibrationReason.B4_DOSE_AUTHORITY_UNAVAILABLE))
        assertTrue(result.unavailableReasons.contains(ColdStartStrengthCalibrationReason.SET_DEMAND_UNAVAILABLE))
    }

    @Test
    fun coldStartAuthorityIsStrengthOnly() {
        listOf(TrainableQuality.HYPERTROPHY, TrainableQuality.POWER).forEach { quality ->
            val result = ColdStartStrengthCalibrationResolver().resolve(
                target.copy(quality = quality), candidate, 2, 2, 120, missingHistory()
            )
            assertFalse(result.available)
            assertTrue(result.unavailableReasons.contains(ColdStartStrengthCalibrationReason.WRONG_QUALITY))
        }
    }

    @Test
    fun unresolvedMechanicsOrAnotherRoleCannotAuthorizeCalibration() {
        val unresolved = ColdStartStrengthCalibrationResolver().resolve(
            target, candidate, 2, 2, 120, missingHistory(StrengthLoadSemantics.BODYWEIGHT_MINUS_ASSISTANCE)
        )
        assertFalse(unresolved.available)
        assertTrue(unresolved.unavailableReasons.contains(ColdStartStrengthCalibrationReason.OWNER_LOAD_SEMANTICS_UNRESOLVED))

        val differentRole = ColdStartStrengthCalibrationResolver().resolve(
            target, candidate, 2, 2, 120, missingHistory(role = "LEGACY_ROLE")
        )
        assertFalse(differentRole.available)
        assertTrue(differentRole.unavailableReasons.contains(ColdStartStrengthCalibrationReason.SAME_OWNER_HISTORY_NOT_ABSENT))
    }

    @Test
    fun onlyExactOwnerMissingSignalQualifiesAndExistingEvidenceTakesPrecedence() {
        val wrongFailure = missingHistory().copy(
            unavailableReasons = listOf(StrengthTrainingLoadUnavailableReason.CAPACITY_REFERENCE_STALE)
        )
        val rejected = ColdStartStrengthCalibrationResolver().resolve(target, candidate, 2, 2, 120, wrongFailure)
        assertFalse(rejected.available)
        assertTrue(rejected.unavailableReasons.contains(ColdStartStrengthCalibrationReason.C14_FAILURE_NOT_EXACT_OWNER_SIGNAL_MISSING))

        val evidence = missingHistory().copy(ownerLocalObservations = listOf(
            StrengthTrainingSourceObservation(
                evidenceId = "history-1",
                date = LocalDate.of(2026, 9, 1),
                sessionId = "session-1",
                reps = 6,
                rawLoadKg = 50.0,
                resolvedLoadKg = 50.0,
                observedRpe = 6.5,
                activityKind = PlannedActivityKind.RESISTANCE,
                classificationAuthority = StimulusClassificationAuthority.REVIEWED_CANONICAL,
                realizedKind = RealizedStimulusKind.STRENGTH_LIKE,
                realizationStatus = RealizedStimulusStatus.REALIZED,
                realizationReasonCodes = emptyList()
            )
        ))
        val evidenceFlagged = ColdStartStrengthCalibrationResolver().resolve(target, candidate, 2, 2, 120, evidence)
        assertFalse(evidenceFlagged.available)

        val reference = StrengthCapacityReference(
            owner = owner,
            estimated1RmKg = 80.0,
            lower80Kg = 70.0,
            upper80Kg = 90.0,
            posteriorLogVariance = .01,
            referenceDate = LocalDate.of(2026, 9, 1),
            posteriorObservationCount = 3,
            twoSidedObservationCount = 3,
            loadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD,
            curveProfileId = "test",
            curveMatchLevel = CurveMatchLevel.EXACT_EXERCISE,
            curveAssignmentVersion = "test",
            evidenceIds = listOf("prior-1")
        )
        val existingC14 = missingHistory().copy(capacityReference = reference)
        assertFalse(ColdStartStrengthCalibrationResolver().resolve(target, candidate, 2, 2, 120, existingC14).available)
    }
}
