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
                    mutableState.value = StrictBayesianLabUiState.Unavailable(
                        normalized,
                        null,
                        StrictFailureDiagnostics(
                            code = StrictLabFailureCode.DATA_NOT_READY,
                            stage = StrictFailureStage.SNAPSHOT,
                            primaryReason = "주간 분석 데이터를 준비하지 못했습니다.",
                            technicalDetails = listOfNotNull(failure::class.qualifiedName, failure.message)
                        )
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
        analyze(request, retryAttempt = 0)
    }

    private fun analyze(
        request: StrictLabAnalysisRequest,
        retryAttempt: Int
    ) {
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
            StrictLabExecutionStage.PREPARING_STRICT_INPUT,
            retryAttempt
        )
        activeJob = scope.launch {
            val captured = freshSnapshot().getOrElse { failure ->
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Unavailable(
                        normalized,
                        priorPreflight,
                        StrictFailureDiagnostics(
                            code = StrictLabFailureCode.DATA_NOT_READY,
                            stage = StrictFailureStage.SNAPSHOT,
                            primaryReason = "최신 주간 분석 데이터를 준비하지 못했습니다.",
                            availableClosedWeeks = priorPreflight.closedWeeks,
                            retryAttempt = retryAttempt,
                            originalControls = normalized.controls.map { it.value },
                            effectiveControls = normalized.controls.map { it.value },
                            technicalDetails = listOfNotNull(failure::class.qualifiedName, failure.message)
                        )
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
                    mutableState.value = StrictBayesianLabUiState.Unavailable(
                        normalized,
                        preflight,
                        StrictFailureDiagnostics(
                            code = StrictLabFailureCode.PREFLIGHT_INELIGIBLE,
                            stage = StrictFailureStage.PREFLIGHT,
                            primaryReason = "갱신된 기록에서 선택한 조합을 분석할 수 없습니다.",
                            affectedFeatureOrSource = preflight.blockers.firstNotNullOfOrNull { it.feature?.value },
                            availableClosedWeeks = preflight.closedWeeks,
                            retryAttempt = retryAttempt,
                            originalControls = normalized.controls.map { it.value },
                            effectiveControls = normalized.controls.map { it.value },
                            snapshotFingerprint = captured.fingerprint,
                            technicalDetails = preflight.blockers.map { "${it.code}:${it.feature}:${it.detail}" }
                        )
                    )
                }
                return@launch
            }
            snapshot = captured
            val outcome = service.execute(
                captured,
                normalized,
                preflight,
                retryAttempt
            ) { stage ->
                if (token == requestToken) {
                    mutableState.value = StrictBayesianLabUiState.Running(
                        token,
                        normalized,
                        preflight,
                        stage,
                        retryAttempt
                    )
                }
            }
            if (token != requestToken) return@launch
            if (currentSnapshotFingerprint() != captured.fingerprint) {
                mutableState.value = StrictBayesianLabUiState.Unavailable(
                    normalized,
                    preflight,
                    StrictFailureDiagnostics(
                        code = StrictLabFailureCode.STALE_RESULT_REJECTED,
                        stage = StrictFailureStage.COORDINATION,
                        primaryReason = "분석 중 기록이 갱신되어 이전 결과를 표시하지 않았습니다.",
                        availableClosedWeeks = captured.closedWeeks.size,
                        retryAttempt = retryAttempt,
                        originalControls = normalized.controls.map { it.value },
                        effectiveControls = normalized.controls.map { it.value },
                        snapshotFingerprint = captured.fingerprint,
                        technicalDetails = listOf(
                            "captured=${captured.fingerprint}",
                            "current=${currentSnapshotFingerprint()}"
                        )
                    )
                )
                return@launch
            }
            mutableState.value = when (outcome) {
                is StrictLabExecutionOutcome.Available -> StrictBayesianLabUiState.Available(
                    normalized,
                    outcome.result,
                    preflight
                )
                is StrictLabExecutionOutcome.Unavailable -> StrictBayesianLabUiState.Unavailable(
                    normalized,
                    preflight,
                    outcome.failure,
                    outcome.adjustmentTrace
                )
            }
        }
    }

    fun retry() {
        val failed = mutableState.value as? StrictBayesianLabUiState.Unavailable ?: return
        val request = failed.request
        if (failed.preflight?.canAnalyze == true && request == selectedRequest) {
            analyze(request, failed.failure.retryAttempt + 1)
        } else {
            updateRequest(request)
        }
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
    is StrictBayesianLabUiState.Available -> preflight
    is StrictBayesianLabUiState.Unavailable -> preflight
    StrictBayesianLabUiState.Idle,
    is StrictBayesianLabUiState.DataPreparing -> null
}
