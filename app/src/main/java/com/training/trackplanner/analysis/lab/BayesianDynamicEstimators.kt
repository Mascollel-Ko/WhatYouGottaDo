package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

internal class BayesianVarEstimator {
    fun estimate(
        alignment: TimeSeriesAlignment,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        horizon: Int,
        shockMetric: TrendMetricId
    ): Map<TrendMetricId, List<BayesianIrfPoint>>? {
        val fit = fitSystem(alignment, system, controls, lag, includeErrorCorrection = false) ?: return null
        val impact = strictCholeskyFactorOrNull(fit.residualCovariance) ?: return null
        val shockIndex = system.indexOf(shockMetric).takeIf { it >= 0 } ?: return null
        val history = Array(lag) { DoubleArray(system.size) }
        history[0] = DoubleArray(system.size) { index -> impact[index][shockIndex] }
        val path = (0..horizon).map { step ->
            val state = history[0].copyOf()
            advanceVar(history, fit.lagMatrices)
            step to state
        }
        return system.mapIndexed { responseIndex, metric ->
            metric to path.map { (step, state) ->
                val estimate = state[responseIndex]
                val uncertainty = sqrt((fit.residualCovariance[responseIndex][responseIndex] * (1.0 + step * 0.2)).coerceAtLeast(0.0))
                BayesianIrfPoint(step, estimate, estimate - Z80 * uncertainty, estimate + Z80 * uncertainty, fit.observations)
            }
        }.toMap()
    }

    internal fun fitSystem(
        alignment: TimeSeriesAlignment,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        includeErrorCorrection: Boolean
    ): SystemFit? {
        val raw = system.map { metric -> alignment.valuesByMetric[metric] ?: return null }
        val standardized = raw.map(::standardize).takeIf { values -> values.all { it != null } }?.filterNotNull() ?: return null
        val controlValues = controls.map { metric -> alignment.valuesByMetric[metric]?.let(::standardize) ?: return null }
        val timeIndices = (lag until alignment.weeks.size).filter { time ->
            system.all { metric ->
                alignment.valueAt(metric, time) != null &&
                    (1..lag).all { offset -> alignment.exactLag(metric, time, offset) != null } &&
                    (!includeErrorCorrection || alignment.exactDifference(metric, time) != null)
            } && controls.all { metric -> alignment.valueAt(metric, time) != null }
        }
        val rows = timeIndices.map { time ->
            val design = buildList {
                add(1.0)
                if (includeErrorCorrection) standardized.forEach { values -> add(values[time - 1]) }
                for (offset in 1..lag) standardized.forEach { values -> add(values[time - offset]) }
                controlValues.forEach { values -> add(values[time]) }
            }.toDoubleArray()
            val response = if (includeErrorCorrection) {
                standardized.map { values -> values[time] - values[time - 1] }.toDoubleArray()
            } else {
                standardized.map { values -> values[time] }.toDoubleArray()
            }
            SystemRow(design, response)
        }
        val parameterCount = rows.firstOrNull()?.design?.size ?: return null
        if (rows.size < maxOf(24, 4 * parameterCount)) return null
        val posteriors = system.indices.map { responseIndex ->
            val candidatePosteriors: List<BayesianLinearRegression.Posterior> = GLOBAL_SHRINKAGE_CANDIDATES
                .map { globalShrinkage ->
                    BayesianLinearRegression.fit(
                        rows.map(SystemRow::design),
                        rows.map { it.response[responseIndex] }.toDoubleArray(),
                        minnesotaPrior(parameterCount, system.size, lag, responseIndex, includeErrorCorrection, globalShrinkage)
                    )
                }
                .filterNotNull()
            candidatePosteriors.maxByOrNull(BayesianLinearRegression.Posterior::logEvidence) ?: return null
        }
        val residuals = Array(rows.size) { row ->
            DoubleArray(system.size) { response ->
                rows[row].response[response] - rows[row].design.indices.sumOf { column -> rows[row].design[column] * posteriors[response].mean[column] }
            }
        }
        val covariance = Array(system.size) { left ->
            DoubleArray(system.size) { right -> residuals.sumOf { row -> row[left] * row[right] } / maxOf(1, rows.size - parameterCount) }
        }
        covariance.indices.forEach { index -> covariance[index][index] = covariance[index][index].coerceAtLeast(1e-8) }
        val posteriorPredictiveCoverage = system.indices.map { response ->
            val threshold = Z80 * sqrt(covariance[response][response])
            residuals.count { row -> abs(row[response]) <= threshold }.toDouble() / residuals.size
        }.average()
        val lagOffset = 1 + if (includeErrorCorrection) system.size else 0
        val lagMatrices = (0 until lag).map { offset ->
            Array(system.size) { response -> DoubleArray(system.size) { source -> posteriors[response].mean[lagOffset + offset * system.size + source] } }
        }
        val errorCorrection = if (includeErrorCorrection) {
            Array(system.size) { response -> DoubleArray(system.size) { source -> posteriors[response].mean[1 + source] } }
        } else null
        return SystemFit(lagMatrices, errorCorrection, covariance, rows.size, residuals, timeIndices, posteriorPredictiveCoverage)
    }

