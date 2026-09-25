package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealizedStimulusClassificationTest {
    private val date = LocalDate.of(2026, 9, 23)

    private fun input(
        reps: Int,
        load: Double? = 80.0,
        reference: Double? = 100.0,
        rpe: Double? = 8.0,
        impliedRir: Double? = null,
        quality: TrainableQuality = if (reps <= 6) TrainableQuality.STRENGTH else TrainableQuality.HYPERTROPHY
    ) = RealizedStimulusInput(
        stableKey = "canonical.exercise", date = date, sessionStableKey = "session",
        activityKind = PlannedActivityKind.RESISTANCE, reps = reps, resolvedLoadKg = load,
        rpe = rpe, directQualities = setOf(quality), reviewedIdentity = true,
        reference1RmKg = reference, impliedRir = impliedRir,
        loadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD
    )

    @Test
    fun strengthSixBoundaryExamplesAreFailClosed() {
        assertTrue(RealizedStimulusClassifier.classify(input(6)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(5, load = 69.9)).status)
        assertFalse(RealizedStimulusClassifier.classify(input(5, reference = null)).isRealized)
        assertEquals(RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
            RealizedStimulusClassifier.classify(input(5, rpe = 5.9)).status)
        assertTrue(RealizedStimulusClassifier.classify(input(5, rpe = null, impliedRir = 4.0)).isRealized)
        assertEquals(RealizedStimulusKind.STRENGTH_LIKE, RealizedStimulusClassifier.classify(input(1)).kind)
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
        val power = RealizedStimulusClassifier.classify(input(5, quality = TrainableQuality.POWER))
        assertEquals(RealizedStimulusStatus.UNCLASSIFIED, power.status)
        assertFalse(power.isRealized)
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
