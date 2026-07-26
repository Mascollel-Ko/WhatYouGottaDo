package com.training.trackplanner.analysis.strengthperformance

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class ScalarLikelihoodSupport(
    val center: Double,
    val standardDeviation: Double
)

class ScalarGridLikelihood(
    val support: List<ScalarLikelihoodSupport>,
    private val evaluator: (Double) -> Double
) {
    fun logValueAt(value: Double): Double = evaluator(value)
}

data class ScalarGridDiagnostics(
    val gridPointCount: Int,
    val expansionCount: Int,
    val lowerBound: Double,
    val upperBound: Double,
    val edgeMass: Double,
    val normalized: Boolean,
    val fingerprint: String
)

data class ScalarGridMoments(
    val mean: Double,
    val variance: Double,
    val diagnostics: ScalarGridDiagnostics
)

data class GaussianProjectionResult(
    val mean: DoubleArray,
    val covariance: Array<DoubleArray>,
    val priorProjectionMean: Double,
    val priorProjectionVariance: Double,
    val posteriorProjectionMean: Double,
    val posteriorProjectionVariance: Double,
    val diagnostics: ScalarGridDiagnostics
)

object ScalarGridPosteriorEngine {
    fun posteriorMoments(
        priorMean: Double,
        priorVariance: Double,
        likelihood: ScalarGridLikelihood
    ): ScalarGridMoments {
        require(priorMean.isFinite() && priorVariance.isFinite() && priorVariance > 0.0)
        val priorSd = sqrt(priorVariance)
        val support = likelihood.support + ScalarLikelihoodSupport(priorMean, priorSd)
        return integrate(support) { value ->
            normalLogDensity(value, priorMean, priorVariance) + likelihood.logValueAt(value)
        }
    }

    fun likelihoodMoments(likelihood: ScalarGridLikelihood): ScalarGridMoments =
        integrate(likelihood.support, likelihood::logValueAt)

    fun project(
        priorMean: DoubleArray,
        priorCovariance: Array<DoubleArray>,
        projection: DoubleArray,
        likelihood: ScalarGridLikelihood
    ): GaussianProjectionResult {
        require(priorMean.size == projection.size)
        require(priorCovariance.size == priorMean.size && priorCovariance.all { it.size == priorMean.size })
        val projectedCovariance = multiply(priorCovariance, projection)
        val priorProjectionMean = dot(projection, priorMean)
        val priorProjectionVariance = dot(projection, projectedCovariance).coerceAtLeast(MIN_VARIANCE)
        val moments = posteriorMoments(priorProjectionMean, priorProjectionVariance, likelihood)
        val gain = DoubleArray(priorMean.size) { index ->
            projectedCovariance[index] / priorProjectionVariance
        }
        val mean = DoubleArray(priorMean.size) { index ->
            priorMean[index] + gain[index] * (moments.mean - priorProjectionMean)
        }
        val varianceChange = moments.variance - priorProjectionVariance
        val covariance = Array(priorMean.size) { row ->
            DoubleArray(priorMean.size) { column ->
                priorCovariance[row][column] + gain[row] * gain[column] * varianceChange
            }
        }
        val stabilized = stabilizeCovariance(covariance)
        require(mean.all(Double::isFinite))
        return GaussianProjectionResult(
            mean = mean,
            covariance = stabilized,
            priorProjectionMean = priorProjectionMean,
            priorProjectionVariance = priorProjectionVariance,
            posteriorProjectionMean = moments.mean,
            posteriorProjectionVariance = moments.variance,
            diagnostics = moments.diagnostics
        )
    }

