package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesAnalysisExecutionTest {
    private val request = TimeSeriesAnalysisRequest(
        TrendMetricId.BADMINTON_TRAINING,
        listOf(TrendMetricId.FATIGUE_COMPOSITE),
        emptyList(),
        2
    )
    private val series = fixture(32)

    @Test
    fun coordinatorTransitionsIdleRunningSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, stage ->
            stage(TimeSeriesExecutionStage.BUILDING_RESPONSE)
            success(req)
        })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        assertTrue(coordinator.state.value is TimeSeriesAnalysisUiState.Running)
        val completed = coordinator.state.awaitSuccess()

        assertEquals(request, completed.request)
        scope.cancel()
    }

    @Test
    fun coordinatorTransitionsRunningUnavailable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, _ -> unavailable(req) })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        val unavailable = withTimeout(2_000) {
            coordinator.state.filterIsInstance<TimeSeriesAnalysisUiState.Unavailable>().first()
        }

        assertEquals(TimeSeriesUnavailableReason.ALL_ESTIMATORS_FAILED, unavailable.reason)
        scope.cancel()
    }

    @Test
    fun unexpectedExceptionBecomesFailedWithDiagnosticId() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { _, _, _ -> error("broken estimator") })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        val failed = withTimeout(2_000) {
            coordinator.state.filterIsInstance<TimeSeriesAnalysisUiState.Failed>().first()
        }

        assertTrue(failed.diagnosticId.startsWith("TS-"))
        assertFalse(failed.message.contains("broken estimator"))
        scope.cancel()
    }

    @Test
    fun cancellationDoesNotBecomeFailed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CountDownLatch(1)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, stage ->
            started.countDown()
            repeat(100) {
                Thread.sleep(5)
                stage(TimeSeriesExecutionStage.FITTING_MODEL)
            }
            success(req)
        })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        assertTrue(started.await(1, TimeUnit.SECONDS))
        coordinator.cancel()
        Thread.sleep(50)

        assertEquals(TimeSeriesAnalysisUiState.Idle, coordinator.state.value)
        scope.cancel()
    }

    @Test
    fun duplicateAnalyzePressStartsOneJob() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, _ ->
            calls.incrementAndGet()
            Thread.sleep(100)
            success(req)
        })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        coordinator.analyze(request)
        coordinator.state.awaitSuccess()

        assertEquals(1, calls.get())
        scope.cancel()
    }

    @Test
    fun staleRequestCannotOverwriteNewerResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val releaseOld = CountDownLatch(1)
        val oldStarted = CountDownLatch(1)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, _ ->
            if (req.requestedHorizon == 2) {
                oldStarted.countDown()
                releaseOld.await(2, TimeUnit.SECONDS)
            }
            success(req)
        })
        val newer = request.copy(requestedHorizon = 1)

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        assertTrue(oldStarted.await(1, TimeUnit.SECONDS))
        coordinator.updateRequest(newer, series)
        coordinator.state.awaitPreflight(newer)
        coordinator.analyze(newer)
        val completed = coordinator.state.awaitSuccess()
        releaseOld.countDown()
        Thread.sleep(50)

        assertEquals(1, completed.request.requestedHorizon)
        assertEquals(1, (coordinator.state.value as TimeSeriesAnalysisUiState.Success).request.requestedHorizon)
        scope.cancel()
    }

    @Test
    fun changingVariablesClearsPreviousSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TimeSeriesAnalysisCoordinator(scope, fakeService { req, _, _ -> success(req) })

        coordinator.updateRequest(request, series)
        coordinator.state.awaitPreflight()
        coordinator.analyze(request)
        coordinator.state.awaitSuccess()
        coordinator.updateRequest(request.copy(requestedHorizon = 1), series)

        assertFalse(coordinator.state.value is TimeSeriesAnalysisUiState.Success)
        scope.cancel()
    }

    @Test
    fun heavyAnalysisExecutesOffCallingThreadAndReportsStages() = runBlocking {
        val caller = Thread.currentThread().name
        val worker = AtomicReference<String>()
        val service = fakeService { req, _, stage ->
            worker.set(Thread.currentThread().name)
            TimeSeriesExecutionStage.entries.drop(1).forEach(stage)
            success(req)
        }

        val outcome = service.execute(request, series, readyPreflight())

        assertTrue(outcome is TimeSeriesExecutionOutcome.Success)
        assertNotEquals(caller, worker.get())
        val performance = (outcome as TimeSeriesExecutionOutcome.Success).performance
        assertTrue(TimeSeriesExecutionStage.FITTING_MODEL in performance.stageDurationsMillis)
        assertTrue(performance.estimatedModelFitUpperBound > 0)
    }

    private fun fakeService(
        analyze: (TimeSeriesAnalysisRequest, Map<TrendMetricId, List<TrendDataPoint>>, (TimeSeriesExecutionStage) -> Unit) -> BayesianTimeSeriesResult
    ) = TimeSeriesAnalysisService(
        preflightBlock = { _, _ -> readyPreflight() },
        analysisBlock = analyze
    )

    private fun readyPreflight() = TimeSeriesPreflight(
        status = TimeSeriesPreflightStatus.READY,
        availableFrom = LocalDate.parse("2026-01-05"),
        availableUntil = LocalDate.parse("2026-08-10"),
        alignedWeeks = 32,
        transformedUsableWeeks = 32,
        requestedEstimatorRows = 29,
        requiredMinimumRows = 24,
        maximumFeasibleHorizon = 2,
        blockers = emptyList(),
        warnings = emptyList()
    )

    private fun success(request: TimeSeriesAnalysisRequest) = BayesianTimeSeriesResult(
        request = request,
        model = BayesianTimeSeriesModel.BAYESIAN_LOCAL_PROJECTION,
        responses = listOf(
            BayesianResponseIrf(
                TrendMetricId.FATIGUE_COMPOSITE,
                listOf(BayesianIrfPoint(0, 0.2, 0.1, 0.3, 28))
            )
        ),
        usedHorizon = request.requestedHorizon,
        alignment = null,
        integrationDiagnostics = emptyList(),
        cointegration = null,
        lagPosterior = null,
        automaticEndogenous = emptyList(),
        automaticSelectionDiagnostics = emptyList(),
        choleskyOrder = emptyList(),
        choleskySensitivity = null,
        transformations = emptyMap(),
        confidence = AnalysisConfidence.MEDIUM,
        warnings = emptyList(),
        summary = "exploratory result"
    )

    private fun unavailable(request: TimeSeriesAnalysisRequest) = success(request).copy(
        model = BayesianTimeSeriesModel.UNAVAILABLE,
        responses = emptyList(),
        usedHorizon = 0,
        unavailableReason = TimeSeriesUnavailableReason.ALL_ESTIMATORS_FAILED,
        summary = "no estimator passed"
    )

    private fun fixture(count: Int): Map<TrendMetricId, List<TrendDataPoint>> {
        val start = LocalDate.parse("2026-01-05")
        return mapOf(
            TrendMetricId.BADMINTON_TRAINING to (0 until count).map { index ->
                TrendDataPoint(start.plusWeeks(index.toLong()), (index % 7).toDouble())
            },
            TrendMetricId.FATIGUE_COMPOSITE to (0 until count).map { index ->
                TrendDataPoint(start.plusWeeks(index.toLong()), (index % 5).toDouble())
            }
        )
    }

    private suspend fun kotlinx.coroutines.flow.StateFlow<TimeSeriesAnalysisUiState>.awaitPreflight(
        request: TimeSeriesAnalysisRequest? = null
    ): TimeSeriesAnalysisUiState.PreflightReady = withTimeout(2_000) {
        filterIsInstance<TimeSeriesAnalysisUiState.PreflightReady>()
            .first { request == null || it.request == request }
    }

    private suspend fun kotlinx.coroutines.flow.StateFlow<TimeSeriesAnalysisUiState>.awaitSuccess(): TimeSeriesAnalysisUiState.Success =
        withTimeout(2_000) { filterIsInstance<TimeSeriesAnalysisUiState.Success>().first() }
}
