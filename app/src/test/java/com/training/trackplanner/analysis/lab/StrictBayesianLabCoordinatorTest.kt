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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrictBayesianLabCoordinatorTest {
    @Test
    fun `one analyze action runs the automatic service path`() = runTest {
        val snapshot = snapshot(1L)
        val attempts = mutableListOf<Int>()
        val coordinator = coordinator(snapshot) { request, _, attempt ->
            attempts += attempt
            StrictLabExecutionOutcome.Available(successResult(request))
        }

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        advanceUntilIdle()

        assertEquals(listOf(0), attempts)
        assertState<StrictBayesianLabUiState.Available>(coordinator.state.value)
    }

    @Test
    fun `snapshot change makes the completed result unavailable as stale`() = runTest {
        val snapshot = snapshot(1L)
        var currentFingerprint = snapshot.fingerprint
        val release = CompletableDeferred<Unit>()
        val coordinator = StrictBayesianLabCoordinator(
            scope = this,
            freshSnapshot = { Result.success(snapshot) },
            currentSnapshotFingerprint = { currentFingerprint },
            service = fakeService { request, _, _ ->
                release.await()
                StrictLabExecutionOutcome.Available(successResult(request))
            }
        )

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        currentFingerprint = "newer-snapshot"
        release.complete(Unit)
        advanceUntilIdle()

        val unavailable = assertState<StrictBayesianLabUiState.Unavailable>(coordinator.state.value)
        assertEquals(StrictLabFailureCode.STALE_RESULT_REJECTED, unavailable.code)
    }

    @Test
    fun `selection change cancels in-flight work`() = runTest {
        val snapshot = snapshot(1L)
        val coordinator = coordinator(snapshot) { _, _, _ -> awaitCancellation() }
        val changed = REQUEST.copy(requestedHorizon = 1)

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        assertState<StrictBayesianLabUiState.Running>(coordinator.state.value)
        coordinator.updateRequest(changed)
        advanceUntilIdle()

        assertEquals(changed, assertState<StrictBayesianLabUiState.PreflightReady>(coordinator.state.value).request)
    }

    @Test
    fun `wide but finite posterior remains available`() = runTest {
        val snapshot = snapshot(1L)
        val coordinator = coordinator(snapshot) { request, _, _ ->
            StrictLabExecutionOutcome.Available(successResult(request))
        }

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        advanceUntilIdle()

        val available = assertState<StrictBayesianLabUiState.Available>(coordinator.state.value)
        assertTrue(available.result.responses.single().points.single().low80 < 0.0)
        assertTrue(available.result.responses.single().points.single().high80 > 0.0)
    }

    @Test
    fun `retry increments attempt without a user-selected reliability mode`() = runTest {
        val snapshot = snapshot(1L)
        val attempts = mutableListOf<Int>()
        val coordinator = coordinator(snapshot) { _, _, attempt ->
            attempts += attempt
            unavailableOutcome(attempt)
        }

        coordinator.updateRequest(REQUEST)
        advanceUntilIdle()
        coordinator.analyze(REQUEST)
        advanceUntilIdle()
        coordinator.retry()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), attempts)
        assertEquals(1, assertState<StrictBayesianLabUiState.Unavailable>(coordinator.state.value).failure.retryAttempt)
    }

    private fun TestScope.coordinator(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        execute: suspend (StrictLabAnalysisRequest, StrictLabPreflight, Int) -> StrictLabExecutionOutcome
    ) = StrictBayesianLabCoordinator(
        scope = this,
        freshSnapshot = { Result.success(snapshot) },
        currentSnapshotFingerprint = { snapshot.fingerprint },
        service = fakeService(execute)
    )

    private fun fakeService(
        execute: suspend (StrictLabAnalysisRequest, StrictLabPreflight, Int) -> StrictLabExecutionOutcome
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
            retryAttempt: Int,
            onStage: (StrictLabExecutionStage) -> Unit
        ): StrictLabExecutionOutcome {
            onStage(StrictLabExecutionStage.SAMPLING_POSTERIOR)
            return execute(request, preflight, retryAttempt)
        }
    }

    private inline fun <reified T> assertState(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got ${value?.javaClass?.simpleName}", value is T)
        return value as T
    }

    private fun successResult(request: StrictLabAnalysisRequest): StrictBayesianLabResult {
        val diagnostics = StrictPosteriorSummary(0.1, 0.1, -2.0, 2.2, 1.0, 500.0, 500.0, 0.04)
        return StrictBayesianLabResult(
            request = request,
            responses = listOf(
                StrictLabResponse(Y, "Fatigue", listOf(StrictLabResponsePoint(1, 0.1, -2.0, 2.2, diagnostics)))
            ),
            officialLagProbability = mapOf(1 to 1.0),
            simplificationDiagnostics = emptyList(),
            summary = "불확실성이 큰 posterior입니다.",
            preparedInputFingerprint = "prepared",
            posteriorFingerprint = "posterior"
        )
    }

    private fun unavailableOutcome(attempt: Int): StrictLabExecutionOutcome.Unavailable =
        StrictLabExecutionOutcome.Unavailable(
            StrictFailureDiagnostics(
                code = StrictLabFailureCode.NUMERICAL_SPD_FAILURE,
                stage = StrictFailureStage.NUMERICAL,
                primaryReason = "No finite posterior",
                preparedInputFingerprint = "prepared-input",
                retryAttempt = attempt
            )
        )

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