    private fun integrate(
        support: List<ScalarLikelihoodSupport>,
        logDensity: (Double) -> Double
    ): ScalarGridMoments {
        require(support.isNotEmpty())
        require(support.all { it.center.isFinite() && it.standardDeviation.isFinite() && it.standardDeviation > 0.0 })
        var lower = max(
            PHYSICAL_LOG_MIN,
            support.minOf { it.center - SUPPORT_STANDARD_DEVIATIONS * it.standardDeviation }
        )
        var upper = min(
            PHYSICAL_LOG_MAX,
            support.maxOf { it.center + SUPPORT_STANDARD_DEVIATIONS * it.standardDeviation }
        )
        require(lower < upper)
        var expansionCount = 0
        while (true) {
            val step = (upper - lower) / (GRID_POINTS - 1)
            val values = DoubleArray(GRID_POINTS) { index -> lower + index * step }
            val logs = values.map(logDensity)
            val maximum = logs.maxOrNull() ?: Double.NEGATIVE_INFINITY
            require(maximum.isFinite()) { "Scalar likelihood has no finite mass." }
            val unnormalized = DoubleArray(GRID_POINTS) { index -> exp(logs[index] - maximum) }
            val total = unnormalized.sum()
            require(total.isFinite() && total > 0.0)
            val weights = DoubleArray(GRID_POINTS) { index -> unnormalized[index] / total }
            val edgeMass = weights.take(EDGE_POINT_COUNT).sum() + weights.takeLast(EDGE_POINT_COUNT).sum()
            if (edgeMass <= MAX_EDGE_MASS || expansionCount == MAX_EXPANSIONS ||
                lower == PHYSICAL_LOG_MIN && upper == PHYSICAL_LOG_MAX
            ) {
                require(edgeMass <= MAX_EDGE_MASS) { "Scalar posterior retained excessive edge mass." }
                val mean = values.indices.sumOf { index -> values[index] * weights[index] }
                val variance = values.indices.sumOf { index ->
                    (values[index] - mean).pow(2) * weights[index]
                }.coerceAtLeast(MIN_VARIANCE)
                val diagnostics = ScalarGridDiagnostics(
                    gridPointCount = GRID_POINTS,
                    expansionCount = expansionCount,
                    lowerBound = lower,
                    upperBound = upper,
                    edgeMass = edgeMass,
                    normalized = kotlin.math.abs(weights.sum() - 1.0) <= 1e-9,
                    fingerprint = fingerprint(
                        GRID_VERSION,
                        GRID_POINTS.toString(),
                        expansionCount.toString(),
                        lower.toBits().toString(),
                        upper.toBits().toString(),
                        edgeMass.toBits().toString(),
                        mean.toBits().toString(),
                        variance.toBits().toString()
                    )
                )
                return ScalarGridMoments(mean, variance, diagnostics)
            }
            val width = upper - lower
            lower = max(PHYSICAL_LOG_MIN, lower - width * EXPANSION_FRACTION)
            upper = min(PHYSICAL_LOG_MAX, upper + width * EXPANSION_FRACTION)
            expansionCount++
        }
    }

    private fun stabilizeCovariance(source: Array<DoubleArray>): Array<DoubleArray> {
        val symmetric = Array(source.size) { row ->
            DoubleArray(source.size) { column -> (source[row][column] + source[column][row]) / 2.0 }
        }
        for (index in symmetric.indices) symmetric[index][index] =
            symmetric[index][index].coerceAtLeast(MIN_VARIANCE)
        var jitter = 0.0
        repeat(MAX_JITTER_ATTEMPTS) {
            val candidate = Array(symmetric.size) { row ->
                DoubleArray(symmetric.size) { column ->
                    symmetric[row][column] + if (row == column) jitter else 0.0
                }
            }
            if (candidate.all { row -> row.all(Double::isFinite) } && isPositiveSemidefinite(candidate)) {
                return candidate
            }
            jitter = if (jitter == 0.0) INITIAL_JITTER else jitter * 10.0
        }
        error("Projected covariance is not positive semidefinite.")
    }

    private fun isPositiveSemidefinite(matrix: Array<DoubleArray>): Boolean {
        val lower = Array(matrix.size) { DoubleArray(matrix.size) }
        for (row in matrix.indices) {
            for (column in 0..row) {
                val residual = matrix[row][column] -
                    (0 until column).sumOf { inner -> lower[row][inner] * lower[column][inner] }
                if (row == column) {
                    if (residual < -PSD_TOLERANCE) return false
                    lower[row][column] = sqrt(residual.coerceAtLeast(0.0))
                } else if (lower[column][column] > PSD_TOLERANCE) {
                    lower[row][column] = residual / lower[column][column]
                } else if (kotlin.math.abs(residual) > PSD_TOLERANCE) {
                    return false
                }
            }
        }
        return true
    }

    private fun dot(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { index -> left[index] * right[index] }

    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(matrix.size) { row ->
            matrix[row].indices.sumOf { column -> matrix[row][column] * vector[column] }
        }

    private fun normalLogDensity(value: Double, mean: Double, variance: Double): Double =
        -0.5 * (ln(2.0 * Math.PI * variance) + (value - mean).pow(2) / variance)

    const val GRID_VERSION = "strength-scalar-grid-1.0.0"
    private const val GRID_POINTS = 1025
    private const val SUPPORT_STANDARD_DEVIATIONS = 6.0
    private const val EDGE_POINT_COUNT = 8
    private const val MAX_EDGE_MASS = 1e-6
    private const val MAX_EXPANSIONS = 4
    private const val EXPANSION_FRACTION = 0.75
    private const val PHYSICAL_LOG_MIN = 0.0
    private val PHYSICAL_LOG_MAX = ln(2_000.0)
    private const val MIN_VARIANCE = 1e-8
    private const val PSD_TOLERANCE = 1e-9
    private const val INITIAL_JITTER = 1e-10
    private const val MAX_JITTER_ATTEMPTS = 7
}
