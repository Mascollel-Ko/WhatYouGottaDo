package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal object BvarPosteriorSampler {
    fun generateDraws(
        input: FutureBvarInput,
        sample: BvarCommonSample,
        modelPosterior: BvarModelPosterior,
        parameters: List<BvarPosteriorParameters>,
        drawSpec: BvarDrawSpec
    ): Pair<List<BvarPosteriorDraw>, List<BvarRejectedDrawDiagnostic>> {
        val parameterById = parameters.associateBy { it.modelId.id }
        val allocation = allocate(modelPosterior, drawSpec.acceptedDrawCount)
        val draws = mutableListOf<BvarPosteriorDraw>()
        val rejected = mutableListOf<BvarRejectedDrawDiagnostic>()
        allocation.entries.sortedBy { it.key }.forEach { (modelId, count) ->
            val parameter = parameterById.getValue(modelId)
            val modelWeight = modelPosterior.joint.single { it.modelId == modelId }.posteriorProbability
            repeat(count) { ordinal ->
                val accepted = drawWithRetries(
                    input = input,
                    sample = sample,
                    parameter = parameter,
                    drawOrdinal = ordinal,
                    drawWeight = modelWeight / count,
                    rejected = rejected
                )
                draws += accepted
            }
        }
        val total = draws.sumOf { it.weight }
        if (draws.any { !it.weight.isFinite() || it.weight <= 0.0 } || kotlin.math.abs(total - 1.0) > 1e-9) {
            fail(BvarPhaseBFailureCode.INVALID_MODEL_POSTERIOR_WEIGHT, "draw-allocation", "draw weights do not sum to one", input)
        }
        return draws.sortedBy { it.drawId } to rejected.sortedWith(compareBy<BvarRejectedDrawDiagnostic> { it.drawId }.thenBy { it.reason })
    }

    fun allocate(modelPosterior: BvarModelPosterior, acceptedDrawCount: Int): Map<String, Int> {
        val positive = modelPosterior.joint.filter { it.posteriorProbability > 0.0 && it.posteriorProbability.isFinite() }
            .sortedBy { it.modelId }
        if (acceptedDrawCount < positive.size) {
            throw BvarPhaseBException(
                BvarPhaseBFailure(
                    BvarPhaseBFailureCode.POSTERIOR_DRAW_GENERATION_FAILED,
                    "draw-allocation",
                    diagnostic = "draw count $acceptedDrawCount cannot give every positive-weight model one draw"
                )
            )
        }
        val remaining = acceptedDrawCount - positive.size
        val base = positive.associate { it.modelId to 1 }.toMutableMap()
        val extras = positive.map { summary ->
            val quota = summary.posteriorProbability * remaining
            AllocationRemainder(summary.modelId, floor(quota).toInt(), quota - floor(quota))
        }
        extras.forEach { base[it.modelId] = base.getValue(it.modelId) + it.floor }
        var left = acceptedDrawCount - base.values.sum()
        extras.sortedWith(compareByDescending<AllocationRemainder> { it.remainder }.thenBy { it.modelId }).forEach {
            if (left > 0) {
                base[it.modelId] = base.getValue(it.modelId) + 1
                left -= 1
            }
        }
        return base.toSortedMap()
    }

    private fun drawWithRetries(
        input: FutureBvarInput,
        sample: BvarCommonSample,
        parameter: BvarPosteriorParameters,
        drawOrdinal: Int,
        drawWeight: Double,
        rejected: MutableList<BvarRejectedDrawDiagnostic>
    ): BvarPosteriorDraw {
        for (attempt in 0 until MAX_ATTEMPTS_PER_DRAW) {
            val drawId = canonicalDrawId(input, parameter, drawOrdinal, attempt)
            val attemptResult = runCatching {
                val covariance = drawInverseWishart(input, parameter, drawOrdinal, attempt)
                val coefficients = drawMatrixNormal(input, parameter, covariance, drawOrdinal, attempt)
                validateFiniteMatrix(input, covariance, BvarPhaseBFailureCode.DRAW_COVARIANCE_NOT_SPD, "SigmaDraw", parameter.modelId)
                validateFiniteMatrix(input, coefficients, BvarPhaseBFailureCode.NONFINITE_POSTERIOR_PARAMETER, "BDraw", parameter.modelId)
                strictLogVolume(input, covariance, BvarPhaseBFailureCode.DRAW_COVARIANCE_NOT_SPD, parameter.modelId)
                BvarPosteriorDraw(
                    drawId = drawId,
                    modelId = parameter.modelId.id,
                    lag = parameter.modelId.lag,
                    lambda = parameter.modelId.lambda,
                    coefficients = coefficients,
                    covariance = covariance,
                    weight = drawWeight,
                    covarianceFingerprint = matrixFingerprint(covariance),
                    coefficientFingerprint = matrixFingerprint(coefficients),
                    diagnostics = listOf("direct conjugate posterior draw", "attempt=$attempt")
                )
            }
            attemptResult.onSuccess { return it }
            rejected += BvarRejectedDrawDiagnostic(drawId, parameter.modelId.id, attemptResult.exceptionOrNull()?.message ?: "draw attempt failed")
        }
        fail(
            BvarPhaseBFailureCode.POSTERIOR_DRAW_GENERATION_FAILED,
            "posterior-draw",
            "unable to produce accepted draw after $MAX_ATTEMPTS_PER_DRAW attempts",
            input,
            parameter.modelId
        )
    }

    private fun drawInverseWishart(
        input: FutureBvarInput,
        parameter: BvarPosteriorParameters,
        drawOrdinal: Int,
        attempt: Int
    ): Array<DoubleArray> {
        val k = parameter.sN.size
        val sPrecision = solveStrict(input, parameter.sN, identity(k), BvarPhaseBFailureCode.POSTERIOR_SCALE_NOT_SPD, parameter.modelId)
        val c = runCatching { StableLinearAlgebra.strictCholesky(sPrecision).factor }.getOrElse {
            fail(BvarPhaseBFailureCode.POSTERIOR_SCALE_NOT_SPD, "inverse-wishart", it.message ?: "scale precision Cholesky failed", input, parameter.modelId)
        }
        val rng = PhaseBDeterministicRandom(seedFor(input, parameter.modelId.id, drawOrdinal, attempt, "inverse-wishart"))
        val a = zero(k, k)
        for (row in 0 until k) {
            val df = parameter.nuN - row
            if (df <= 0.0) {
                fail(BvarPhaseBFailureCode.POSTERIOR_DRAW_GENERATION_FAILED, "inverse-wishart", "invalid Bartlett degrees of freedom", input, parameter.modelId)
            }
            a[row][row] = sqrt(rng.chiSquare(df))
            for (column in 0 until row) {
                a[row][column] = rng.normal()
            }
        }
        val ca = multiply(c, a)
        val wishart = multiply(ca, transpose(ca))
        return solveStrict(input, wishart, identity(k), BvarPhaseBFailureCode.DRAW_COVARIANCE_NOT_SPD, parameter.modelId).let(::symmetrize)
    }

    private fun drawMatrixNormal(
        input: FutureBvarInput,
        parameter: BvarPosteriorParameters,
        covariance: Array<DoubleArray>,
        drawOrdinal: Int,
        attempt: Int
    ): Array<DoubleArray> {
        val lv = runCatching { StableLinearAlgebra.strictCholesky(parameter.vN).factor }.getOrElse {
            fail(BvarPhaseBFailureCode.POSTERIOR_PRECISION_NOT_SPD, "matrix-normal", it.message ?: "Vn Cholesky failed", input, parameter.modelId)
        }
        val ls = runCatching { StableLinearAlgebra.strictCholesky(covariance).factor }.getOrElse {
            fail(BvarPhaseBFailureCode.DRAW_COVARIANCE_NOT_SPD, "matrix-normal", it.message ?: "Sigma draw Cholesky failed", input, parameter.modelId)
        }
        val rng = PhaseBDeterministicRandom(seedFor(input, parameter.modelId.id, drawOrdinal, attempt, "matrix-normal"))
        val z = Array(parameter.bN.size) { DoubleArray(parameter.bN[0].size) { rng.normal() } }
        return add(parameter.bN, multiply(multiply(lv, z), transpose(ls)))
    }

    private data class AllocationRemainder(val modelId: String, val floor: Int, val remainder: Double)

    private const val MAX_ATTEMPTS_PER_DRAW = 5
}

