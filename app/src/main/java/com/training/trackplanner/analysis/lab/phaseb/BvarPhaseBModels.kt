package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput
import com.training.trackplanner.analysis.lab.pipeline.IdentifiedShockPosterior
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.math.BigDecimal
import java.time.LocalDate

internal const val PHASE_B_NUMERICAL_POLICY_VERSION = "phase-b-numerical-policy-v1"
internal const val PHASE_B_COEFFICIENT_ORDERING_VERSION = "phase-b-var-design-intercept-lag-major-v1"
internal const val PHASE_B_PRIOR_VERSION = "phase-b-conjugate-standardized-minnesota-niw-v1"
internal const val PHASE_B_MODEL_PRIOR_RULE = "uniform-joint-lag-lambda-v1"
internal const val PHASE_B_ALLOCATION_POLICY_VERSION = "phase-b-largest-remainder-allocation-v1"
internal const val PHASE_B_RANDOM_POLICY_VERSION = "phase-b-splitmix64-boxmuller-gamma-v1"
internal const val PHASE_B_SHOCK_NORMALIZATION = "UNIT_STRUCTURAL_STANDARD_DEVIATION"
internal const val TARGET_ACCEPTED_DRAWS = 1000

internal enum class BvarVariableRole {
    SHOCK_SOURCE,
    ENDOGENOUS_STATE
}

internal enum class BvarPhaseBFailureCode {
    INVALID_BVAR_INPUT_IDENTITY,
    INVALID_ENDOGENOUS_ORDERING,
    INSUFFICIENT_ENDOGENOUS_DIMENSION,
    INSUFFICIENT_COMMON_BVAR_ROWS,
    ROW_PLAN_LAG_MISMATCH,
    SCALING_PLAN_MISMATCH,
    NONFINITE_PREPARED_VALUE,
    INVALID_LAG_GRID,
    INVALID_LAMBDA_GRID,
    INVALID_PRIOR,
    PRIOR_NOT_SPD,
    POSTERIOR_PRECISION_NOT_SPD,
    POSTERIOR_SCALE_NOT_SPD,
    NONFINITE_POSTERIOR_PARAMETER,
    NONFINITE_LOG_MARGINAL_LIKELIHOOD,
    INVALID_MODEL_POSTERIOR_WEIGHT,
    POSTERIOR_DRAW_GENERATION_FAILED,
    DRAW_COVARIANCE_NOT_SPD,
    STRUCTURAL_SHOCK_RECONSTRUCTION_FAILED,
    SHOCK_POSTERIOR_IDENTITY_MISMATCH,
    INTERNAL_NUMERICAL_FAILURE
}

internal data class BvarPhaseBFailure(
    val code: BvarPhaseBFailureCode,
    val stage: String,
    val modelId: String? = null,
    val lag: Int? = null,
    val lambda: Double? = null,
    val sourceFingerprints: Map<String, String> = emptyMap(),
    val diagnostic: String
)

internal class BvarPhaseBException(val failure: BvarPhaseBFailure) : IllegalArgumentException(failure.diagnostic)

internal sealed interface BvarPhaseBEstimationResult {
    data class Success(val result: BvarPhaseBResult) : BvarPhaseBEstimationResult
    data class Failure(val failure: BvarPhaseBFailure) : BvarPhaseBEstimationResult
}

internal data class BvarEndogenousSystem(
    val shockSourceMetric: TrendMetricId,
    val orderedMetrics: List<TrendMetricId>,
    val rolesByMetric: Map<TrendMetricId, BvarVariableRole>,
    val fingerprint: String
) {
    val k: Int = orderedMetrics.size
    val shockSourceIndex: Int = orderedMetrics.indexOf(shockSourceMetric)

    companion object {
        fun createValidated(
            shockSourceMetric: TrendMetricId,
            orderedMetrics: List<TrendMetricId>
        ): BvarEndogenousSystem {
            if (orderedMetrics.size !in 2..8) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(
                        BvarPhaseBFailureCode.INSUFFICIENT_ENDOGENOUS_DIMENSION,
                        "endogenous-system",
                        diagnostic = "BVAR endogenous dimension must be between 2 and 8"
                    )
                )
            }
            if (orderedMetrics.distinct().size != orderedMetrics.size || orderedMetrics.count { it == shockSourceMetric } != 1) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(
                        BvarPhaseBFailureCode.INVALID_ENDOGENOUS_ORDERING,
                        "endogenous-system",
                        diagnostic = "BVAR ordered metrics must be unique and include the shock source exactly once"
                    )
                )
            }
            val roles = orderedMetrics.associateWith { metric ->
                if (metric == shockSourceMetric) BvarVariableRole.SHOCK_SOURCE else BvarVariableRole.ENDOGENOUS_STATE
            }
            return BvarEndogenousSystem(
                shockSourceMetric,
                orderedMetrics.toList(),
                roles,
                strictFingerprint(
                    listOf(
                        shockSourceMetric.name,
                        orderedMetrics.joinToString(",") { it.name },
                        BVAR_SYSTEM_VERSION
                    )
                )
            )
        }
    }
}