    private fun advanceVar(history: Array<DoubleArray>, lagMatrices: List<Array<DoubleArray>>) {
        val next = DoubleArray(history[0].size) { response ->
            lagMatrices.indices.sumOf { lag -> lagMatrices[lag][response].indices.sumOf { source -> lagMatrices[lag][response][source] * history[lag][source] } }
        }
        for (index in history.lastIndex downTo 1) history[index] = history[index - 1]
        history[0] = next
    }

    private fun minnesotaPrior(
        parameterCount: Int,
        systemSize: Int,
        lag: Int,
        responseIndex: Int,
        includeErrorCorrection: Boolean,
        globalShrinkage: Double
    ): DoubleArray {
        val lagOffset = 1 + if (includeErrorCorrection) systemSize else 0
        return DoubleArray(parameterCount) { coefficient ->
            when {
                coefficient == 0 -> 1e-6
                includeErrorCorrection && coefficient < lagOffset -> 1.0 / (0.35 * 0.35)
                coefficient in lagOffset until lagOffset + lag * systemSize -> {
                    val relative = coefficient - lagOffset
                    val lagNumber = relative / systemSize + 1
                    val sourceIndex = relative % systemSize
                    val ownLagMultiplier = if (sourceIndex == responseIndex) 1.0 else CROSS_LAG_SHRINKAGE
                    val standardDeviation = globalShrinkage * ownLagMultiplier / lagNumber.toDouble().pow(LAG_DECAY_POWER)
                    1.0 / (standardDeviation * standardDeviation)
                }
                else -> 1.0 / (EXOGENOUS_PRIOR_STANDARD_DEVIATION * EXOGENOUS_PRIOR_STANDARD_DEVIATION)
            }
        }
    }

    private companion object {
        val GLOBAL_SHRINKAGE_CANDIDATES = doubleArrayOf(0.35, 0.50, 0.70)
        const val CROSS_LAG_SHRINKAGE = 0.55
        const val LAG_DECAY_POWER = 2.0
        const val EXOGENOUS_PRIOR_STANDARD_DEVIATION = 0.50
    }
}

internal class BayesianVecmEstimator {
    fun estimate(
        alignment: TimeSeriesAlignment,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        horizon: Int,
        shockMetric: TrendMetricId,
        cointegrationVector: List<Double>
    ): Map<TrendMetricId, List<BayesianIrfPoint>>? {
        if (cointegrationVector.size != system.size) return null
        val fit = fitVecm(alignment, system, controls, lag, cointegrationVector) ?: return null
        val impact = strictCholeskyFactorOrNull(fit.residualCovariance) ?: return null
        val shockIndex = system.indexOf(shockMetric).takeIf { it >= 0 } ?: return null
        val levels = DoubleArray(system.size)
        val deltaHistory = Array(maxOf(1, lag - 1)) { DoubleArray(system.size) }
        deltaHistory[0] = DoubleArray(system.size) { index -> impact[index][shockIndex] }
        val path = (0..horizon).map { step ->
            levels.indices.forEach { index -> levels[index] += deltaHistory[0][index] }
            val state = levels.copyOf()
            val nextDelta = DoubleArray(system.size) { response ->
                val correction = fit.alpha[response] * fit.beta.indices.sumOf { source -> fit.beta[source] * levels[source] }
                val dynamics = fit.gammaMatrices.indices.sumOf { lagIndex ->
                    fit.gammaMatrices[lagIndex][response].indices.sumOf { source -> fit.gammaMatrices[lagIndex][response][source] * deltaHistory[lagIndex][source] }
                }
                correction + dynamics
            }
            for (index in deltaHistory.lastIndex downTo 1) deltaHistory[index] = deltaHistory[index - 1]
            deltaHistory[0] = nextDelta
            step to state
        }
        return system.mapIndexed { responseIndex, metric ->
            metric to path.map { (step, state) ->
                val estimate = state[responseIndex]
                val uncertainty = sqrt((fit.residualCovariance[responseIndex][responseIndex] * (1.0 + step * 0.25)).coerceAtLeast(0.0))
                BayesianIrfPoint(step, estimate, estimate - Z80 * uncertainty, estimate + Z80 * uncertainty, fit.observations)
            }
        }.toMap()
    }

