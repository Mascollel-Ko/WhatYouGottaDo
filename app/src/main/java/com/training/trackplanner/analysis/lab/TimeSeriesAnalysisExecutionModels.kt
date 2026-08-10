package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate

internal enum class TimeSeriesExecutionStage {
    PREPARING_DATA,
    CHECKING_SERIES,
    SELECTING_MODEL_INPUTS,
    FITTING_MODEL,
    IDENTIFYING_SHOCK,
    BUILDING_RESPONSE,
    FINALIZING
}

internal enum class TimeSeriesPreflightStatus {
    READY,
    BLOCKED
}

internal enum class TimeSeriesPreflightBlockerCode {
    INVALID_HORIZON,
    RESPONSE_REQUIRED,
    REQUIRED_SERIES_UNAVAILABLE,
    NO_ALIGNED_DATA,
    TRANSFORMATION_UNAVAILABLE,
    INSUFFICIENT_USABLE_HISTORY,
    INSUFFICIENT_ROWS_AFTER_LAG_HORIZON,
    INSUFFICIENT_VARIATION
}

internal data class TimeSeriesPreflightBlocker(
    val code: TimeSeriesPreflightBlockerCode,
    val metric: TrendMetricId? = null,
    val observed: Int? = null,
    val required: Int? = null
)

internal enum class TimeSeriesPreflightWarningCode {
    INTERNAL_GAPS_REDUCE_ROWS,
    REQUESTED_HORIZON_WILL_BE_REDUCED
}

internal data class TimeSeriesPreflightWarning(
    val code: TimeSeriesPreflightWarningCode,
    val observed: Int? = null,
    val requestedHorizon: Int? = null,
    val feasibleHorizon: Int? = null
)

internal data class TimeSeriesPreflight(
    val status: TimeSeriesPreflightStatus,
    val availableFrom: LocalDate?,
    val availableUntil: LocalDate?,
    val alignedWeeks: Int,
    val transformedUsableWeeks: Int,
    val requestedEstimatorRows: Int,
    val requiredMinimumRows: Int,
    val maximumFeasibleHorizon: Int?,
    val blockers: List<TimeSeriesPreflightBlocker>,
    val warnings: List<TimeSeriesPreflightWarning>
) {
    val canAnalyze: Boolean
        get() = status == TimeSeriesPreflightStatus.READY
}

internal data class TimeSeriesPerformanceProfile(
    val stageDurationsMillis: Map<TimeSeriesExecutionStage, Long>,
    val candidateCount: Int,
    val responseCount: Int,
    val lagCandidateCount: Int,
    val horizonCount: Int,
    val estimatedModelFitUpperBound: Int
)

internal sealed interface TimeSeriesExecutionOutcome {
    val preflight: TimeSeriesPreflight

    data class Success(
        val result: BayesianTimeSeriesResult,
        override val preflight: TimeSeriesPreflight,
        val performance: TimeSeriesPerformanceProfile
    ) : TimeSeriesExecutionOutcome

    data class Unavailable(
        val reason: TimeSeriesUnavailableReason,
        val message: String,
        val result: BayesianTimeSeriesResult?,
        override val preflight: TimeSeriesPreflight,
        val performance: TimeSeriesPerformanceProfile? = null
    ) : TimeSeriesExecutionOutcome

    data class Failed(
        val reason: TimeSeriesUnavailableReason,
        val message: String,
        val diagnosticId: String,
        override val preflight: TimeSeriesPreflight
    ) : TimeSeriesExecutionOutcome
}

internal sealed interface TimeSeriesAnalysisUiState {
    data object Idle : TimeSeriesAnalysisUiState

    data class PreflightReady(
        val request: TimeSeriesAnalysisRequest,
        val preflight: TimeSeriesPreflight
    ) : TimeSeriesAnalysisUiState

    data class Running(
        val requestToken: Long,
        val request: TimeSeriesAnalysisRequest,
        val preflight: TimeSeriesPreflight,
        val stage: TimeSeriesExecutionStage
    ) : TimeSeriesAnalysisUiState

    data class Success(
        val request: TimeSeriesAnalysisRequest,
        val result: BayesianTimeSeriesResult,
        val preflight: TimeSeriesPreflight,
        val performance: TimeSeriesPerformanceProfile
    ) : TimeSeriesAnalysisUiState

    data class Unavailable(
        val request: TimeSeriesAnalysisRequest,
        val reason: TimeSeriesUnavailableReason,
        val message: String,
        val result: BayesianTimeSeriesResult?,
        val preflight: TimeSeriesPreflight
    ) : TimeSeriesAnalysisUiState

    data class Failed(
        val request: TimeSeriesAnalysisRequest,
        val message: String,
        val diagnosticId: String,
        val preflight: TimeSeriesPreflight
    ) : TimeSeriesAnalysisUiState
}
