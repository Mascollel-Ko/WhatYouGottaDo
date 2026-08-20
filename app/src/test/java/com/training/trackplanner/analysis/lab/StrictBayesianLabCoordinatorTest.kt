package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.AnalysisWeekState
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState
import com.training.trackplanner.analysis.lab.weekly.WeeklyFeatureCell
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrictBayesianLabCoordinatorTest {
    @Test
    fun `snapshot change rejects a completed stale posterior`() = runTest {
        val snapshot = snapshot(1L)
        var currentFingerprint = snapshot.fingerprint
        val release = CompletableDeferred<Unit>()
        val service = fakeService { request, preflight ->
            release.await()
            StrictLabExecutionOutcome.Success(successResult(request))
        }
        val coordinator = StrictBayesianLabCoordinator(
            scope = this,
            freshSnapshot = { Result.success(snapshot) },
            currentSnapshotFingerprint = { currentFingerprint },
            service = service
        )

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        assertState<StrictBayesianLabUiState.Running>(coordinator.state.value)
        currentFingerprint = "newer-snapshot"
        release.complete(Unit)
        advanceUntilIdle()

        val failed = assertState<StrictBayesianLabUiState.Failed>(coordinator.state.value)
        assertEquals(StrictLabFailureCode.STALE_RESULT_REJECTED, failed.code)
    }

    @Test
    fun `selection change cancels in flight work and never publishes its result`() = runTest {
        val snapshot = snapshot(1L)
        val service = fakeService { _, _ ->
            awaitCancellation()
        }
        val coordinator = StrictBayesianLabCoordinator(
            scope = this,
            freshSnapshot = { Result.success(snapshot) },
            currentSnapshotFingerprint = { snapshot.fingerprint },
            service = service
        )
        val changed = REQUEST.copy(requestedHorizon = 1)

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        assertState<StrictBayesianLabUiState.Running>(coordinator.state.value)
        coordinator.updateRequest(changed)
        advanceUntilIdle()

        val ready = assertState<StrictBayesianLabUiState.PreflightReady>(coordinator.state.value)
        assertEquals(changed, ready.request)
    }

    @Test
    fun `wide but finite posterior remains a successful uncertain result`() = runTest {
        val snapshot = snapshot(1L)
        val coordinator = StrictBayesianLabCoordinator(
            scope = this,
            freshSnapshot = { Result.success(snapshot) },
            currentSnapshotFingerprint = { snapshot.fingerprint },
            service = fakeService { request, _ -> StrictLabExecutionOutcome.Success(successResult(request)) }
        )

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        advanceUntilIdle()

        val success = assertState<StrictBayesianLabUiState.Success>(coordinator.state.value)
        assertTrue(success.result.responses.single().points.single().low80 < 0.0)
        assertTrue(success.result.responses.single().points.single().high80 > 0.0)
    }

    private fun fakeService(
        execute: suspend (StrictLabAnalysisRequest, StrictLabPreflight) -> StrictLabExecutionOutcome
    ) = object : StrictBayesianLabService(Dispatchers.Unconfined) {
        override suspend fun preflight(
            snapshot: WeeklyAnalysisFeatureSnapshot,
            request: StrictLabAnalysisRequest
        ) = StrictLabPreflight(
            snapshot.fingerprint,
            snapshot.closedWeeks.first(),
            snapshot.closedWeeks.last(),
            snapshot.closedWeeks.size,
            blockers = emptyList(),
            warnings = emptyList()
        )

        override suspend fun execute(
            snapshot: WeeklyAnalysisFeatureSnapshot,
            request: StrictLabAnalysisRequest,
            preflight: StrictLabPreflight,
            onStage: (StrictLabExecutionStage) -> Unit
        ): StrictLabExecutionOutcome {
            onStage(StrictLabExecutionStage.SAMPLING_POSTERIOR)
            return execute(request, preflight)
        }
    }

    private inline fun <reified T> assertState(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got ${value?.javaClass?.simpleName}", value is T)
        return value as T
    }

    private fun successResult(request: StrictLabAnalysisRequest): StrictBayesianLabResult {
        val diagnostics = StrictPosteriorSummary(
            mean = 0.1,
            median = 0.1,
            lower80 = -2.0,
            upper80 = 2.2,
            rhat = 1.0,
            bulkEss = 500.0,
            tailEss = 500.0,
            mcseToSd = 0.04
        )
        return StrictBayesianLabResult(
            request,
            responses = listOf(
                StrictLabResponse(
                    Y,
                    "Fatigue",
                    listOf(StrictLabResponsePoint(1, 0.1, -2.0, 2.2, diagnostics))
                )
            ),
            officialLagProbability = mapOf(1 to 1.0),
            simplificationDiagnostics = emptyList(),
            summary = "불확실성이 큰 posterior입니다.",
            preparedInputFingerprint = "prepared",
            posteriorFingerprint = "posterior"
        )
    }

    private fun snapshot(revision: Long): WeeklyAnalysisFeatureSnapshot {
        val weeks = (0 until 12).map { START.plusWeeks(it.toLong()) }
        val descriptors = mapOf(
            X to AnalysisFeatureDescriptor(X, AnalysisSourceKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD), "Load", AnalysisFeatureFamily.TRAINING_FLOW),
            Y to AnalysisFeatureDescriptor(Y, AnalysisSourceKey.metric(TrendMetricId.FATIGUE_COMPOSITE), "Fatigue", AnalysisFeatureFamily.RECOVERY_CHECK_IN)
        )
        val cells = descriptors.keys.associateWith { key ->
            weeks.mapIndexed { index, week ->
                WeeklyFeatureCell(key, week, WeeklyCellState.OBSERVED, (index % 5).toDouble(), "fixture")
            }
        }
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks,
            weeks.associateWith { AnalysisWeekState.CLOSED },
            descriptors,
            cells,
            emptyList(),
            revision,
            "metadata-v1",
            setOf("calculator-v1")
        )
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2025, 1, 6)
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val REQUEST = StrictLabAnalysisRequest(X, listOf(Y), emptyList(), 2)
    }
}
