package com.training.trackplanner.data.personalized

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StrengthTrainingLoadAuthorityTest {
    private val key = "barbell_bench_press"
    private val role = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
    private val owner = StimulusPrescriptionOwnerIdentity(key, role)
    private val cutoff = LocalDate.of(2026, 9, 20)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rpeRir = RpeRirPolicy.fromContext(context)
    private val strengthRegistry = StrengthPerformanceRegistry.fromContext(context)
    private val resolver = StrengthTrainingLoadAuthorityResolver()

    @Test
    fun exactOwnerSameRepHistoryCreatesReserveBearingLoadBelowTheCapacityCeiling() {
        val snapshot = snapshot(
            specs = listOf(
                SetSpec(5, 78.0, 8.0, 7),
                SetSpec(5, 78.0, 8.0, 14),
                SetSpec(5, 78.0, 8.0, 21)
            )
        )

        val result = resolver.resolve(snapshot, target(), b5Selected(target()), requestedSetCount = 2)
        val proposal = requireNotNull(result.proposal)

        assertEquals(StrengthCapacityEvidenceTier.DIRECT_PERSONAL_STRENGTH, result.evidenceTier)
        assertEquals(5, proposal.targetRepetitions)
        assertEquals(2, proposal.setCount)
        assertEquals(6.5, proposal.targetRpe, 0.0)
        assertEquals(100.0, proposal.capacityReference.estimated1RmKg, 1e-9)
        assertEquals(5, proposal.repMaxCeiling.repetitions)
        assertEquals(StrengthRepMaxCeilingMeaning.CAPACITY_CEILING_ONLY_NOT_TRAINING_LOAD, proposal.repMaxCeiling.meaning)
        assertTrue(proposal.repMaxCeiling.medianKg in 85.0..90.0)
        assertTrue(proposal.trainingLoadKg in 70.0..75.0)
        assertTrue(proposal.trainingLoadKg < proposal.repMaxCeiling.medianKg)
        assertTrue(proposal.trainingLoadKg <= 78.0)
        assertTrue(proposal.trainingLoadKg / proposal.capacityReference.estimated1RmKg >= 0.70)
        assertEquals(0.5, proposal.roundingIncrementKg, 0.0)
        assertTrue(proposal.reasonCodes.contains("CAPACITY_CEILING_IS_NOT_TRAINING_LOAD"))

        val easierCalibration = StrengthTrainingCalibrationEvidence(
            plannedProposal = proposal,
            evidenceId = "completed-calibration-rpe-5",
            date = cutoff,
            performedRepetitions = 5,
            performedLoadKg = proposal.trainingLoadKg,
            observedRpe = 5.0
        )
        assertEquals(proposal.owner, easierCalibration.owner)
        assertEquals(5, easierCalibration.plannedProposal.targetRepetitions)
        assertEquals(proposal.trainingLoadKg, easierCalibration.plannedProposal.trainingLoadKg, 0.0)
        assertEquals(6.5, easierCalibration.plannedProposal.targetRpe, 0.0)
        assertEquals(5.0, easierCalibration.observedRpe!!, 0.0)
    }

    @Test
    fun exactOwnerHigherRepRpeHistoryUsesTheExistingCurveForABoundaryStrengthBridge() {
        val snapshot = snapshot(specs = listOf(
            SetSpec(8, 80.0, 8.0, 7, RealizedStimulusKind.HYPERTROPHY_LIKE),
            SetSpec(8, 80.0, 8.0, 14, RealizedStimulusKind.HYPERTROPHY_LIKE),
            SetSpec(8, 80.0, 8.0, 21, RealizedStimulusKind.HYPERTROPHY_LIKE)
        ))

        val result = resolver.resolve(snapshot, target(), b5Selected(target()), requestedSetCount = 2)
        val proposal = requireNotNull(result.proposal)

        assertEquals(StrengthCapacityEvidenceTier.PERSONAL_CURVE_INFERRED, result.evidenceTier)
        assertEquals(6, proposal.targetRepetitions)
        assertEquals(StrengthTrainingLoadDerivation.HIGHER_REP_PERSONAL_CURVE_BRIDGE, proposal.derivation)
        assertEquals(3, result.candidateSessionCount)
        assertTrue(proposal.trainingLoadKg < proposal.repMaxCeiling.medianKg)
    }

    @Test
    fun higherRepWorkWithoutRpeSelectsBoundarySixOnlyFromAnExactReferenceAndDoesNotCallItAnRm() {
        val snapshot = snapshot(
            specs = listOf(
                SetSpec(8, 80.0, null, 7, RealizedStimulusKind.HYPERTROPHY_LIKE),
                SetSpec(8, 80.0, null, 14, RealizedStimulusKind.HYPERTROPHY_LIKE),
                SetSpec(8, 80.0, null, 21, RealizedStimulusKind.HYPERTROPHY_LIKE)
            ),
            posteriorLogVariance = 0.001,
            posteriorObservationCount = 3,
            twoSidedObservationCount = 3
        )

        val result = resolver.resolve(snapshot, target(), b5Selected(target()), requestedSetCount = 2)
        val proposal = requireNotNull(result.proposal)

        assertEquals(StrengthCapacityEvidenceTier.SAME_OWNER_E1RM_INFERRED, result.evidenceTier)
        assertEquals(6, proposal.targetRepetitions)
        assertEquals(StrengthTrainingLoadDerivation.EXACT_REFERENCE_CONSERVATIVE_CALIBRATION, proposal.derivation)
        assertTrue(proposal.trainingLoadKg < proposal.repMaxCeiling.medianKg)
        assertTrue(proposal.trainingLoadKg < proposal.capacityReference.estimated1RmKg)
        assertTrue(proposal.reasonCodes.contains("BOUNDARY_6_REP_CALIBRATION"))
    }

    @Test
    fun noRpeAndInsufficientReferenceCannotTurnAWorkSetIntoAnRm() {
        val snapshot = snapshot(
            specs = listOf(
                SetSpec(5, 78.0, null, 7),
                SetSpec(5, 78.0, null, 14)
            ),
            posteriorLogVariance = 0.1,
            posteriorObservationCount = 1,
            twoSidedObservationCount = 1
        )

        val result = resolver.resolve(snapshot, target(), b5Selected(target()), requestedSetCount = 2)

        assertFalse(result.available)
        assertTrue(result.unavailableReasons.any {
            it in setOf(
                StrengthTrainingLoadUnavailableReason.EFFORT_EVIDENCE_INSUFFICIENT,
                StrengthTrainingLoadUnavailableReason.INSUFFICIENT_TWO_SIDED_REFERENCE
            )
        })
    }

    @Test
    fun missingCanonicalOwnerReferencePreservesTheSourceWorkSetsButDoesNotInferAnRm() {
        val base = snapshot(specs = listOf(
            SetSpec(5, 40.0, 8.0, 7),
            SetSpec(5, 40.0, 8.0, 14)
        ))
        val withoutReference = base.copy(canonicalStrengthSignals = emptyMap())

        val result = resolver.resolve(withoutReference, target(), b5Selected(target()), requestedSetCount = 2)

        assertFalse(result.available)
        assertEquals(StrengthTrainingLoadUnavailableReason.EXACT_OWNER_STRENGTH_SIGNAL_MISSING,
            result.unavailableReasons.single())
        assertEquals(2, result.ownerLocalObservations.size)
        assertTrue(result.ownerLocalObservations.all { it.resolvedLoadKg == 40.0 && it.observedRpe == 8.0 })
        assertTrue(result.ownerLocalObservations.all { it.reps == 5 })
        assertEquals(StrengthLoadSemantics.EXTERNAL_LOAD, result.ownerLoadSemantics)
    }

    @Test
    fun b5CandidateMustCoverTheExactStrengthTargetAndFutureReferenceIsRejected() {
        val base = snapshot(specs = listOf(
            SetSpec(5, 78.0, 8.0, 7),
            SetSpec(5, 78.0, 8.0, 14)
        ))
        val uncovered = b5Selected(target()).copy(coveredTargetIds = emptySet())
        val noTargetEdge = resolver.resolve(base, target(), uncovered, requestedSetCount = 2)
        val futureReference = base.copy(canonicalStrengthSignals = base.canonicalStrengthSignals.mapValues { (_, value) ->
            value.copy(referenceDate = cutoff.plusDays(1))
        })
        val future = resolver.resolve(futureReference, target(), b5Selected(target()), requestedSetCount = 2)

        assertEquals(StrengthTrainingLoadUnavailableReason.B5_DIRECT_TARGET_SELECTION_UNAVAILABLE,
            noTargetEdge.unavailableReasons.single())
        assertEquals(StrengthTrainingLoadUnavailableReason.CAPACITY_REFERENCE_STALE,
            future.unavailableReasons.single())
    }

    @Test
    fun anotherExerciseAndAnotherSelectionRoleCannotReuseThisOwnersCapacity() {
        val snapshot = snapshot(specs = listOf(SetSpec(5, 78.0, 8.0, 7), SetSpec(5, 78.0, 8.0, 14)))
        val wrongExercise = resolver.resolve(
            snapshot,
            target(),
            b5Selected(target(), StimulusPrescriptionOwnerIdentity("barbell_back_squat", role)),
            requestedSetCount = 2
        )
        val wrongRole = resolver.resolve(
            snapshot,
            target(),
            b5Selected(target(), StimulusPrescriptionOwnerIdentity(key, "LEGACY_STRENGTH_ROLE")),
            requestedSetCount = 2
        )

        assertEquals(StrengthTrainingLoadUnavailableReason.EXACT_OWNER_STRENGTH_SIGNAL_MISSING, wrongExercise.unavailableReasons.single())
        assertEquals(StrengthTrainingLoadUnavailableReason.OWNER_ROLE_NOT_CANONICAL_STRENGTH, wrongRole.unavailableReasons.single())
    }

    @Test
    fun directionOnlyAndNonStrengthTargetsCannotCreateCalibrationAuthority() {
        val snapshot = snapshot(specs = listOf(SetSpec(5, 78.0, 8.0, 7), SetSpec(5, 78.0, 8.0, 14)))
        val directionTarget = target(StimulusTargetNumericAuthority.DIRECTION_ONLY)
        val hypertrophyTarget = target(quality = TrainableQuality.HYPERTROPHY)
        val powerTarget = target(quality = TrainableQuality.POWER)
        val directionOnly = resolver.resolve(snapshot, directionTarget, b5Selected(directionTarget), 2)
        val hypertrophy = resolver.resolve(snapshot, hypertrophyTarget, b5Selected(hypertrophyTarget), 2)
        val power = resolver.resolve(snapshot, powerTarget, b5Selected(powerTarget), 2)

        assertEquals(StrengthTrainingLoadUnavailableReason.B4_DOSE_AUTHORITY_UNAVAILABLE, directionOnly.unavailableReasons.single())
        assertEquals(StrengthTrainingLoadUnavailableReason.WRONG_QUALITY, hypertrophy.unavailableReasons.single())
        assertEquals(StrengthTrainingLoadUnavailableReason.WRONG_QUALITY, power.unavailableReasons.single())
    }

    @Test
    fun missingOwnerLoadSemanticsFailsClosedBeforeCapacityLookup() {
        val snapshot = snapshot(specs = listOf(SetSpec(5, 78.0, 8.0, 7), SetSpec(5, 78.0, 8.0, 14)))
        val ownerWithoutSemantics = StimulusPrescriptionOwnerIdentity("unmodeled_strength_owner", role)

        val result = resolver.resolve(snapshot, target(), b5Selected(target(), ownerWithoutSemantics), requestedSetCount = 2)

        assertEquals(StrengthTrainingLoadUnavailableReason.OWNER_LOAD_SEMANTICS_UNRESOLVED,
            result.unavailableReasons.single())
        assertFalse(result.available)
    }

    @Test
    fun contradictoryOwnerHistoryFailsClosed() {
        val snapshot = snapshot(
            specs = listOf(SetSpec(5, 150.0, 8.0, 7), SetSpec(5, 150.0, 8.0, 14), SetSpec(5, 150.0, 8.0, 21))
        )
        val result = resolver.resolve(snapshot, target(), b5Selected(target()), 2)

        assertFalse(result.available)
        assertEquals(StrengthTrainingLoadUnavailableReason.CONTRADICTORY_CAPACITY_EVIDENCE, result.unavailableReasons.single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun repMaxCeilingCannotBeUsedAsTrainingLoad() {
        val reference = StrengthCapacityReference(
            owner = owner,
            estimated1RmKg = 100.0,
            lower80Kg = 90.0,
            upper80Kg = 110.0,
            posteriorLogVariance = .01,
            referenceDate = cutoff,
            posteriorObservationCount = 3,
            twoSidedObservationCount = 3,
            loadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD,
            curveProfileId = "bench",
            curveMatchLevel = com.training.trackplanner.analysis.strengthperformance.curve.CurveMatchLevel.EXACT_EXERCISE,
            curveAssignmentVersion = "test",
            evidenceIds = emptyList()
        )
        val ceiling = StrengthRepMaxCeiling(owner, 5, 87.5, 78.0, 96.0)
        StrengthTrainingLoadProposal(
            owner = owner,
            targetRepetitions = 5,
            setCount = 2,
            trainingLoadKg = ceiling.medianKg,
            targetRpe = 6.5,
            targetRirMean = 4.0,
            capacityReference = reference,
            repMaxCeiling = ceiling,
            derivation = StrengthTrainingLoadDerivation.EXACT_REFERENCE_CONSERVATIVE_CALIBRATION,
            confidence = StrengthTrainingLoadConfidence.LOW,
            roundingIncrementKg = .5,
            safetyCapKg = 87.5,
            safetyCapReasons = listOf("CAPACITY"),
            evidenceIds = emptyList(),
            reasonCodes = emptyList()
        )
    }

    private data class SetSpec(
        val reps: Int,
        val loadKg: Double,
        val rpe: Double?,
        val daysAgo: Long,
        val kind: RealizedStimulusKind = RealizedStimulusKind.STRENGTH_LIKE
    )

    private fun snapshot(
        specs: List<SetSpec>,
        posteriorLogVariance: Double = .01,
        posteriorObservationCount: Int = 3,
        twoSidedObservationCount: Int = 3
    ): PlanningHistorySnapshot {
        val observations = specs.mapIndexed { index, spec ->
            val date = cutoff.minusDays(spec.daysAgo)
            StimulusSetObservation(
                source = StimulusSourceRef(index.toLong() + 1, "c14-test-${index + 1}", index.toLong() + 1,
                    1, "c14-session-${spec.daysAgo}", date, key),
                activityKind = PlannedActivityKind.RESISTANCE,
                reps = spec.reps,
                weightKg = spec.loadKg,
                seconds = 0,
                rpe = spec.rpe,
                realizedPrescriptionClass = if (spec.reps in 1..6) RealizedStimulusClass.STRENGTH_LIKE
                    else RealizedStimulusClass.HYPERTROPHY_LIKE,
                facetProfileKey = key,
                classificationAuthority = StimulusClassificationAuthority.REVIEWED_CANONICAL,
                realizedStimulusClassification = RealizedStimulusClassification(
                    kind = spec.kind,
                    status = RealizedStimulusStatus.REALIZED,
                    authority = RealizedStimulusAuthority.REVIEWED,
                    resolvedLoadKg = spec.loadKg,
                    reference1RmKg = 100.0,
                    relativeIntensity = spec.loadKg / 100.0,
                    observedRpe = spec.rpe,
                    reasonCodes = listOf("REVIEWED_CANONICAL_REALIZED_STIMULUS")
                )
            )
        }
        val directRelation = ExercisePhysicalQualityRelation(
            relationId = "C14_TEST_STRENGTH",
            exerciseStableKey = key,
            qualityId = TrainableQuality.STRENGTH,
            relationLevel = StimulusCapabilityLevel.DIRECT_CAPABILITY,
            regionQualifier = PhysicalQualityRegion.SYSTEMIC,
            modeQualifier = PhysicalQualityMode.GENERAL,
            prescriptionDependent = true,
            provenance = "TEST",
            evidenceRelationKeys = setOf("C14_TEST"),
            reviewStatus = "REVIEWED",
            notes = "Test-only exact owner relation"
        )
        return PlanningHistorySnapshot(
            cutoff = cutoff,
            allConfirmedSets = emptyList(),
            exercises = mapOf(key to Exercise(key, "Barbell Bench Press", "STRENGTH")),
            metadata = emptyMap(),
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 2.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(
                posteriorMedianKg = 100.0,
                observationCount = posteriorObservationCount,
                source = "CANONICAL_EXERCISE_LOCAL_POSTERIOR_TEST",
                posteriorLogVariance = posteriorLogVariance,
                referenceDate = cutoff.minusDays(1),
                twoSidedObservationCount = twoSidedObservationCount,
                baselineEstablished = true
            )),
            strengthPerformanceRegistry = strengthRegistry,
            repetitionCurveRegistry = curves,
            rpeRirPolicy = rpeRir,
            stimulusExposureLedger = StimulusExposureLedger(
                facetProfilesByStableKey = mapOf(key to CanonicalStimulusFacetProfile(
                    stableKey = key,
                    physicalQualities = listOf(directRelation)
                )),
                setObservations = observations,
                courtObservations = emptyList(),
                cutoff = cutoff,
                historyStart = cutoff.minusDays(55)
            )
        )
    }

    private fun target(
        authority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
        quality: TrainableQuality = TrainableQuality.STRENGTH
    ) = StimulusQualityTarget(
        quality = quality,
        strategy = StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
        priority = TargetPriority.PRIMARY,
        numericAuthority = authority,
        baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        baselineConfidence = PlanningConfidence.MODERATE,
        weeklyDirectUnitsTarget = StimulusTargetRange(0.0, 0.0, 6.0),
        weeklyDirectSessionsTarget = StimulusTargetRange(0.0, 0.0, 2.0),
        exposureWeekDirectUnitsReference = null,
        exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(),
        evidence = emptyList()
    )

    private fun b5Selected(
        target: StimulusQualityTarget,
        selectedOwner: StimulusPrescriptionOwnerIdentity = owner
    ) = StimulusSelectedCandidate(
        stableKey = selectedOwner.stableKey,
        coveredTargetIds = setOf("QUALITY:${target.quality.name}"),
        primaryTargetId = "QUALITY:${target.quality.name}",
        selectionReasons = listOf("B4_TARGET_REQUESTED_IDENTITY"),
        currentPrescriptionCompatibility = "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6",
        targetSetsFromExistingPrescription = 2,
        selectionRole = selectedOwner.selectionRole
    )
}