internal data class BvarModelGridSpec(
    val pMax: Int,
    val lagCandidates: List<Int>,
    val lambdaGrid: List<Double>,
    val lambdaIds: List<String>,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            pMax: Int,
            lambdaGrid: List<Double> = DEFAULT_LAMBDA_GRID
        ): BvarModelGridSpec {
            if (pMax !in 1..4) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(BvarPhaseBFailureCode.INVALID_LAG_GRID, "model-grid", diagnostic = "PHASE B lag grid must be contiguous 1..pMax with pMax in 1..4")
                )
            }
            if (lambdaGrid.isEmpty() || lambdaGrid.any { !it.isFinite() || it <= 0.0 }) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(BvarPhaseBFailureCode.INVALID_LAMBDA_GRID, "model-grid", diagnostic = "lambda grid values must be finite and positive")
                )
            }
            val canonical = lambdaGrid.sorted()
            val ids = canonical.map(::canonicalDecimal)
            if (ids.distinct().size != ids.size) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(BvarPhaseBFailureCode.INVALID_LAMBDA_GRID, "model-grid", diagnostic = "lambda grid contains duplicate canonical values")
                )
            }
            val lags = (1..pMax).toList()
            return BvarModelGridSpec(
                pMax,
                lags,
                canonical,
                ids,
                strictFingerprint(
                    listOf(
                        lags.joinToString(","),
                        ids.joinToString(","),
                        PHASE_B_MODEL_GRID_VERSION
                    )
                )
            )
        }
    }
}

internal data class BvarPriorSpec(
    val interceptVariance: Double,
    val lagDecay: Double,
    val nu0Rule: String,
    val s0Rule: String,
    val b0Rule: String,
    val v0Rule: String,
    val modelPriorRule: String,
    val fingerprint: String
) {
    companion object {
        fun defaultFor(grid: BvarModelGridSpec): BvarPriorSpec {
            val interceptVariance = 100.0
            val lagDecay = 2.0
            return BvarPriorSpec(
                interceptVariance = interceptVariance,
                lagDecay = lagDecay,
                nu0Rule = "nu0=K+2",
                s0Rule = "S0=identity(K)",
                b0Rule = "B0=zero(q,K)",
                v0Rule = "diag(intercept=100,lag=lambda^2/l^(2d),d=2)",
                modelPriorRule = PHASE_B_MODEL_PRIOR_RULE,
                fingerprint = strictFingerprint(
                    listOf(
                        PHASE_B_PRIOR_VERSION,
                        PHASE_B_COEFFICIENT_ORDERING_VERSION,
                        interceptVariance,
                        lagDecay,
                        grid.lambdaIds.joinToString(","),
                        grid.lagCandidates.joinToString(","),
                        "B0_ZERO",
                        "V0_DIAGONAL_STANDARDIZED_MINNESOTA",
                        "NU0_K_PLUS_2",
                        "S0_IDENTITY",
                        PHASE_B_MODEL_PRIOR_RULE,
                        PHASE_B_NUMERICAL_POLICY_VERSION
                    )
                )
            )
        }
    }
}

internal data class BvarDrawSpec(
    val acceptedDrawCount: Int,
    val testOnly: Boolean = false,
    val fingerprint: String = strictFingerprint(listOf(acceptedDrawCount, testOnly, PHASE_B_DRAW_SPEC_VERSION))
) {
    companion object {
        fun production(count: Int = TARGET_ACCEPTED_DRAWS): BvarDrawSpec {
            if (count !in 250..2000) {
                throw BvarPhaseBException(
                    BvarPhaseBFailure(BvarPhaseBFailureCode.POSTERIOR_DRAW_GENERATION_FAILED, "draw-spec", diagnostic = "production draw count must be 250..2000")
                )
            }
            return BvarDrawSpec(count, testOnly = false)
        }

        fun testOnly(count: Int): BvarDrawSpec {
            require(count >= 2)
            return BvarDrawSpec(count, testOnly = true)
        }
    }
}

