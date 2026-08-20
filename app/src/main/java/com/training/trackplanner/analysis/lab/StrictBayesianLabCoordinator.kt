package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class StrictBayesianLabCoordinator(
    private val scope: CoroutineScope,
    private val freshSnapshot: suspend () -> Result<WeeklyAnalysisFeatureSnapshot>,
    private val currentSnapshotFingerprint: () -> String?,
    private val service: StrictBayesianLabService = StrictBayesianLabService()
) {
    private val mutableState = MutableStateFlow<StrictBayesianLabUiState>(StrictBayesianLabUiState.Idle)
    val state: StateFlow<StrictBayesianLabUiState> = mutableState.asStateFlow()

    private var activeJob: Job? = null
    private var requestToken = 0L
    private var snapshot: WeeklyAnalysisFeatureSnapshot? = null
    private var selectedRequest: StrictLabAnalysisRequest? = null

    fun updateRequest(request: StrictLabAnalysisRequest) {
        val normalized = request.normalized()
        if (normalized == selectedRequest && mutableState.value !is StrictBayesianLabUiState.Idle) return
        selectedRequest = normalized
        requestToken += 1
        activeJob?.cancel()
        val token = requestToken
        mutableState.value = StrictBayesianLabUiState.DataPreparing(normalized)
        activeJob = scope.launch {
            val captured = freshSnapshot().getOrElse { failure ->
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Failed(
                        normalized,
                        null,
                        StrictLabFailureCode.DATA_NOT_READY,
                        "주간 분석 데이터를 준비하지 못했습니다.",
                        listOfNotNull(failure.message),
                        null
                    )
                }
                return@launch
            }
            val preflight = service.preflight(captured, normalized)
            if (token == requestToken) {
                snapshot = captured
                mutableState.value = StrictBayesianLabUiState.PreflightReady(normalized, preflight)
            }
        }
    }

    fun analyze(request: StrictLabAnalysisRequest) {
        if (mutableState.value is StrictBayesianLabUiState.Running) return
        val normalized = request.normalized()
        if (normalized != selectedRequest) return
        val priorPreflight = mutableState.value.preflightOrNull() ?: return
        if (!priorPreflight.canAnalyze) return
        requestToken += 1
        activeJob?.cancel()
        val token = requestToken
        mutableState.value = StrictBayesianLabUiState.Running(
            token,
            normalized,
            priorPreflight,
            StrictLabExecutionStage.PREPARING_STRICT_INPUT
        )
        activeJob = scope.launch {
            val captured = freshSnapshot().getOrElse { failure ->
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Failed(
                        normalized,
                        priorPreflight,
                        StrictLabFailureCode.DATA_NOT_READY,
                        "최신 주간 분석 데이터를 준비하지 못했습니다.",
                        listOfNotNull(failure.message),
                        null
                    )
                }
                return@launch
            }
            val preflight = if (captured.fingerprint == priorPreflight.snapshotFingerprint) {
                priorPreflight
            } else {
                service.preflight(captured, normalized)
            }
            if (!preflight.canAnalyze) {
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Failed(
                        normalized,
                        preflight,
                        StrictLabFailureCode.PREFLIGHT_INELIGIBLE,
                        "갱신된 기록에서 선택한 조합을 분석할 수 없습니다.",
                        preflight.blockers.map { "${it.code}:${it.feature}:${it.detail}" },
                        null
                    )
                }
                return@launch
            }
            snapshot = captured
            val outcome = service.execute(captured, normalized, preflight) { stage ->
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Running(token, normalized, preflight, stage)
                }
            }
            if (token != requestToken) return@launch
            if (currentSnapshotFingerprint() != captured.fingerprint) {
                mutableState.value = StrictBayesianLabUiState.Failed(
                    normalized,
                    preflight,
                    StrictLabFailureCode.STALE_RESULT_REJECTED,
                    "분석 중 기록이 갱신되어 이전 결과를 표시하지 않았습니다.",
                    listOf("captured=${captured.fingerprint}", "current=${currentSnapshotFingerprint()}"),
                    null
                )
                return@launch
            }
            mutableState.value = when (outcome) {
                is StrictLabExecutionOutcome.Success -> StrictBayesianLabUiState.Success(
                    normalized,
                    outcome.result,
                    preflight
                )
                is StrictLabExecutionOutcome.Failure -> StrictBayesianLabUiState.Failed(
                    normalized,
                    preflight,
                    outcome.code,
                    outcome.message,
                    outcome.diagnostics,
                    outcome.diagnosticId
                )
            }
        }
    }

    fun retry() {
        val failed = mutableState.value as? StrictBayesianLabUiState.Failed ?: return
        val request = failed.request
        if (failed.preflight?.canAnalyze == true && request == selectedRequest) analyze(request) else updateRequest(request)
    }

    fun cancel() {
        requestToken += 1
        activeJob?.cancel()
        activeJob = null
        mutableState.value = StrictBayesianLabUiState.Idle
    }
}

private fun StrictBayesianLabUiState.preflightOrNull(): StrictLabPreflight? = when (this) {
    is StrictBayesianLabUiState.PreflightReady -> preflight
    is StrictBayesianLabUiState.Running -> preflight
    is StrictBayesianLabUiState.Success -> preflight
    is StrictBayesianLabUiState.Failed -> preflight
    StrictBayesianLabUiState.Idle,
    is StrictBayesianLabUiState.DataPreparing -> null
}
