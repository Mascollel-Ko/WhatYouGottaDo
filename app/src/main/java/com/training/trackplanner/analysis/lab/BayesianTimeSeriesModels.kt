package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate

internal enum class BayesianTimeSeriesModel {
    BAYESIAN_LOCAL_PROJECTION,
    BAYESIAN_VAR,
    BAYESIAN_VECM,
    UNAVAILABLE
}

internal enum class IntegrationOrder {
    I0,
    I1,
    I2_OR_HIGHER,
    INCONCLUSIVE
}

internal data class TimeSeriesAnalysisRequest(
    val xMetric: TrendMetricId,
    val yMetrics: List<TrendMetricId>,
    val controls: List<TrendMetricId>,
    val requestedHorizon: Int = 2
)

internal data class TimeSeriesAlignment(
    val weeks: List<LocalDate>,
    val valuesByMetric: Map<TrendMetricId, List<Double>>,
    val excludedMetrics: Map<TrendMetricId, String>,
    val missingRates: Map<TrendMetricId, Double>,
    val grid: TimeSeriesCalendarGrid? = null,
    val rowExclusions: List<TimeSeriesRowExclusion> = emptyList()
)

internal class TimeSeriesCalendarGrid private constructor(
    val weeks: List<LocalDate>,
    val cellsByMetric: Map<TrendMetricId, List<TimeSeriesCell>>
) {
    fun cell(metric: TrendMetricId, index: Int): TimeSeriesCell? = cellsByMetric[metric]?.getOrNull(index)

    companion object {
        fun createValidated(
            weeks: List<LocalDate>,
            cellsByMetric: Map<TrendMetricId, List<TimeSeriesCell>>
        ): TimeSeriesCalendarGrid {
            require(weeks.isNotEmpty()) { "calendar grid cannot be empty" }
            require(weeks == weeks.sorted()) { "calendar weeks must be sorted" }
            require(weeks.distinct().size == weeks.size) { "calendar weeks must be unique" }
            weeks.zipWithNext().forEach { (left, right) ->
                require(left.plusWeeks(1) == right) { "calendar weeks must be exactly seven days apart" }
            }
            require(weeks.map { it.dayOfWeek }.distinct().size == 1) { "calendar week start day must be consistent" }
            cellsByMetric.forEach { (metric, cells) ->
                require(cells.size == weeks.size) { "cell count mismatch for $metric" }
                cells.forEachIndexed { index, cell ->
                    require(cell.weekStart == weeks[index]) { "cell week mismatch for $metric at $index" }
                    require(cell.metric == metric) { "cell metric mismatch for $metric at $index" }
                }
            }
            return TimeSeriesCalendarGrid(weeks, cellsByMetric)
        }
    }
}

internal data class TimeSeriesCell(
    val metric: TrendMetricId,
    val weekStart: LocalDate,
    val state: TimeSeriesCellState,
    val value: Double?,
    val missingReason: String? = null,
    val source: String? = null,
    val version: String? = null
)

internal data class MetricLifecycleMetadata(
    val availableFromWeek: LocalDate? = null,
    val availableUntilWeek: LocalDate? = null,
    val structuralZeroAllowed: Boolean = false,
    val notApplicableWeeks: Set<LocalDate> = emptySet(),
    val versionDiscontinuityWeeks: Set<LocalDate> = emptySet(),
    val versionDiscontinuityRanges: List<TimeSeriesWeekRange> = emptyList()
)

internal data class TimeSeriesWeekRange(
    val startWeek: LocalDate,
    val endWeek: LocalDate
) {
    fun contains(week: LocalDate): Boolean = !week.isBefore(startWeek) && !week.isAfter(endWeek)
}

internal data class TimeSeriesObservation(
    val metric: TrendMetricId,
    val weekStart: LocalDate,
    val value: Double?,
    val state: TimeSeriesCellState? = null,
    val missingReason: String? = null,
    val source: String? = null,
    val version: String? = null
)