    private fun fitVecm(
        alignment: TimeSeriesAlignment,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        lag: Int,
        beta: List<Double>
    ): VecmFit? {
        val raw = system.map { metric -> alignment.valuesByMetric[metric] ?: return null }
        val standardized = raw.map(::standardize).takeIf { values -> values.all { it != null } }?.filterNotNull() ?: return null
        val controlValues = controls.map { metric -> alignment.valuesByMetric[metric]?.let(::standardize) ?: return null }
        val timeIndices = (lag until alignment.weeks.size).filter { time ->
            system.all { metric ->
                alignment.exactDifference(metric, time) != null &&
                    alignment.exactLag(metric, time, 1) != null &&
                    (1 until lag).all { offset -> alignment.exactDifference(metric, time - offset) != null }
            } && controls.all { metric -> alignment.valueAt(metric, time) != null }
        }
        val rows = timeIndices.map { time ->
            val design = buildList {
                add(1.0)
                add(beta.indices.sumOf { index -> beta[index] * standardized[index][time - 1] })
                for (offset in 1 until lag) {
                    standardized.forEach { values -> add(values[time - offset] - values[time - offset - 1]) }
                }
                controlValues.forEach { values -> add(values[time]) }
            }.toDoubleArray()
            val response = standardized.map { values -> values[time] - values[time - 1] }.toDoubleArray()
            SystemRow(design, response)
        }
        val parameterCount = rows.firstOrNull()?.design?.size ?: return null
        if (rows.size < maxOf(36, 6 * parameterCount)) return null
        val posteriors = system.indices.map { responseIndex ->
            val prior = DoubleArray(parameterCount) { coefficient ->
                when (coefficient) {
                    0 -> 1e-6
                    1 -> 1.0 / (0.35 * 0.35)
                    else -> 1.0 / (0.5 * 0.5)
                }
            }
            BayesianLinearRegression.fit(rows.map(SystemRow::design), rows.map { it.response[responseIndex] }.toDoubleArray(), prior)
                ?: return null
        }
        val residuals = Array(rows.size) { row ->
            DoubleArray(system.size) { response ->
                rows[row].response[response] - rows[row].design.indices.sumOf { column -> rows[row].design[column] * posteriors[response].mean[column] }
            }
        }
        val covariance = Array(system.size) { left ->
            DoubleArray(system.size) { right -> residuals.sumOf { row -> row[left] * row[right] } / maxOf(1, rows.size - parameterCount) }
        }
        covariance.indices.forEach { index -> covariance[index][index] = covariance[index][index].coerceAtLeast(1e-8) }
        val gammaOffset = 2
        val gammaMatrices = (0 until maxOf(0, lag - 1)).map { offset ->
            Array(system.size) { response ->
                DoubleArray(system.size) { source -> posteriors[response].mean[gammaOffset + offset * system.size + source] }
            }
        }
        return VecmFit(
            beta = beta.toDoubleArray(),
            alpha = DoubleArray(system.size) { response -> posteriors[response].mean[1] },
            gammaMatrices = gammaMatrices,
            residualCovariance = covariance,
            observations = rows.size
        )
    }
}

internal data class SystemFit(
    val lagMatrices: List<Array<DoubleArray>>,
    val errorCorrection: Array<DoubleArray>?,
    val residualCovariance: Array<DoubleArray>,
    val observations: Int,
    val residuals: Array<DoubleArray>,
    val timeIndices: List<Int>,
    val posteriorPredictiveCoverage80: Double
)

internal data class VecmFit(
    val beta: DoubleArray,
    val alpha: DoubleArray,
    val gammaMatrices: List<Array<DoubleArray>>,
    val residualCovariance: Array<DoubleArray>,
    val observations: Int
)

private data class SystemRow(val design: DoubleArray, val response: DoubleArray)

private fun standardize(values: List<Double>): DoubleArray? {
    val finite = values.filter(Double::isFinite)
    val mean = finite.average()
    val standardDeviation = sqrt(variance(finite))
    if (standardDeviation <= 1e-9) return null
    return DoubleArray(values.size) { index ->
        values[index].takeIf(Double::isFinite)?.let { (it - mean) / standardDeviation } ?: Double.NaN
    }
}

internal fun strictCholeskyFactorOrNull(matrix: Array<DoubleArray>): Array<DoubleArray>? {
    return runCatching { StableLinearAlgebra.strictCholesky(matrix).factor }.getOrNull()
}

private const val Z80 = 1.28155
