package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput
import com.training.trackplanner.analysis.lab.pipeline.HorizonPolicy
import com.training.trackplanner.analysis.lab.pipeline.StrictVariableRole
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import com.training.trackplanner.analysis.trends.TrendMetricId
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import org.apache.commons.math3.special.Gamma

internal object BvarPhaseBDesignMaterializer {
    fun materialize(
        input: FutureBvarInput,
        system: BvarEndogenousSystem,
        grid: BvarModelGridSpec,
        prior: BvarPriorSpec
    ): BvarCommonSample {
        validateInputIdentity(input, system, grid, prior)
        val weeks = input.rowPlan.rows.map { it.sourceWeek }
        if (weeks != weeks.sorted() || weeks.distinct().size != weeks.size) {
            fail(BvarPhaseBFailureCode.INVALID_BVAR_INPUT_IDENTITY, "design", "row weeks must be sorted and unique", input)
        }
        val minRows = maxOf(24, 4 * system.k)
        if (weeks.size < minRows) {
            fail(
                BvarPhaseBFailureCode.INSUFFICIENT_COMMON_BVAR_ROWS,
                "design",
                "common BVAR rows ${weeks.size} below required $minRows",
                input
            )
        }
        val weekIndex = input.view.transformedSeriesByMetric.getValue(system.orderedMetrics.first()).calendar.weeks
            .withIndex()
            .associate { it.value to it.index }
        val y = Array(weeks.size) { rowIndex ->
            val row = input.rowPlan.rows[rowIndex]
            DoubleArray(system.k) { metricIndex ->
                standardizedValue(input, system.orderedMetrics[metricIndex], weekIndex.getValue(row.sourceWeek))
            }
        }
        val qMax = 1 + system.k * grid.pMax
        val xMax = Array(weeks.size) { rowIndex ->
            val row = input.rowPlan.rows[rowIndex]
            DoubleArray(qMax).also { x ->
                x[0] = 1.0
                for (lag in 1..grid.pMax) {
                    val lagWeek = row.lagWeeks[lag]
                        ?: fail(BvarPhaseBFailureCode.ROW_PLAN_LAG_MISMATCH, "design", "row lacks lag $lag", input)
                    val lagIndex = weekIndex.getValue(lagWeek)
                    system.orderedMetrics.forEachIndexed { metricIndex, metric ->
                        x[1 + (lag - 1) * system.k + metricIndex] = standardizedValue(input, metric, lagIndex)
                    }
                }
            }
        }
        val identity = BvarDesignIdentity(
            orderedMetrics = system.orderedMetrics,
            sourceWeeks = weeks,
            pMax = grid.pMax,
            commonRowCount = weeks.size,
            qMax = qMax,
            coefficientOrderingVersion = PHASE_B_COEFFICIENT_ORDERING_VERSION,
            fingerprint = strictFingerprint(
                listOf(
                    input.fingerprint,
                    system.fingerprint,
                    grid.fingerprint,
                    input.rowPlan.fingerprint,
                    input.scalingPlan.fingerprint,
                    weeks.joinToString(","),
                    "Y:${matrixFingerprint(y)}",
                    "XMAX:${matrixFingerprint(xMax)}",
                    PHASE_B_COEFFICIENT_ORDERING_VERSION
                )
            )
        )
        return BvarCommonSample(identity, y, xMax)
    }

