package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class TimeSeriesAnalysisCoordinator(
    private val scope: CoroutineScope,
    private val service: TimeSeriesAnalysisService = TimeSeriesAnalysisService()
) {
    private val mutableState = MutableStateFlow<TimeSeriesAnalysisUiState>(TimeSeriesAnalysisUiState.Idle)
    val state: StateFlow<TimeSeriesAnalysisUiState> = mutableState.asStateFlow()

    private var activeJob: Job? = null
    private var requestToken = 0L
    private var snapshot: InputSnapshot? = null

    fun updateRequest(
        request: TimeSeriesAnalysisRequest,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ) {
        val next = InputSnapshot(request.immutableCopy(), metricSeries.immutableCopy())
        if (next == snapshot && mutableState.value !is TimeSeriesAnalysisUiState.Idle) return
        requestToken += 1
        activeJob?.cancel()
        snapshot = next
        mutableState.value = TimeSeriesAnalysisUiState.Idle
        val token = requestToken
        activeJob = scope.launch {
            val preflight = service.preflight(next.request, next.metricSeries)
            if (token == requestToken) {
                mutableState.value = TimeSeriesAnalysisUiState.PreflightReady(next.request, preflight)
            }
        }
    }

    fun analyze(request: TimeSeriesAnalysisRequest) {
        if (mutableState.value is TimeSeriesAnalysisUiState.Running) return
        val current = snapshot ?: return
        val normalized = request.immutableCopy()
        if (normalized != current.request) return
        val preflight = mutableState.value.preflightOrNull() ?: return
        if (!preflight.canAnalyze) return
        requestToken += 1
        activeJob?.cancel()
        val token = requestToken
        mutableState.value = TimeSeriesAnalysisUiState.Running(
            token,
            normalized,
            preflight,
            TimeSeriesExecutionStage.PREPARING_DATA
        )
        activeJob = scope.launch {
            val outcome = service.execute(normalized, current.metricSeries, preflight) { stage ->
                if (token == requestToken) {
                    mutableState.value = TimeSeriesAnalysisUiState.Running(token, normalized, preflight, stage)
                }
            }
            if (token != requestToken) return@launch
            mutableState.value = when (outcome) {
                is TimeSeriesExecutionOutcome.Success -> TimeSeriesAnalysisUiState.Success(
                    normalized,
                    outcome.result,
                    outcome.preflight,
                    outcome.performance
                )
                is TimeSeriesExecutionOutcome.Unavailable -> TimeSeriesAnalysisUiState.Unavailable(
                    normalized,
                    outcome.reason,
                    outcome.message,
                    outcome.result,
                    outcome.preflight
                )
                is TimeSeriesExecutionOutcome.Failed -> TimeSeriesAnalysisUiState.Failed(
                    normalized,
                    outcome.message,
                    outcome.diagnosticId,
                    outcome.preflight
                )
            }
        }
    }

    fun retry() {
        val failed = mutableState.value as? TimeSeriesAnalysisUiState.Failed ?: return
        analyze(failed.request)
    }

    fun cancel() {
        requestToken += 1
        activeJob?.cancel()
        activeJob = null
        mutableState.value = TimeSeriesAnalysisUiState.Idle
    }

    private data class InputSnapshot(
        val request: TimeSeriesAnalysisRequest,
        val metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    )
}

private fun TimeSeriesAnalysisRequest.immutableCopy(): TimeSeriesAnalysisRequest = copy(
    yMetrics = yMetrics.distinct().toList(),
    controls = controls.distinct().toList()
)

private fun Map<TrendMetricId, List<TrendDataPoint>>.immutableCopy(): Map<TrendMetricId, List<TrendDataPoint>> =
    entries.associate { (metric, points) -> metric to points.toList() }

private fun TimeSeriesAnalysisUiState.preflightOrNull(): TimeSeriesPreflight? = when (this) {
    is TimeSeriesAnalysisUiState.PreflightReady -> preflight
    is TimeSeriesAnalysisUiState.Success -> preflight
    is TimeSeriesAnalysisUiState.Unavailable -> preflight
    is TimeSeriesAnalysisUiState.Failed -> preflight
    is TimeSeriesAnalysisUiState.Running -> preflight
    TimeSeriesAnalysisUiState.Idle -> null
}