internal class PhaseBDeterministicRandom(seed: Long) {
    private var state: Long = seed
    private var cachedNormal: Double? = null

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun uniformOpen(): Double {
        val value = ((nextLong() ushr 11) * DOUBLE_UNIT).coerceIn(DOUBLE_UNIT, 1.0 - DOUBLE_UNIT)
        return value
    }

    fun normal(): Double {
        cachedNormal?.let {
            cachedNormal = null
            return it
        }
        val u1 = uniformOpen()
        val u2 = uniformOpen()
        val radius = sqrt(-2.0 * ln(u1))
        val angle = 2.0 * PI * u2
        cachedNormal = radius * sin(angle)
        return radius * cos(angle)
    }

    fun chiSquare(degreesOfFreedom: Double): Double = gamma(shape = degreesOfFreedom / 2.0, scale = 2.0)

    fun gamma(shape: Double, scale: Double): Double {
        require(shape > 0.0 && scale > 0.0)
        if (shape < 1.0) {
            return gamma(shape + 1.0, scale) * uniformOpen().pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            val x = normal()
            val vBase = 1.0 + c * x
            if (vBase <= 0.0) continue
            val v = vBase * vBase * vBase
            val u = uniformOpen()
            if (u < 1.0 - 0.0331 * x * x * x * x) return scale * d * v
            if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return scale * d * v
        }
    }

    private companion object {
        const val GOLDEN_GAMMA: Long = -7046029254386353131L
        const val DOUBLE_UNIT: Double = 1.0 / (1L shl 53)
    }
}

internal fun canonicalDrawId(
    input: FutureBvarInput,
    parameter: BvarPosteriorParameters,
    drawOrdinal: Int,
    attempt: Int
): String = strictFingerprint(
    listOf(
        input.fingerprint,
        parameter.fingerprint,
        parameter.modelId.id,
        drawOrdinal,
        attempt,
        "draw-id",
        PHASE_B_RANDOM_POLICY_VERSION
    )
)

internal fun seedFor(
    input: FutureBvarInput,
    modelId: String,
    drawOrdinal: Int,
    attempt: Int,
    componentTag: String
): Long {
    val hex = strictFingerprint(
        listOf(
            input.fingerprint,
            modelId,
            drawOrdinal,
            attempt,
            componentTag,
            PHASE_B_RANDOM_POLICY_VERSION
        )
    ).substring(0, 16)
    return java.lang.Long.parseUnsignedLong(hex, 16)
}
