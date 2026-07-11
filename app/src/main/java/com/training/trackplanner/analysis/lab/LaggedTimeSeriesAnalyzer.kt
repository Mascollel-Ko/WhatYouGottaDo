package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

internal enum class LaggedCoefficientRole {
    INTERCEPT,
    MAIN_X,
    LAG_Y,
    LAG_X,
    CONTROL
}

internal fun laggedPriorPrecision(role: LaggedCoefficientRole): Double {
    val priorSd = when (role) {
        LaggedCoefficientRole.INTERCEPT -> 1000.0
        LaggedCoefficientRole.MAIN_X -> 0.75
        LaggedCoefficientRole.LAG_Y,
        LaggedCoefficientRole.LAG_X -> 0.50
        LaggedCoefficientRole.CONTROL -> 0.35
    }
    return 1.0 / (priorSd * priorSd)
}

internal data class LaggedEffectPoint(
    val horizonWeeks: Int,
    val estimate: Double,
    val low80: Double,
    val high80: Double,
    val observations: Int,
    val residualVariance: Double = 0.0
) {
    val intervalWidth80: Double
        get() = high80 - low80
}

internal data class LaggedTimeSeriesResult(
    val xMetric: TrendMetricId,
    val yMetric: TrendMetricId,
    val controls: List<TrendMetricId>,
    val points: List<LaggedEffectPoint>,
    val observations: Int,
    val candidateWeeks: Int,
    val confidence: AnalysisConfidence,
    val warnings: List<String>,
    val summary: String,
    val automaticAdjustments: List<String> = emptyList(),
    val model: BayesianTimeSeriesModel = BayesianTimeSeriesModel.UNAVAILABLE,
    val usedHorizon: Int = 0,
    val lagPosterior: BayesianLagPosterior? = null
)

internal class LaggedTimeSeriesAnalyzer {
    private val analyzer = BayesianTimeSeriesAnalyzer()

    fun analyze(
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        controls: List<TrendMetricId>,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        horizons: IntRange = 1..4
    ): LaggedTimeSeriesResult {
        val result = analyzer.analyze(
            request = TimeSeriesAnalysisRequest(xMetric, listOf(yMetric), controls, horizons.last.coerceIn(1, 8)),
            metricSeries = metricSeries
        )
        val response = result.responses.firstOrNull()
        return LaggedTimeSeriesResult(
            xMetric = xMetric,
            yMetric = yMetric,
            controls = controls,
            points = response?.points.orEmpty().filter { point -> point.horizonWeeks >= horizons.first }.map { point ->
                LaggedEffectPoint(point.horizonWeeks, point.estimate, point.low80, point.high80, point.observations)
            },
            observations = response?.points?.minOfOrNull { it.observations } ?: 0,
            candidateWeeks = result.alignment?.weeks?.size ?: 0,
            confidence = result.confidence,
            warnings = result.warnings,
            summary = result.summary,
            automaticAdjustments = result.automaticEndogenous.map { metric -> AnalysisMetricRegistry.descriptor(metric)?.displayName ?: metric.name },
            model = result.model,
            usedHorizon = result.usedHorizon,
            lagPosterior = result.lagPosterior
        )
    }
}