internal enum class TimeSeriesCellState {
    OBSERVED_VALUE,
    STRUCTURAL_ZERO,
    MISSING,
    NOT_APPLICABLE,
    PRE_METRIC_CREATION,
    VERSION_DISCONTINUITY
}

internal data class TimeSeriesRowExclusion(
    val targetWeek: LocalDate,
    val sourceWeek: LocalDate?,
    val lagWeeks: List<LocalDate>,
    val horizon: Int,
    val reason: TimeSeriesRowExclusionReason,
    val cellReferences: List<TimeSeriesCellReference>,
    val diagnostics: List<String> = emptyList()
)

internal data class TimeSeriesCellReference(
    val role: TimeSeriesCellRole,
    val lagOrder: Int?,
    val metric: TrendMetricId,
    val week: LocalDate?,
    val state: TimeSeriesCellState?,
    val valuePresent: Boolean
)

internal enum class TimeSeriesCellRole {
    SOURCE,
    TARGET,
    LAG
}

internal data class TimeSeriesModelRow(
    val targetWeek: LocalDate,
    val sourceWeek: LocalDate,
    val lagWeeks: List<LocalDate>,
    val horizon: Int,
    val target: Double,
    val source: Double,
    val lags: List<Double>
)

internal enum class TimeSeriesRowExclusionReason {
    MISSING_TARGET,
    MISSING_SOURCE,
    MISSING_LAG,
    MISSING_HORIZON,
    DISCONTINUOUS_LAG,
    DISCONTINUOUS_DIFFERENCE,
    DISCONTINUOUS_HORIZON,
    PRE_METRIC_CREATION,
    VERSION_DISCONTINUITY,
    NOT_APPLICABLE,
    STRUCTURAL_ZERO_NOT_ALLOWED,
    INVALID_CELL_STATE
}

internal data class IntegrationDiagnostic(
    val metric: TrendMetricId,
    val levelOrder: IntegrationOrder,
    val adfLevelStatistic: Double,
    val kpssLevelStatistic: Double,
    val adfDifferenceStatistic: Double?,
    val kpssDifferenceStatistic: Double?,
    val message: String
)

internal data class BayesianLagPosterior(
    val probabilities: Map<Int, Double>,
    val selectedLag: Int?,
    val modelAveraged: Boolean
)

internal data class CointegrationDiagnostic(
    val rank: Int?,
    val posteriorProbabilityRankPositive: Double,
    val johansenTraceStatistic: Double?,
    val isSupported: Boolean,
    val message: String,
    val cointegrationVector: List<Double>? = null,
    val diagnostics: List<String> = emptyList()
)

internal data class AutomaticEndogenousSelection(
    val metrics: List<TrendMetricId>,
    val diagnostics: List<String>
)

internal data class CholeskySensitivityDiagnostic(
    val isOrderSensitive: Boolean,
    val message: String
)

internal data class BayesianIrfPoint(
    val horizonWeeks: Int,
    val estimate: Double,
    val low80: Double,
    val high80: Double,
    val observations: Int
)

internal data class BayesianResponseIrf(
    val yMetric: TrendMetricId,
    val points: List<BayesianIrfPoint>
)

internal data class BayesianTimeSeriesResult(
    val request: TimeSeriesAnalysisRequest,
    val model: BayesianTimeSeriesModel,
    val responses: List<BayesianResponseIrf>,
    val usedHorizon: Int,
    val alignment: TimeSeriesAlignment?,
    val integrationDiagnostics: List<IntegrationDiagnostic>,
    val cointegration: CointegrationDiagnostic?,
    val lagPosterior: BayesianLagPosterior?,
    val automaticEndogenous: List<TrendMetricId>,
    val automaticSelectionDiagnostics: List<String>,
    val choleskyOrder: List<TrendMetricId>,
    val choleskySensitivity: CholeskySensitivityDiagnostic?,
    val transformations: Map<TrendMetricId, String>,
    val confidence: AnalysisConfidence,
    val warnings: List<String>,
    val summary: String
)
