package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class TimeSeriesAnalysisPreflightPolicy(
    private val alignmentService: TimeSeriesAlignmentService = TimeSeriesAlignmentService()
) {
    fun evaluate(
        request: TimeSeriesAnalysisRequest,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): TimeSeriesPreflight {
        val requiredMetrics = (listOf(request.xMetric) + request.yMetrics + request.controls).distinct()
        val minimumRows = minimumEstimatorRows(request)
        val initialBlockers = buildList {
            if (request.requestedHorizon !in MIN_HORIZON..MAX_HORIZON) {
                add(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.INVALID_HORIZON))
            }
            if (request.yMetrics.distinct().none { it != request.xMetric }) {
                add(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.RESPONSE_REQUIRED))
            }
            requiredMetrics.filter { metricSeries[it].isNullOrEmpty() }.forEach { metric ->
                add(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.REQUIRED_SERIES_UNAVAILABLE, metric))
            }
        }
        if (initialBlockers.isNotEmpty()) return blocked(minimumRows, initialBlockers)

        val rawAlignment = alignmentService.align(requiredMetrics, metricSeries)
            ?: return blocked(minimumRows, listOf(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.NO_ALIGNED_DATA)))
        val levelCatalog = alignmentService.align(AnalysisMetricRegistry.descriptors.map { it.id }, metricSeries)
            ?.let { alignmentService.restrictToWeeks(it, rawAlignment.weeks) }
            ?: return blocked(
                minimumRows,
                listOf(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.NO_ALIGNED_DATA)),
                rawAlignment
            )
        val transformationPlan = alignmentService.transformationPlan(levelCatalog, requiredMetrics.toSet())
        val unsupported = requiredMetrics.firstOrNull { metric ->
            transformationPlan.diagnostics[metric]?.levelOrder == IntegrationOrder.I2_OR_HIGHER
        }
        if (unsupported != null) {
            return blocked(
                minimumRows,
                listOf(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.TRANSFORMATION_UNAVAILABLE, unsupported)),
                rawAlignment
            )
        }
        val preparedCatalog = alignmentService.preparedCandidateCatalog(levelCatalog, transformationPlan)?.preparedSeriesByMetric
            ?: return blocked(
                minimumRows,
                listOf(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.TRANSFORMATION_UNAVAILABLE)),
                rawAlignment
            )
        val preparedAlignment = alignmentService.alignmentFromPrepared(requiredMetrics, preparedCatalog)
            ?: return blocked(
                minimumRows,
                listOf(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.TRANSFORMATION_UNAVAILABLE)),
                rawAlignment
            )

        val usableWeeks = preparedAlignment.weeks.indices.count { index ->
            requiredMetrics.all { metric -> preparedAlignment.valueAt(metric, index) != null }
        }
        val rowsByHorizon = (MIN_HORIZON..request.requestedHorizon.coerceIn(MIN_HORIZON, MAX_HORIZON))
            .associateWith { horizon -> estimatorRows(preparedAlignment, request, horizon) }
        val requestedRows = rowsByHorizon[request.requestedHorizon] ?: 0
        val feasibleHorizon = rowsByHorizon.filterValues { rows -> rows >= minimumRows }.keys.maxOrNull()
        val blockers = buildList {
            if (usableWeeks < MIN_TRANSFORMED_WEEKS) {
                add(
                    TimeSeriesPreflightBlocker(
                        TimeSeriesPreflightBlockerCode.INSUFFICIENT_USABLE_HISTORY,
                        observed = usableWeeks,
                        required = MIN_TRANSFORMED_WEEKS
                    )
                )
            }
            if (feasibleHorizon == null) {
                add(
                    TimeSeriesPreflightBlocker(
                        TimeSeriesPreflightBlockerCode.INSUFFICIENT_ROWS_AFTER_LAG_HORIZON,
                        observed = rowsByHorizon[MIN_HORIZON] ?: 0,
                        required = minimumRows
                    )
                )
            }
            if (!hasRequiredVariation(preparedAlignment, request)) {
                add(TimeSeriesPreflightBlocker(TimeSeriesPreflightBlockerCode.INSUFFICIENT_VARIATION))
            }
        }
        val warnings = buildList {
            if (usableWeeks < preparedAlignment.weeks.size) {
                add(
                    TimeSeriesPreflightWarning(
                        TimeSeriesPreflightWarningCode.INTERNAL_GAPS_REDUCE_ROWS,
                        observed = preparedAlignment.weeks.size - usableWeeks
                    )
                )
            }
            if (feasibleHorizon != null && feasibleHorizon < request.requestedHorizon) {
                add(
                    TimeSeriesPreflightWarning(
                        TimeSeriesPreflightWarningCode.REQUESTED_HORIZON_WILL_BE_REDUCED,
                        requestedHorizon = request.requestedHorizon,
                        feasibleHorizon = feasibleHorizon
                    )
                )
            }
        }
        return TimeSeriesPreflight(
            status = if (blockers.isEmpty()) TimeSeriesPreflightStatus.READY else TimeSeriesPreflightStatus.BLOCKED,
            availableFrom = preparedAlignment.weeks.firstOrNull(),
            availableUntil = preparedAlignment.weeks.lastOrNull(),
            alignedWeeks = preparedAlignment.weeks.size,
            transformedUsableWeeks = usableWeeks,
            requestedEstimatorRows = requestedRows,
            requiredMinimumRows = minimumRows,
            maximumFeasibleHorizon = feasibleHorizon,
            blockers = blockers,
            warnings = warnings
        )
    }

    private fun estimatorRows(
        alignment: TimeSeriesAlignment,
        request: TimeSeriesAnalysisRequest,
        horizon: Int
    ): Int {
        val endogenous = (listOf(request.xMetric) + request.yMetrics).distinct()
        return (BASE_LAG until alignment.weeks.size - horizon).count { index ->
            alignment.exactDifference(request.xMetric, index) != null &&
                endogenous.all { metric ->
                    alignment.valueAt(metric, index) != null && alignment.exactLag(metric, index, BASE_LAG) != null
                } &&
                request.controls.distinct().all { metric -> alignment.valueAt(metric, index) != null } &&
                request.yMetrics.distinct().all { metric -> alignment.exactHorizon(metric, index, horizon) != null }
        }
    }

    private fun hasRequiredVariation(
        alignment: TimeSeriesAlignment,
        request: TimeSeriesAnalysisRequest
    ): Boolean {
        val shock = (1 until alignment.weeks.size).mapNotNull { alignment.exactDifference(request.xMetric, it) }
        if (shock.size < 2 || variance(shock) <= EPSILON) return false
        return request.yMetrics.distinct().all { metric ->
            val values = alignment.valuesByMetric[metric].orEmpty().filter(Double::isFinite)
            values.size >= 2 && variance(values) > EPSILON
        }
    }

    private fun minimumEstimatorRows(request: TimeSeriesAnalysisRequest): Int {
        val endogenousCount = (listOf(request.xMetric) + request.yMetrics).distinct().size
        val controlCount = request.controls.distinct().count { it != request.xMetric && it !in request.yMetrics }
        return maxOf(MIN_TRANSFORMED_WEEKS, 4 * (2 + endogenousCount * BASE_LAG + controlCount))
    }

    private fun blocked(
        minimumRows: Int,
        blockers: List<TimeSeriesPreflightBlocker>,
        alignment: TimeSeriesAlignment? = null
    ) = TimeSeriesPreflight(
        status = TimeSeriesPreflightStatus.BLOCKED,
        availableFrom = alignment?.weeks?.firstOrNull(),
        availableUntil = alignment?.weeks?.lastOrNull(),
        alignedWeeks = alignment?.weeks?.size ?: 0,
        transformedUsableWeeks = 0,
        requestedEstimatorRows = 0,
        requiredMinimumRows = minimumRows,
        maximumFeasibleHorizon = null,
        blockers = blockers,
        warnings = emptyList()
    )

    private companion object {
        const val MIN_HORIZON = 1
        const val MAX_HORIZON = 8
        const val MIN_TRANSFORMED_WEEKS = 24
        const val BASE_LAG = 1
        const val EPSILON = 1e-9
    }
}

