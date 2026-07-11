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
    val missingRates: Map<TrendMetricId, Double>
)

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
    val cointegrationVector: List<Double>? = null
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