    private fun validateInputIdentity(
        input: FutureBvarInput,
        system: BvarEndogenousSystem,
        grid: BvarModelGridSpec,
        prior: BvarPriorSpec
    ) {
        if (input.orderedEndogenousMetrics != system.orderedMetrics || input.priorFingerprint != prior.fingerprint) {
            fail(BvarPhaseBFailureCode.INVALID_BVAR_INPUT_IDENTITY, "input", "FutureBvarInput identity does not match PHASE B system/prior", input)
        }
        if (input.rowPlan.specification.lag != grid.pMax || input.rowPlan.specification.horizonPolicy != HorizonPolicy.NOT_APPLICABLE) {
            fail(BvarPhaseBFailureCode.ROW_PLAN_LAG_MISMATCH, "input", "BVAR row plan must be prepared for pMax without horizons", input)
        }
        if (input.scalingPlan.sourceRowPlanFingerprint != input.rowPlan.fingerprint) {
            fail(BvarPhaseBFailureCode.SCALING_PLAN_MISMATCH, "input", "scaling plan must bind the common BVAR row plan", input)
        }
        val roles = input.rowPlan.specification.requirements.associate { it.metric to it.roles }
        val shockCount = roles.values.count { StrictVariableRole.SHOCK_SOURCE in it }
        if (shockCount != 1 || roles[system.shockSourceMetric]?.contains(StrictVariableRole.SHOCK_SOURCE) != true) {
            fail(BvarPhaseBFailureCode.INVALID_ENDOGENOUS_ORDERING, "input", "BVAR row plan must contain exactly one shock source", input)
        }
        if (roles.values.any { StrictVariableRole.CONTEMPORANEOUS_CONTROL in it || StrictVariableRole.LAGGED_CONTROL in it }) {
            fail(BvarPhaseBFailureCode.INVALID_ENDOGENOUS_ORDERING, "input", "PHASE B BVAR input must not include exogenous controls", input)
        }
        if (system.orderedMetrics.toSet() != input.view.metrics.toSet() || input.scalingPlan.statisticsByMetric.keys != system.orderedMetrics.toSet()) {
            fail(BvarPhaseBFailureCode.SCALING_PLAN_MISMATCH, "input", "view/scaling/system metrics must match exactly", input)
        }
    }

    private fun standardizedValue(input: FutureBvarInput, metric: TrendMetricId, index: Int): Double {
        val value = input.view.value(metric, index)
            ?: fail(BvarPhaseBFailureCode.NONFINITE_PREPARED_VALUE, "design", "missing prepared value for $metric", input)
        if (!value.isFinite()) {
            fail(BvarPhaseBFailureCode.NONFINITE_PREPARED_VALUE, "design", "non-finite prepared value for $metric", input)
        }
        val statistic = input.scalingPlan.statisticsByMetric[metric]
            ?: fail(BvarPhaseBFailureCode.SCALING_PLAN_MISMATCH, "design", "missing scaling statistic for $metric", input)
        if (!statistic.mean.isFinite() || !statistic.scale.isFinite() || statistic.scale <= 0.0) {
            fail(BvarPhaseBFailureCode.SCALING_PLAN_MISMATCH, "design", "invalid scaling statistic for $metric", input)
        }
        return (value - statistic.mean) / statistic.scale
    }
}

internal object BvarPosteriorAlgebra {
    fun fitAll(
        input: FutureBvarInput,
        sample: BvarCommonSample,
        system: BvarEndogenousSystem,
        grid: BvarModelGridSpec,
        prior: BvarPriorSpec
    ): Pair<List<BvarPosteriorParameters>, BvarModelPosterior> {
        val parameters = grid.lagCandidates.flatMap { lag ->
            grid.lambdaGrid.map { lambda ->
                fitCandidate(input, sample, system.k, lag, lambda, prior)
            }
        }.sortedWith(compareBy<BvarPosteriorParameters> { it.modelId.lag }.thenBy { it.modelId.lambda })
        val posterior = modelPosterior(input, parameters)
        return parameters to posterior
    }