internal class TimeSeriesAnalysisService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val preflightPolicy: TimeSeriesAnalysisPreflightPolicy = TimeSeriesAnalysisPreflightPolicy(),
    private val analyzer: LegacyTimeSeriesAnalyzer = LegacyTimeSeriesAnalyzer(),
    private val preflightBlock: (TimeSeriesAnalysisRequest, Map<TrendMetricId, List<TrendDataPoint>>) -> TimeSeriesPreflight =
        preflightPolicy::evaluate,
    private val analysisBlock: (
        TimeSeriesAnalysisRequest,
        Map<TrendMetricId, List<TrendDataPoint>>,
        (TimeSeriesExecutionStage) -> Unit
    ) -> BayesianTimeSeriesResult = analyzer::analyze,
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun preflight(
        request: TimeSeriesAnalysisRequest,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): TimeSeriesPreflight = withContext(dispatcher) {
        preflightBlock(request, metricSeries)
    }

    suspend fun execute(
        request: TimeSeriesAnalysisRequest,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        preflight: TimeSeriesPreflight,
        onStage: (TimeSeriesExecutionStage) -> Unit = {}
    ): TimeSeriesExecutionOutcome = withContext(dispatcher) {
        if (!preflight.canAnalyze) {
            return@withContext TimeSeriesExecutionOutcome.Unavailable(
                reason = preflight.blockers.firstOrNull()?.toUnavailableReason()
                    ?: TimeSeriesUnavailableReason.INVALID_REQUEST,
                message = "The selected request did not pass preflight.",
                result = null,
                preflight = preflight
            )
        }
        val context = currentCoroutineContext()
        val stageDurations = linkedMapOf<TimeSeriesExecutionStage, Long>()
        var currentStage = TimeSeriesExecutionStage.PREPARING_DATA
        var stageStarted = nanoTime()
        fun transition(stage: TimeSeriesExecutionStage) {
            context.ensureActive()
            val now = nanoTime()
            stageDurations[currentStage] = stageDurations.getOrDefault(currentStage, 0L) +
                ((now - stageStarted).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
            currentStage = stage
            stageStarted = now
            onStage(stage)
        }
        onStage(currentStage)
        try {
            val result = analysisBlock(request, metricSeries, ::transition)
            transition(TimeSeriesExecutionStage.FINALIZING)
            val now = nanoTime()
            stageDurations[currentStage] = stageDurations.getOrDefault(currentStage, 0L) +
                ((now - stageStarted).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
            val performance = performanceProfile(request, stageDurations)
            if (result.model == BayesianTimeSeriesModel.UNAVAILABLE) {
                TimeSeriesExecutionOutcome.Unavailable(
                    reason = result.unavailableReason ?: TimeSeriesUnavailableReason.ALL_ESTIMATORS_FAILED,
                    message = result.summary,
                    result = result,
                    preflight = preflight,
                    performance = performance
                )
            } else {
                TimeSeriesExecutionOutcome.Success(result, preflight, performance)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            val diagnosticId = diagnosticId(failure)
            LOGGER.log(Level.SEVERE, "$diagnosticId time-series execution failed", failure)
            TimeSeriesExecutionOutcome.Failed(
                reason = TimeSeriesUnavailableReason.UNEXPECTED_INTERNAL_ERROR,
                message = "The exploratory analysis could not be completed because of an internal error.",
                diagnosticId = diagnosticId,
                preflight = preflight
            )
        }
    }

    private fun performanceProfile(
        request: TimeSeriesAnalysisRequest,
        stageDurations: Map<TimeSeriesExecutionStage, Long>
    ): TimeSeriesPerformanceProfile {
        val candidates = AnalysisMetricRegistry.descriptors.count { it.supportsMultivariate }
        val responses = request.yMetrics.distinct().size.coerceAtLeast(1)
        val horizons = request.requestedHorizon.coerceIn(1, 8) + 1
        val upperBound = candidates * responses * ROLLING_ORIGIN_UPPER_BOUND * LAG_CANDIDATE_COUNT * horizons
        return TimeSeriesPerformanceProfile(
            stageDurationsMillis = stageDurations.toMap(),
            candidateCount = candidates,
            responseCount = responses,
            lagCandidateCount = LAG_CANDIDATE_COUNT,
            horizonCount = horizons,
            estimatedModelFitUpperBound = upperBound
        )
    }

    private fun diagnosticId(failure: Throwable): String {
        val value = "${failure::class.qualifiedName}:${failure.message}".hashCode().toUInt().toString(16).uppercase()
        return "TS-$value"
    }

    private companion object {
        val LOGGER: Logger = Logger.getLogger(TimeSeriesAnalysisService::class.java.name)
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val ROLLING_ORIGIN_UPPER_BOUND = 5
        const val LAG_CANDIDATE_COUNT = 4
    }
}

private fun TimeSeriesPreflightBlocker.toUnavailableReason(): TimeSeriesUnavailableReason = when (code) {
    TimeSeriesPreflightBlockerCode.INVALID_HORIZON,
    TimeSeriesPreflightBlockerCode.RESPONSE_REQUIRED -> TimeSeriesUnavailableReason.INVALID_REQUEST
    TimeSeriesPreflightBlockerCode.REQUIRED_SERIES_UNAVAILABLE -> TimeSeriesUnavailableReason.REQUIRED_SERIES_UNAVAILABLE
    TimeSeriesPreflightBlockerCode.NO_ALIGNED_DATA -> TimeSeriesUnavailableReason.NO_ALIGNED_DATA
    TimeSeriesPreflightBlockerCode.TRANSFORMATION_UNAVAILABLE -> TimeSeriesUnavailableReason.TRANSFORMATION_UNAVAILABLE
    TimeSeriesPreflightBlockerCode.INSUFFICIENT_USABLE_HISTORY -> TimeSeriesUnavailableReason.INSUFFICIENT_USABLE_HISTORY
    TimeSeriesPreflightBlockerCode.INSUFFICIENT_ROWS_AFTER_LAG_HORIZON -> TimeSeriesUnavailableReason.INSUFFICIENT_ROWS_AFTER_LAG_HORIZON
    TimeSeriesPreflightBlockerCode.INSUFFICIENT_VARIATION -> TimeSeriesUnavailableReason.NUMERICAL_INSTABILITY
}
