package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import com.training.trackplanner.data.StrengthProxyTransferHistoryEntity
import com.training.trackplanner.data.StrengthAnalysisLifecycleResult
import com.training.trackplanner.data.StrengthAnalysisLifecycleStatus
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.StrengthPosteriorEventEntity
import com.training.trackplanner.data.StrengthPosteriorEvidenceEntity
import com.training.trackplanner.data.StrengthPosteriorHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistentStrengthPerformanceSummaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)

    @Test
    fun `summary is registry driven and reads persisted state without rebuilding observations`() {
        val state = StrengthPosteriorModel.toEntity(
            StrengthPosteriorModel.initialState(registry, initialProfile = null),
            now = 10L
        )
        val summary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = registry,
            modelState = state,
            history = listOf(history()),
            events = listOf(event()),
            evidence = listOf(evidence()),
            curvePosteriors = emptyList(),
            currentBodyWeightKg = 82.0,
            bootstrapProvenance = "completed|INITIAL_INSTALLATION_BOOTSTRAP|1",
            backupRestorationProvenance = "PERSISTED_POSTERIOR_BACKUP|2"
        )

        assertEquals(
            registry.targets().map { target -> target.targetKey.value },
            summary.targets.map(PersistentStrengthTargetSummary::targetKey)
        )
        assertEquals(4, summary.targets.size)
        assertEquals(1, summary.eventCount)
        assertEquals("PERSISTED_POSTERIOR_BACKUP|2", summary.backupRestorationProvenance)
        assertNotNull(summary.modelStateFingerprint)
        assertTrue(summary.numericalDiagnostics.isEmpty())
    }

    @Test
    fun `weighted pull-up history keeps processing-time load snapshots immutable`() {
        val summary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = registry,
            modelState = null,
            history = listOf(history()),
            events = listOf(event()),
            evidence = listOf(evidence()),
            curvePosteriors = emptyList(),
            currentBodyWeightKg = 90.0,
            bootstrapProvenance = null,
            backupRestorationProvenance = null
        )
        val target = summary.targets.single { item -> item.targetKey == StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value }
        val point = target.history.single()

        assertEquals(80.0, point.bodyWeightKgAtProcessing!!, 0.0)
        assertEquals(20.0, point.rawAddedWeightKgAtProcessing!!, 0.0)
        assertEquals(100.0, point.totalLoadKgAtProcessing!!, 0.0)
        assertEquals(90.0, target.currentBodyWeightKg!!, 0.0)
        assertEquals(10.0, target.currentAddedWeightKg!!, 0.0)
        assertEquals("EXACT_DATE", point.bodyWeightSource)
    }

    @Test
    fun `relevant session count includes every distinct target event and failures stay separate`() {
        val history = (1..7).map { index ->
            history().copy(
                eventUuid = "event-$index",
                sessionDate = "2026-07-${index.toString().padStart(2, '0')}",
                evidenceFingerprint = "evidence-$index"
            )
        }
        val evidence = (1..7).map { index ->
            evidence().copy(
                eventUuid = "event-$index",
                sessionDate = "2026-07-${index.toString().padStart(2, '0')}",
                evidenceFingerprint = "evidence-$index",
                observationType = if (index == 7) {
                    StrengthObservationType.FAILURE_UPPER_CENSORED.name
                } else {
                    StrengthObservationType.STRONG_NRM.name
                }
            )
        }
        val summary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = registry,
            modelState = null,
            history = history,
            events = (1..7).map { index ->
                event().copy(
                    eventUuid = "event-$index",
                    sessionDate = "2026-07-${index.toString().padStart(2, '0')}",
                    completionFingerprint = "completion-$index",
                    evidenceFingerprint = "evidence-$index"
                )
            },
            evidence = evidence,
            curvePosteriors = emptyList(),
            currentBodyWeightKg = 90.0,
            bootstrapProvenance = null,
            backupRestorationProvenance = null
        )
        val target = summary.targets.single { item ->
            item.targetKey == StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value
        }

        assertEquals(7, target.relevantSessionCount)
        assertEquals(6, target.strongNrmObservationCount)
        assertEquals(1, target.failureObservationCount)
    }

    @Test
    fun `summary distinguishes known RPE local innovation from direct absolute evidence`() {
        val proxyEvidence = evidence().copy(
            exerciseStableKey = "ex_a61f1e96",
            exerciseNameAtProcessing = "Incline dumbbell press",
            directTargetKey = null,
            observationType = StrengthObservationType.RPE_MIXTURE_OBSERVATION.name
        )
        val targetHistory = history().copy(
            targetKey = StrengthPerformanceRegistry.BENCH_PRESS.value,
            directObservedLoad = null,
            directObservationType = "NONE"
        )
        val localHistory = StrengthExercisePerformanceHistoryEntity(
            revisionKey = "strength-revision-3.0.0",
            eventUuid = proxyEvidence.eventUuid,
            sessionKey = proxyEvidence.sessionKey,
            sessionDate = proxyEvidence.sessionDate,
            exerciseStableKey = proxyEvidence.exerciseStableKey,
            priorLogMean = kotlin.math.ln(52.0),
            priorLogVariance = 0.08,
            sessionLikelihoodLogMean = kotlin.math.ln(60.0),
            sessionLikelihoodLogVariance = 0.04,
            sessionLikelihoodProper = true,
            innovationResidualLog = kotlin.math.ln(60.0 / 52.0),
            innovationVariance = 0.12,
            posteriorLogMean = kotlin.math.ln(56.0),
            posteriorLogVariance = 0.05,
            posteriorMeanIncrementLog = kotlin.math.ln(56.0 / 52.0),
            transitionDays = 7,
            baselineEstablishedBefore = true,
            baselineEstablishedAfter = true,
            proxyTransferEligible = true,
            proxyTransferApplied = true,
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
            rirPolicyVersion = "strength-rpe-rir-policy-1.0.0",
            evidenceFingerprint = proxyEvidence.evidenceFingerprint,
            createdAt = 2L
        )
        val proxy = StrengthProxyTransferHistoryEntity(
            revisionKey = localHistory.revisionKey,
            eventUuid = localHistory.eventUuid,
            sessionDate = localHistory.sessionDate,
            exerciseStableKey = localHistory.exerciseStableKey,
            targetKey = targetHistory.targetKey,
            innovationResidualLog = checkNotNull(localHistory.innovationResidualLog),
            innovationVariance = checkNotNull(localHistory.innovationVariance),
            transferCoefficient = 0.45,
            transferLogVariance = 0.3,
            orderedSharedFactorKeys = "strength.factor.horizontal_press",
            sharedLoadingVectorEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(0.45)),
            targetSpecificContribution = 0.0,
            applied = true,
            exclusionReason = null,
            proxyRegistryVersion = StrengthPerformanceRegistry.PROXY_CONFIG_VERSION,
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            transferFingerprint = "proxy-1",
            createdAt = 2L
        )
        val summary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = registry,
            modelState = null,
            history = listOf(targetHistory),
            events = listOf(event()),
            evidence = listOf(proxyEvidence),
            curvePosteriors = emptyList(),
            currentBodyWeightKg = null,
            bootstrapProvenance = null,
            backupRestorationProvenance = null,
            localHistory = listOf(localHistory),
            proxyTransfers = listOf(proxy)
        )
        val bench = summary.targets.single { target ->
            target.targetKey == StrengthPerformanceRegistry.BENCH_PRESS.value
        }

        assertEquals(1, bench.knownRpeObservationCount)
        assertEquals(1, bench.proxyObservationCount)
        assertEquals(1, bench.localExerciseDetails.size)
        assertEquals("Incline dumbbell press", bench.localExerciseDetails.single().exerciseName)
        assertEquals(0, summary.targetSpecificProxyViolationCount)
    }

    @Test
    fun `failed current lifecycle suppresses legacy posterior values`() {
        val summary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = registry,
            modelState = StrengthPosteriorModel.toEntity(
                StrengthPosteriorModel.initialState(registry, initialProfile = null),
                now = 10L
            ),
            history = listOf(history()),
            events = listOf(event()),
            evidence = listOf(evidence()),
            curvePosteriors = emptyList(),
            currentBodyWeightKg = 82.0,
            bootstrapProvenance = "legacy-bootstrap",
            backupRestorationProvenance = "legacy-restore",
            activeRevision = StrengthModelRevisionPolicy.legacy(1L).copy(
                status = StrengthModelRevisionPolicy.STATUS_ACTIVE
            ),
            lifecycle = StrengthAnalysisLifecycleResult(
                StrengthAnalysisLifecycleStatus.REBUILD_FAILED,
                "REBUILD_EVENT_FAILED",
                "실패한 운동일: 2026-07-01"
            )
        )

        assertEquals(StrengthAnalysisLifecycleStatus.REBUILD_FAILED, summary.lifecycleStatus)
        assertEquals("REBUILD_EVENT_FAILED", summary.lifecycleDiagnosticCode)
        assertEquals("실패한 운동일: 2026-07-01", summary.lifecycleDiagnosticMessage)
        assertTrue(summary.targets.isEmpty())
        assertEquals(0, summary.eventCount)
        assertEquals(null, summary.activeRevisionKey)
    }

    private fun event() = StrengthPosteriorEventEntity(
        eventUuid = "event-1",
        sessionKey = "date:2026-07-01",
        sessionDate = "2026-07-01",
        completionFingerprint = "completion-1",
        status = "PROCESSED",
        creationReason = "LIVE_SESSION_COMPLETION",
        confirmedSetCount = 1,
        createdAt = 1L,
        processedAt = 2L,
        modelVersion = StrengthPosteriorModel.MODEL_VERSION,
        curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
        factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
        evidenceFingerprint = "evidence-1"
    )

    private fun history() = StrengthPosteriorHistoryEntity(
        eventUuid = "event-1",
        targetKey = StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value,
        sessionDate = "2026-07-01",
        priorMedian = 95.0,
        priorLow50 = 90.0,
        priorHigh50 = 100.0,
        priorLow80 = 85.0,
        priorHigh80 = 105.0,
        priorLow95 = 80.0,
        priorHigh95 = 110.0,
        posteriorMedian = 100.0,
        posteriorLow50 = 96.0,
        posteriorHigh50 = 104.0,
        posteriorLow80 = 92.0,
        posteriorHigh80 = 108.0,
        posteriorLow95 = 88.0,
        posteriorHigh95 = 112.0,
        directObservedLoad = 100.0,
        directObservationType = StrengthObservationType.DIRECT_1RM.name,
        sessionObservationMedian = 100.0,
        sessionObservationLow80 = 98.0,
        sessionObservationHigh80 = 102.0,
        posteriorMeanChange = 5.0,
        posteriorVarianceBefore = 0.04,
        posteriorVarianceAfter = 0.03,
        intervalWidthChange80 = -4.0,
        predictivePercentile = 0.60,
        standardizedSurprise = 0.25,
        modelVersion = StrengthPosteriorModel.MODEL_VERSION,
        factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
        curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
        targetConfigVersion = StrengthPerformanceRegistry.TARGET_CONFIG_VERSION,
        evidenceFingerprint = "evidence-1",
        sourceEvidenceStatus = "AVAILABLE",
        sourceSetCountAtProcessing = 1,
        bodyWeightKgAtProcessing = 80.0,
        rawAddedWeightKgAtProcessing = 20.0,
        bodyWeightSource = "EXACT_DATE",
        curveProfileId = "curve.general.v1",
        curveMatchLevel = "GENERAL_TARGET_POLICY",
        curveCalibrationStatus = "CANONICAL_ONLY",
        createdAt = 2L
    )

    private fun evidence() = StrengthPosteriorEvidenceEntity(
        evidenceFingerprint = "evidence-1",
        eventUuid = "event-1",
        sessionKey = "date:2026-07-01",
        sessionDate = "2026-07-01",
        exerciseStableKey = "ex_e41f4c2b",
        exerciseNameAtProcessing = "중량 턱걸이",
        directTargetKey = StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value,
        observationType = StrengthObservationType.DIRECT_1RM.name,
        capacityMedianKg = 100.0,
        capacityLow80Kg = 98.0,
        capacityHigh80Kg = 102.0,
        lowerBoundOnly = 0,
        logVariance = 0.01,
        directObservedLoadKg = 100.0,
        bodyWeightKg = 80.0,
        rawAddedWeightKg = 20.0,
        bodyWeightSource = "EXACT_DATE",
        curveProfileId = "curve.general.v1",
        curveMatchLevel = "GENERAL_TARGET_POLICY",
        curveVarianceMultiplier = 1.25,
        curveSubjectKey = "target:strength.weighted_pull_up",
        sourceSetIdsEncoded = "1",
        strongObservationCount = 1,
        diagnosticsEncoded = "",
        createdAt = 2L
    )
}
