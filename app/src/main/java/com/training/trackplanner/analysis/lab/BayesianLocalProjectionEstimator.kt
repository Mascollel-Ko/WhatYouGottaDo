package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.ln
import kotlin.math.sqrt

internal data class BayesianLocalProjectionEstimate(
    val point: BayesianIrfPoint,
    val logEvidence: Double
)

internal data class RollingPredictiveScore(
    val logPredictiveDensity: Double,
    val rmse: Double,
    val mae: Double,
    val coverage80: Double,
    val origins: Int
)

internal class BayesianLocalProjectionEstimator {
    fun estimate(
        alignment: TimeSeriesAlignment,
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        endogenous: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        horizon: Int,
        structuralShock: Map<Int, Double> = emptyMap()
    ): BayesianLocalProjectionEstimate? {
        val fitted = fit(alignment, xMetric, yMetric, endogenous, controls, lag, horizon, structuralShock) ?: return null
        val estimate = fitted.posterior.mean[1]
        val standardError = sqrt(fitted.posterior.covariance[1][1].coerceAtLeast(0.0))
        return BayesianLocalProjectionEstimate(
            point = BayesianIrfPoint(
                horizonWeeks = horizon,
                estimate = estimate,
                low80 = estimate - Z80 * standardError,
                high80 = estimate + Z80 * standardError,
                observations = fitted.rows.size
            ),
            logEvidence = fitted.posterior.logEvidence
        )
    }

    fun rollingPredictiveScore(
        alignment: TimeSeriesAlignment,
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        endogenous: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int = 1,
        horizon: Int = 1
    ): RollingPredictiveScore? {
        val minimumRows = minimumRows(endogenous.size, controls.size, lag)
        val minimumTraining = minimumRows + lag + horizon
        if (alignment.weeks.size < minimumTraining + MIN_ROLLING_ORIGINS) return null
        val firstOrigin = maxOf(minimumTraining - 1, alignment.weeks.size - horizon - MAX_ROLLING_ORIGINS)
        val origins = (firstOrigin until alignment.weeks.size - horizon).toList()
        val errors = mutableListOf<Double>()
        val logDensities = mutableListOf<Double>()
        var covered = 0
        origins.forEach { origin ->
            val training = slice(alignment, origin + 1)
            val fitted = fit(training, xMetric, yMetric, endogenous, controls, lag, horizon, emptyMap()) ?: return@forEach
            val feature = featureVector(alignment, xMetric, endogenous, controls, lag, origin, emptyMap()) ?: return@forEach
            val standardizedFeature = standardizedFeature(feature, fitted)
            val predicted = fitted.posterior.mean.indices.sumOf { index -> fitted.posterior.mean[index] * standardizedFeature[index] }
            val predictiveVariance = fitted.posterior.residualVariance + quadraticForm(standardizedFeature, fitted.posterior.covariance)
            if (!predictiveVariance.isFinite() || predictiveVariance <= EPSILON) return@forEach
            val actual = alignment.valuesByMetric[yMetric]?.getOrNull(origin + horizon) ?: return@forEach
            val actualStandardized = (actual - fitted.yMean) / fitted.yStandardDeviation
            val error = actualStandardized - predicted
            errors += error
            val standardDeviation = sqrt(predictiveVariance)
            if (kotlin.math.abs(error) <= Z80 * standardDeviation) covered += 1
            logDensities += -0.5 * (ln(2.0 * Math.PI * predictiveVariance) + error * error / predictiveVariance) - ln(fitted.yStandardDeviation)
        }
        if (errors.size < MIN_ROLLING_ORIGINS) return null
        return RollingPredictiveScore(
            logPredictiveDensity = logDensities.sum(),
            rmse = sqrt(errors.sumOf { it * it } / errors.size),
            mae = errors.sumOf { kotlin.math.abs(it) } / errors.size,
            coverage80 = covered.toDouble() / errors.size,
            origins = errors.size
        )
    }

    private fun fit(
        alignment: TimeSeriesAlignment,
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        endogenous: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        horizon: Int,
        structuralShock: Map<Int, Double>
    ): FittedProjection? {
        val yValues = alignment.valuesByMetric[yMetric] ?: return null
        val rows = (lag until yValues.size - horizon).mapNotNull { time ->
            val response = yValues.getOrNull(time + horizon) ?: return@mapNotNull null
            val feature = featureVector(alignment, xMetric, endogenous, controls, lag, time, structuralShock) ?: return@mapNotNull null
            RegressionRow(response, feature)
        }
        if (rows.size < minimumRows(endogenous.size, controls.size, lag)) return null
        val standardized = standardize(rows) ?: return null
        val prior = DoubleArray(standardized.x.first().size) { index ->
            when (index) {
                0 -> 1e-6
                1 -> 1.0 / (0.75 * 0.75)
                else -> 1.0 / (0.5 * 0.5)
            }
        }
        val posterior = BayesianLinearRegression.fit(standardized.x, standardized.y, prior) ?: return null
        return FittedProjection(rows, posterior, standardized.yMean, standardized.yStandardDeviation, standardized.featureMeans, standardized.featureStandardDeviations)
    }