    fun fitCandidate(
        input: FutureBvarInput,
        sample: BvarCommonSample,
        k: Int,
        lag: Int,
        lambda: Double,
        prior: BvarPriorSpec
    ): BvarPosteriorParameters {
        val modelId = BvarModelId.create(lag, lambda)
        val q = 1 + k * lag
        val x = sample.xMax.map { row -> row.copyOfRange(0, q) }.toTypedArray()
        val y = sample.y
        val b0 = zero(q, k)
        val v0Diagonal = DoubleArray(q) { index ->
            if (index == 0) prior.interceptVariance else {
                val lagIndex = ((index - 1) / k) + 1
                lambda * lambda / lagIndex.toDouble().powInt((2.0 * prior.lagDecay).toInt())
            }
        }
        val v0 = diagonal(v0Diagonal)
        val v0Precision = diagonal(v0Diagonal.map { 1.0 / it }.toDoubleArray())
        val precision = add(v0Precision, crossProduct(x))
        val rhs = add(multiply(v0Precision, b0), crossProduct(x, y))
        val vn = solveStrict(input, precision, identity(q), BvarPhaseBFailureCode.POSTERIOR_PRECISION_NOT_SPD, modelId)
        val bn = solveStrict(input, precision, rhs, BvarPhaseBFailureCode.POSTERIOR_PRECISION_NOT_SPD, modelId)
        val residual = subtract(y, multiply(x, bn))
        val delta = subtract(bn, b0)
        val nu0 = k + 2.0
        val s0 = identity(k)
        val sn = add(add(s0, crossProduct(residual)), multiply(transpose(delta), multiply(v0Precision, delta)))
        val symSn = symmetrize(sn)
        validateFiniteMatrix(input, vn, BvarPhaseBFailureCode.NONFINITE_POSTERIOR_PARAMETER, "Vn", modelId)
        validateFiniteMatrix(input, bn, BvarPhaseBFailureCode.NONFINITE_POSTERIOR_PARAMETER, "Bn", modelId)
        validateFiniteMatrix(input, symSn, BvarPhaseBFailureCode.NONFINITE_POSTERIOR_PARAMETER, "Sn", modelId)
        val logDetV0 = strictLogVolume(input, v0, BvarPhaseBFailureCode.PRIOR_NOT_SPD, modelId)
        val logDetVN = strictLogVolume(input, vn, BvarPhaseBFailureCode.POSTERIOR_PRECISION_NOT_SPD, modelId)
        val logDetS0 = strictLogVolume(input, s0, BvarPhaseBFailureCode.PRIOR_NOT_SPD, modelId)
        val logDetSN = strictLogVolume(input, symSn, BvarPhaseBFailureCode.POSTERIOR_SCALE_NOT_SPD, modelId)
        val nuN = nu0 + y.size
        if (nu0 <= k + 1.0 || nuN <= k + 1.0) {
            fail(BvarPhaseBFailureCode.INVALID_PRIOR, "posterior", "inverse-Wishart degrees of freedom invalid", input, modelId)
        }
        val logMl = logMarginalLikelihood(y.size, k, nu0, nuN, logDetV0, logDetVN, logDetS0, logDetSN)
        if (!logMl.isFinite()) {
            fail(BvarPhaseBFailureCode.NONFINITE_LOG_MARGINAL_LIKELIHOOD, "evidence", "non-finite log marginal likelihood", input, modelId)
        }
        return BvarPosteriorParameters(
            modelId = modelId,
            b0 = b0,
            v0 = v0,
            v0Precision = v0Precision,
            bN = bn,
            vN = vn,
            nu0 = nu0,
            s0 = s0,
            nuN = nuN,
            sN = symSn,
            logDetV0 = logDetV0,
            logDetVN = logDetVN,
            logDetS0 = logDetS0,
            logDetSN = logDetSN,
            logMarginalLikelihood = logMl,
            fingerprint = strictFingerprint(
                listOf(
                    input.fingerprint,
                    sample.identity.fingerprint,
                    modelId.id,
                    matrixFingerprint(bn),
                    matrixFingerprint(vn),
                    nuN,
                    matrixFingerprint(symSn),
                    logMl,
                    PHASE_B_NUMERICAL_POLICY_VERSION
                )
            )
        )
    }

    fun logMarginalLikelihood(
        n: Int,
        k: Int,
        nu0: Double,
        nuN: Double,
        logDetV0: Double,
        logDetVN: Double,
        logDetS0: Double,
        logDetSN: Double
    ): Double =
        -0.5 * n * k * ln(PI) +
            0.5 * k * (logDetVN - logDetV0) +
            0.5 * nu0 * logDetS0 -
            0.5 * nuN * logDetSN +
            multivariateLogGamma(k, nuN / 2.0) -
            multivariateLogGamma(k, nu0 / 2.0)