internal data class BvarPhaseBRequest(
    val preparedInput: FutureBvarInput,
    val system: BvarEndogenousSystem,
    val grid: BvarModelGridSpec,
    val prior: BvarPriorSpec,
    val drawSpec: BvarDrawSpec
)

internal data class BvarDesignIdentity(
    val orderedMetrics: List<TrendMetricId>,
    val sourceWeeks: List<LocalDate>,
    val pMax: Int,
    val commonRowCount: Int,
    val qMax: Int,
    val coefficientOrderingVersion: String,
    val fingerprint: String
)

internal data class BvarCommonSample(
    val identity: BvarDesignIdentity,
    val y: Array<DoubleArray>,
    val xMax: Array<DoubleArray>
)

internal data class BvarModelId(
    val lag: Int,
    val lambda: Double,
    val lambdaId: String,
    val id: String
) {
    companion object {
        fun create(lag: Int, lambda: Double): BvarModelId {
            val lambdaId = canonicalDecimal(lambda)
            return BvarModelId(lag, lambda, lambdaId, "p=$lag|lambda=$lambdaId")
        }
    }
}

internal data class BvarPosteriorParameters(
    val modelId: BvarModelId,
    val b0: Array<DoubleArray>,
    val v0: Array<DoubleArray>,
    val v0Precision: Array<DoubleArray>,
    val bN: Array<DoubleArray>,
    val vN: Array<DoubleArray>,
    val nu0: Double,
    val s0: Array<DoubleArray>,
    val nuN: Double,
    val sN: Array<DoubleArray>,
    val logDetV0: Double,
    val logDetVN: Double,
    val logDetS0: Double,
    val logDetSN: Double,
    val logMarginalLikelihood: Double,
    val fingerprint: String
)

internal data class BvarModelPosteriorSummary(
    val modelId: String,
    val lag: Int,
    val lambda: Double,
    val logMarginalLikelihood: Double,
    val logPosteriorWeight: Double,
    val posteriorProbability: Double
)

internal data class BvarModelPosterior(
    val joint: List<BvarModelPosteriorSummary>,
    val marginalLag: Map<Int, Double>,
    val marginalLambda: Map<Double, Double>,
    val mapModelId: String,
    val posteriorMeanLag: Double,
    val posteriorMeanLambda: Double,
    val entropy: Double,
    val effectiveModelCount: Double,
    val fingerprint: String
)

internal data class BvarPosteriorDraw(
    val drawId: String,
    val modelId: String,
    val lag: Int,
    val lambda: Double,
    val coefficients: Array<DoubleArray>,
    val covariance: Array<DoubleArray>,
    val weight: Double,
    val covarianceFingerprint: String,
    val coefficientFingerprint: String,
    val diagnostics: List<String>
)

internal data class BvarRejectedDrawDiagnostic(
    val drawId: String,
    val modelId: String,
    val reason: String
)

internal data class BvarPhaseBResult(
    val inputFingerprint: String,
    val system: BvarEndogenousSystem,
    val designIdentity: BvarDesignIdentity,
    val prior: BvarPriorSpec,
    val posteriorParameters: List<BvarPosteriorParameters>,
    val modelPosterior: BvarModelPosterior,
    val posteriorDraws: List<BvarPosteriorDraw>,
    val rejectedDrawDiagnostics: List<BvarRejectedDrawDiagnostic>,
    val identifiedShockPosterior: IdentifiedShockPosterior?,
    val numericalDiagnostics: List<String>,
    val warnings: List<String>,
    val fingerprint: String
)

internal fun canonicalDecimal(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal val DEFAULT_LAMBDA_GRID: List<Double> = listOf(0.05, 0.10, 0.20, 0.40, 0.80)

private const val BVAR_SYSTEM_VERSION = "phase-b-endogenous-system-v1"
private const val PHASE_B_MODEL_GRID_VERSION = "phase-b-contiguous-lag-lambda-grid-v1"
private const val PHASE_B_DRAW_SPEC_VERSION = "phase-b-draw-spec-v1"
