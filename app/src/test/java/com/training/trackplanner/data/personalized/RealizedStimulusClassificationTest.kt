package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealizedStimulusClassificationTest {
    private val date = LocalDate.of(2026, 9, 23)
    private val approvedStrengthKey = "barbell_back_squat"

    private fun input(
        reps: Int,
        key: String = if (reps <= 6) approvedStrengthKey else "canonical.exercise",
        load: Double? = 80.0,
        reference: Double? = 100.0,
        rpe: Double? = 8.0,
        impliedRir: Double? = null,
        quality: TrainableQuality = if (reps <= 6) TrainableQuality.STRENGTH else TrainableQuality.HYPERTROPHY
    ) = RealizedStimulusInput(
        stableKey = key, date = date, sessionStableKey = "session",
        activityKind = PlannedActivityKind.RESISTANCE, reps = reps, resolvedLoadKg = load,
        rpe = rpe, directQualities = setOf(quality), reviewedIdentity = true,
        reference1RmKg = reference, impliedRir = impliedRir,
        loadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD
    )

    private fun plannedStrengthInput(actualReps: Int, plannedReps: Int = 5) = input(actualReps, key = "barbell_bench_press").copy(
        strengthSetIntentEvidence = StrengthSetIntentEvidence(
            intent = StrengthSetIntent.PLANNED_STRENGTH,
            plannedReps = plannedReps,
            selectionRole = CANONICAL_STRENGTH_SELECTION_ROLE,
            sourceProgramStableKey = "exact-source-program",
            sourceItemId = "exact-source-item"
        )
    )

    @Test
    fun strengthSixBoundaryExamplesAreFailClosed() {
        assertTrue(RealizedStimulusClassifier.classify(input(6)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(5, load = 60.0, rpe = 5.0)).status)
        assertTrue(RealizedStimulusClassifier.classify(input(5, reference = null, rpe = 8.0)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(5, load = 60.0, rpe = 5.9)).status)
        assertTrue(RealizedStimulusClassifier.classify(input(5, rpe = null, impliedRir = 4.0)).isRealized)
        assertEquals(RealizedStimulusKind.STRENGTH_LIKE, RealizedStimulusClassifier.classify(input(1)).kind)
    }

    @Test
    fun everyApprovedIdentityUsesTheSameRealizedStrengthClassifier() {
        val examples = listOf(
            "barbell_back_squat" to 5,
            "ex_c5043892" to 6,
            "barbell_deadlift" to 3,
            "ex_e41f4c2b" to 5,
            "ex_e41e8dcf" to 5,
            "barbell_bench_press" to 5,
            "ex_3a7d3eda" to 6,
            "ex_32219f7a" to 5,
            "ex_79f3bdbe" to 6,
            "ex_bb4b4276" to 6
        )
        examples.forEach { (stableKey, reps) ->
            val result = RealizedStimulusClassifier.classify(input(reps, key = stableKey))
            assertTrue("$stableKey x $reps should be realized Strength", result.isRealized)
            assertEquals(RealizedStimulusKind.STRENGTH_LIKE, result.kind)
        }
    }

    @Test
    fun approvedMixedPrescriptionClassifiesEachSetIndependently() {
        val results = listOf(5, 5, 8).map { reps ->
            RealizedStimulusClassifier.classify(input(reps, key = "ex_e41f4c2b"))
        }
        assertEquals(RealizedStimulusKind.STRENGTH_LIKE, results[0].kind)
        assertEquals(RealizedStimulusKind.STRENGTH_LIKE, results[1].kind)
        assertEquals(RealizedStimulusKind.HYPERTROPHY_LIKE, results[2].kind)
    }

    @Test
    fun plannedStrengthIntentSurvivesNormalAndSmallOverperformance() {
        listOf(5 to StrengthExposureAssessment.REALIZED,
            6 to StrengthExposureAssessment.OVERPERFORMED,
            7 to StrengthExposureAssessment.OVERPERFORMED).forEach { (actual, expected) ->
            val result = RealizedStimulusClassifier.classify(plannedStrengthInput(actual))
            assertEquals(expected, result.strengthExposureAssessment)
            assertEquals(StrengthSetIntent.PLANNED_STRENGTH, result.strengthSetIntent)
            assertTrue(result.isRealized)
        }
    }

    @Test
    fun plannedStrengthLargeOverperformanceNeedsAnyOneReliableLoadOrEffortSignal() {
        val rpeOnly = RealizedStimulusClassifier.classify(plannedStrengthInput(8).copy(
            resolvedLoadKg = null, reference1RmKg = null, rpe = 8.0, impliedRir = null))
        assertEquals(StrengthExposureAssessment.OVERPERFORMED, rpeOnly.strengthExposureAssessment)

        val loadOnly = RealizedStimulusClassifier.classify(plannedStrengthInput(8).copy(
            resolvedLoadKg = 75.0, reference1RmKg = 100.0, rpe = null, impliedRir = null))
        assertEquals(StrengthExposureAssessment.OVERPERFORMED, loadOnly.strengthExposureAssessment)

        val noEvidence = RealizedStimulusClassifier.classify(plannedStrengthInput(8).copy(
            resolvedLoadKg = null, reference1RmKg = null, rpe = null, impliedRir = null))
        assertEquals(StrengthExposureAssessment.UNCERTAIN, noEvidence.strengthExposureAssessment)
        assertEquals(StrengthSetIntent.PLANNED_STRENGTH, noEvidence.strengthSetIntent)
        assertFalse(noEvidence.isRealized)

        val clearlyLow = RealizedStimulusClassifier.classify(plannedStrengthInput(12).copy(
            resolvedLoadKg = 50.0, reference1RmKg = 100.0, rpe = 4.0, impliedRir = 6.0))
        assertEquals(StrengthExposureAssessment.NOT_STRENGTH, clearlyLow.strengthExposureAssessment)
        assertEquals(StrengthSetIntent.PLANNED_STRENGTH, clearlyLow.strengthSetIntent)
    }

    @Test
    fun unplannedStrengthHistoryNeedsAnApprovedIdentityAndAnyOneEvidenceSignal() {
        val relativeLoadOnly = RealizedStimulusClassifier.classify(input(5, key = "barbell_deadlift")
            .copy(rpe = null, resolvedLoadKg = 75.0, reference1RmKg = 100.0))
        assertEquals(StrengthExposureAssessment.REALIZED, relativeLoadOnly.strengthExposureAssessment)

        val rpeOnly = RealizedStimulusClassifier.classify(input(5, key = "barbell_deadlift")
            .copy(rpe = 8.0, resolvedLoadKg = null, reference1RmKg = null))
        assertEquals(StrengthExposureAssessment.REALIZED, rpeOnly.strengthExposureAssessment)

        val insufficient = RealizedStimulusClassifier.classify(input(5, key = "barbell_deadlift")
            .copy(rpe = null, resolvedLoadKg = null, reference1RmKg = null, impliedRir = null))
        assertEquals(StrengthExposureAssessment.UNCERTAIN, insufficient.strengthExposureAssessment)
        assertTrue(insufficient.isUnclassified)

        listOf("barbell_reverse_curl", "machine_chest_press", "pull_up", "machine_shoulder_press",
            "lat_pulldown", "leg_press", "barbell_romanian_deadlift", "ex_6466fe77", "ex_7814843a")
            .forEach { key ->
                val result = RealizedStimulusClassifier.classify(input(5, key = key))
                assertEquals("$key must not receive Strength credit", StrengthExposureAssessment.NOT_STRENGTH,
                    result.strengthExposureAssessment)
                assertFalse("$key must not receive Strength credit", result.isRealized && result.kind == RealizedStimulusKind.STRENGTH_LIKE)
            }
    }

    @Test
    fun exactPlanLinkageChangesStrengthInterpretationButRepSimilarityDoesNot() {
        val plannedSeven = RealizedStimulusClassifier.classify(plannedStrengthInput(7))
        assertEquals(StrengthExposureAssessment.OVERPERFORMED, plannedSeven.strengthExposureAssessment)
        val unplannedSeven = RealizedStimulusClassifier.classify(input(7, key = "barbell_bench_press"))
        assertFalse(unplannedSeven.kind == RealizedStimulusKind.STRENGTH_LIKE)
        assertNotEquals(StrengthSetIntent.PLANNED_STRENGTH, unplannedSeven.strengthSetIntent)
    }

    @Test
    fun plannedStrengthOverperformanceIsNotAutomaticallyHypertrophyCredit() {
        val key = "barbell_bench_press"
        val row = PlanningSetRecord(
            date, key, "Bench Press", "RESISTANCE", 1, 8, 40.0, 0, null,
            StrengthSetIntentEvidence(
                intent = StrengthSetIntent.PLANNED_STRENGTH,
                plannedReps = 5,
                selectionRole = CANONICAL_STRENGTH_SELECTION_ROLE
            )
        )
        val metadata = com.training.trackplanner.data.RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("HYPERTROPHY_VOLUME")
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = date,
            allConfirmedSets = listOf(row),
            exercises = mapOf(key to com.training.trackplanner.data.Exercise(key, key, "RESISTANCE")),
            metadata = mapOf(key to metadata),
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 1.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences()
        )

        assertEquals(0.0, snapshot.hypertrophyStimulus(row), 0.0)
    }

    @Test
    fun lowRepPowerAndTaskWorkDoNotBecomeStrength() {
        listOf("power_clean", "ex_314df428", "ex_33841b88").forEach { key ->
            val result = RealizedStimulusClassifier.classify(input(3, key = key, quality = TrainableQuality.POWER))
            assertFalse("$key must remain outside Strength", result.isRealized && result.kind == RealizedStimulusKind.STRENGTH_LIKE)
        }
    }

    @Test
    fun approvedCapabilityAtSevenRepsAndAnyIdentityAtZeroRepsDoNotReceiveStrengthCredit() {
        val seven = RealizedStimulusClassifier.classify(input(7, key = "barbell_back_squat"))
        assertEquals(RealizedStimulusKind.HYPERTROPHY_LIKE, seven.kind)
        assertFalse(seven.kind == RealizedStimulusKind.STRENGTH_LIKE)

        val invalid = RealizedStimulusClassifier.classify(input(0, key = "barbell_back_squat"))
        assertFalse(invalid.kind == RealizedStimulusKind.STRENGTH_LIKE)
        assertFalse(invalid.isRealized)
    }

    @Test
    fun hypertrophyFiveBoundaryExamplesUseItsOwnEffortGate() {
        assertTrue(RealizedStimulusClassifier.classify(input(7)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(8, rpe = 6.9)).status)
        assertTrue(RealizedStimulusClassifier.classify(input(8, rpe = null, impliedRir = 3.0)).isRealized)
        assertTrue(RealizedStimulusClassifier.classify(input(8, reference = null, rpe = 8.0)).isRealized)
        assertFalse(RealizedStimulusClassifier.classify(input(8, load = null)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(16)).status)
    }

    @Test
    fun hypertrophyUnknownEffortRemainsUnclassified() {
        val result = RealizedStimulusClassifier.classify(input(8, rpe = null, impliedRir = null))
        assertEquals(RealizedStimulusStatus.UNCLASSIFIED, result.status)
        assertEquals(listOf("EFFORT_AUTHORITY_UNAVAILABLE"), result.reasonCodes)
        assertFalse(result.isRealized)
    }

    @Test
    fun reviewedNonRealizationIsDistinctFromUnclassified() {
        val warmup = RealizedStimulusClassifier.classify(input(8).copy(reviewedNonRealization = true))
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION, warmup.status)
        assertEquals(RealizedStimulusStatus.UNCLASSIFIED, RealizedStimulusClassification.UNCLASSIFIED.status)
        assertEquals(RealizedStimulusKind.NONE, warmup.kind)
    }

    @Test
    fun capabilityProxyCannotBecomeRealizedPrescription() {
        val power = RealizedStimulusClassifier.classify(input(5, key = "power_clean", quality = TrainableQuality.POWER).copy(
            activityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        ))
        assertEquals(RealizedStimulusStatus.UNCLASSIFIED, power.status)
        assertFalse(power.isRealized)
    }

    @Test
    fun lowRepsOnAnUnapprovedStrengthIdentityDoNotCountAsStrength() {
        val result = RealizedStimulusClassifier.classify(input(5, key = "ex_8e4bf08e"))
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION, result.status)
        assertEquals(listOf("STRENGTH_CAPABILITY_NOT_APPROVED"), result.reasonCodes)
        assertFalse(result.isRealized)
    }

    @Test
    fun referenceIndexUsesExactPriorAndEarlierPosteriorWithoutFutureLeakage() {
        val index = CanonicalStrengthReferenceIndex(listOf(
            row("prior", "2026-09-22", "key", 4.0, true, posterior = 4.4),
            row("future", "2026-09-24", "key", 5.0, true),
            row("same", "2026-09-23", "session", 4.6, true)
        ))
        assertEquals(kotlin.math.exp(4.6), index.reference1RmKg("key", date, "session")!!, 0.001)
        assertEquals(kotlin.math.exp(4.4), index.reference1RmKg("key", date, "other")!!, 0.001)
        assertEquals(null, index.reference1RmKg("key", LocalDate.of(2026, 9, 21), "other"))
    }

    private fun row(event: String, date: String, session: String, prior: Double, baseline: Boolean, posterior: Double = prior) =
        StrengthExercisePerformanceHistoryEntity(
            revisionKey = "revision", eventUuid = event, sessionKey = session, sessionDate = date,
            exerciseStableKey = "key", priorLogMean = prior, priorLogVariance = 0.1,
            sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true,
            innovationResidualLog = null, innovationVariance = null, posteriorLogMean = posterior,
            posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1,
            baselineEstablishedBefore = baseline, baselineEstablishedAfter = baseline,
            proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "test",
            curveVersion = "test", rirPolicyVersion = "test", evidenceFingerprint = "test", createdAt = 1L
        )
}