    fun multivariateLogGamma(k: Int, a: Double): Double {
        if (k <= 0 || a <= (k - 1) / 2.0) {
            throw BvarPhaseBException(
                BvarPhaseBFailure(
                    BvarPhaseBFailureCode.INTERNAL_NUMERICAL_FAILURE,
                    "multivariate-log-gamma",
                    diagnostic = "invalid multivariate log-gamma domain"
                )
            )
        }
        return k * (k - 1) / 4.0 * ln(PI) + (1..k).sumOf { j -> Gamma.logGamma(a + (1.0 - j) / 2.0) }
    }

    private fun modelPosterior(
        input: FutureBvarInput,
        parameters: List<BvarPosteriorParameters>
    ): BvarModelPosterior {
        val logPrior = -ln(parameters.size.toDouble())
        val logWeights = parameters.map { it.logMarginalLikelihood + logPrior }
        val maxLog = logWeights.maxOrNull()
            ?: fail(BvarPhaseBFailureCode.INVALID_MODEL_POSTERIOR_WEIGHT, "model-posterior", "empty model grid", input)
        val shifted = logWeights.map { exp(it - maxLog) }
        val denominator = shifted.sum()
        if (!denominator.isFinite() || denominator <= 0.0) {
            fail(BvarPhaseBFailureCode.INVALID_MODEL_POSTERIOR_WEIGHT, "model-posterior", "invalid log-sum-exp denominator", input)
        }
        val probabilities = shifted.map { it / denominator }
        val probabilitySum = probabilities.sum()
        if (probabilities.any { !it.isFinite() || it <= 0.0 } || kotlin.math.abs(probabilitySum - 1.0) > 1e-9) {
            fail(BvarPhaseBFailureCode.INVALID_MODEL_POSTERIOR_WEIGHT, "model-posterior", "normalized probabilities are invalid", input)
        }
        val summaries = parameters.indices.map { index ->
            val parameter = parameters[index]
            BvarModelPosteriorSummary(
                modelId = parameter.modelId.id,
                lag = parameter.modelId.lag,
                lambda = parameter.modelId.lambda,
                logMarginalLikelihood = parameter.logMarginalLikelihood,
                logPosteriorWeight = logWeights[index],
                posteriorProbability = probabilities[index]
            )
        }
        val lagMarginal = summaries.groupBy { it.lag }.toSortedMap().mapValues { (_, values) -> values.sumOf { it.posteriorProbability } }
        val lambdaMarginal = summaries.groupBy { it.lambda }.toSortedMap().mapValues { (_, values) -> values.sumOf { it.posteriorProbability } }
        val map = summaries.maxWith(compareBy<BvarModelPosteriorSummary> { it.posteriorProbability }.thenBy { -it.lag }.thenBy { -it.lambda })
        val entropy = -summaries.sumOf { it.posteriorProbability * ln(it.posteriorProbability) }
        val effective = exp(entropy)
        return BvarModelPosterior(
            joint = summaries,
            marginalLag = lagMarginal,
            marginalLambda = lambdaMarginal,
            mapModelId = map.modelId,
            posteriorMeanLag = summaries.sumOf { it.lag * it.posteriorProbability },
            posteriorMeanLambda = summaries.sumOf { it.lambda * it.posteriorProbability },
            entropy = entropy,
            effectiveModelCount = effective,
            fingerprint = strictFingerprint(
                listOf(
                    input.fingerprint,
                    summaries.joinToString(",") { "${it.modelId}:${it.logMarginalLikelihood}:${it.posteriorProbability}" },
                    lagMarginal.entries.joinToString(",") { "${it.key}:${it.value}" },
                    lambdaMarginal.entries.joinToString(",") { "${canonicalDecimal(it.key)}:${it.value}" },
                    map.modelId,
                    entropy,
                    effective,
                    PHASE_B_MODEL_PRIOR_RULE
                )
            )
        )
    }
}

internal fun matrixFingerprint(matrix: Array<DoubleArray>): String =
    strictFingerprint(matrix.map { row -> row.joinToString(",") })