    private fun featureVector(
        alignment: TimeSeriesAlignment,
        xMetric: TrendMetricId,
        endogenous: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        time: Int,
        structuralShock: Map<Int, Double>
    ): DoubleArray? {
        val xValues = alignment.valuesByMetric[xMetric] ?: return null
        val shock = structuralShock[time] ?: (xValues.getOrNull(time) ?: return null) - (xValues.getOrNull(time - 1) ?: return null)
        val lags = endogenous.flatMap { metric ->
            val values = alignment.valuesByMetric[metric] ?: return null
            (1..lag).map { offset -> values.getOrNull(time - offset) ?: return null }
        }
        val contemporaneousControls = controls.map { metric -> alignment.valuesByMetric[metric]?.getOrNull(time) ?: return null }
        return doubleArrayOf(shock) + lags.toDoubleArray() + contemporaneousControls.toDoubleArray()
    }

    private fun standardize(rows: List<RegressionRow>): Standardized? {
        val yMean = rows.map { it.y }.average()
        val yStandardDeviation = sqrt(variance(rows.map { it.y })).takeIf { it > EPSILON } ?: return null
        val featureMeans = rows.first().x.indices.map { index -> rows.map { it.x[index] }.average() }.toDoubleArray()
        val featureStandardDeviations = rows.first().x.indices.map { index -> sqrt(variance(rows.map { it.x[index] })) }.toDoubleArray()
        if (featureStandardDeviations.firstOrNull()?.let { it <= EPSILON } != false) return null
        val x = rows.map { row ->
            doubleArrayOf(1.0) + row.x.mapIndexed { index, value ->
                val standardDeviation = featureStandardDeviations[index]
                if (standardDeviation <= EPSILON) 0.0 else (value - featureMeans[index]) / standardDeviation
            }.toDoubleArray()
        }
        return Standardized(x, rows.map { row -> (row.y - yMean) / yStandardDeviation }.toDoubleArray(), yMean, yStandardDeviation, featureMeans, featureStandardDeviations)
    }

    private fun standardizedFeature(feature: DoubleArray, fitted: FittedProjection): DoubleArray =
        doubleArrayOf(1.0) + feature.mapIndexed { index, value ->
            val standardDeviation = fitted.featureStandardDeviations[index]
            if (standardDeviation <= EPSILON) 0.0 else (value - fitted.featureMeans[index]) / standardDeviation
        }.toDoubleArray()

    private fun slice(alignment: TimeSeriesAlignment, endExclusive: Int): TimeSeriesAlignment = alignment.copy(
        weeks = alignment.weeks.take(endExclusive),
        valuesByMetric = alignment.valuesByMetric.mapValues { (_, values) -> values.take(endExclusive) }
    )

    private fun minimumRows(endogenousCount: Int, controlCount: Int, lag: Int): Int =
        maxOf(MIN_OBSERVATIONS, 4 * (1 + endogenousCount * lag + controlCount + DETERMINISTIC_TERMS))

    private fun quadraticForm(vector: DoubleArray, matrix: Array<DoubleArray>): Double =
        vector.indices.sumOf { row -> vector[row] * matrix[row].indices.sumOf { column -> matrix[row][column] * vector[column] } }

    private data class RegressionRow(val y: Double, val x: DoubleArray)

    private data class Standardized(
        val x: List<DoubleArray>,
        val y: DoubleArray,
        val yMean: Double,
        val yStandardDeviation: Double,
        val featureMeans: DoubleArray,
        val featureStandardDeviations: DoubleArray
    )

    private data class FittedProjection(
        val rows: List<RegressionRow>,
        val posterior: BayesianLinearRegression.Posterior,
        val yMean: Double,
        val yStandardDeviation: Double,
        val featureMeans: DoubleArray,
        val featureStandardDeviations: DoubleArray
    )

    private companion object {
        const val EPSILON = 1e-9
        const val MIN_OBSERVATIONS = 24
        const val DETERMINISTIC_TERMS = 1
        const val MIN_ROLLING_ORIGINS = 3
        const val MAX_ROLLING_ORIGINS = 5
        const val Z80 = 1.28155
    }
}