internal fun vectorFingerprint(vector: DoubleArray): String =
    strictFingerprint(vector.map { it.toString() })

internal fun identity(size: Int): Array<DoubleArray> =
    Array(size) { row -> DoubleArray(size) { column -> if (row == column) 1.0 else 0.0 } }

internal fun zero(rows: Int, columns: Int): Array<DoubleArray> =
    Array(rows) { DoubleArray(columns) }

internal fun diagonal(values: DoubleArray): Array<DoubleArray> =
    Array(values.size) { row -> DoubleArray(values.size) { column -> if (row == column) values[row] else 0.0 } }

internal fun add(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
    Array(left.size) { row -> DoubleArray(left[row].size) { column -> left[row][column] + right[row][column] } }

internal fun subtract(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
    Array(left.size) { row -> DoubleArray(left[row].size) { column -> left[row][column] - right[row][column] } }

internal fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
    StableLinearAlgebra.multiply(left, right)

internal fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> =
    StableLinearAlgebra.transpose(matrix)

internal fun crossProduct(x: Array<DoubleArray>): Array<DoubleArray> =
    multiply(transpose(x), x)

internal fun crossProduct(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
    multiply(transpose(left), right)

internal fun symmetrize(matrix: Array<DoubleArray>): Array<DoubleArray> =
    Array(matrix.size) { row -> DoubleArray(matrix[row].size) { column -> (matrix[row][column] + matrix[column][row]) / 2.0 } }

internal fun strictLogVolume(
    input: FutureBvarInput,
    matrix: Array<DoubleArray>,
    failureCode: BvarPhaseBFailureCode,
    modelId: BvarModelId?
): Double {
    val factor = runCatching { StableLinearAlgebra.strictCholesky(matrix) }.getOrElse {
        fail(failureCode, "strict-spd", it.message ?: "strict SPD check failed", input, modelId)
    }
    val value = 2.0 * factor.factor.indices.sumOf { index -> ln(factor.factor[index][index]) }
    if (!value.isFinite()) {
        fail(BvarPhaseBFailureCode.NONFINITE_POSTERIOR_PARAMETER, "strict-spd", "non-finite Cholesky log volume", input, modelId)
    }
    return value
}

internal fun solveStrict(
    input: FutureBvarInput,
    left: Array<DoubleArray>,
    right: Array<DoubleArray>,
    failureCode: BvarPhaseBFailureCode,
    modelId: BvarModelId?
): Array<DoubleArray> =
    runCatching { StableLinearAlgebra.solveSpdStrict(left, right).solution }.getOrElse {
        fail(failureCode, "strict-spd-solve", it.message ?: "strict SPD solve failed", input, modelId)
    }

internal fun validateFiniteMatrix(
    input: FutureBvarInput,
    matrix: Array<DoubleArray>,
    code: BvarPhaseBFailureCode,
    label: String,
    modelId: BvarModelId?
) {
    if (matrix.isEmpty() || matrix.any { row -> row.any { !it.isFinite() } }) {
        fail(code, "finite-matrix", "$label contains non-finite values", input, modelId)
    }
}

internal fun fail(
    code: BvarPhaseBFailureCode,
    stage: String,
    diagnostic: String,
    input: FutureBvarInput? = null,
    modelId: BvarModelId? = null
): Nothing {
    throw BvarPhaseBException(
        BvarPhaseBFailure(
            code = code,
            stage = stage,
            modelId = modelId?.id,
            lag = modelId?.lag,
            lambda = modelId?.lambda,
            sourceFingerprints = input?.let {
                mapOf(
                    "view" to it.view.fingerprint,
                    "rowPlan" to it.rowPlan.fingerprint,
                    "scalingPlan" to it.scalingPlan.fingerprint,
                    "input" to it.fingerprint
                )
            }.orEmpty(),
            diagnostic = diagnostic
        )
    )
}

private fun Double.powInt(power: Int): Double {
    var result = 1.0
    repeat(power) { result *= this }
    return result
}
