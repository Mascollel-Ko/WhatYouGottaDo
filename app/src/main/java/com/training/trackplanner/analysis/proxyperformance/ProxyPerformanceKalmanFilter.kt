package com.training.trackplanner.analysis.proxyperformance

import kotlin.math.pow
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.NonPositiveDefiniteMatrixException
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector

internal data class ProxyPerformanceModelConfig(
    val variant: ProxyModelVariant,
    val target: MajorLiftTarget,
    val factors: List<ProxyLatentFactor>,
    val persistence: Map<ProxyLatentFactor, Double>,
    val processNoise: Map<ProxyLatentFactor, Double>
)

internal object ProxyPerformanceStateModel {
    const val MODEL_VERSION = "proxy-performance-1.0.0"

    private val sharedFactors = listOf(
        ProxyLatentFactor.PRESS_SHARED,
        ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC,
        ProxyLatentFactor.KNEE_EXTENSION,
        ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN,
        ProxyLatentFactor.TRUNK_BRACING
    )

    fun config(target: MajorLiftTarget, variant: ProxyModelVariant): ProxyPerformanceModelConfig {
        val factors = when (variant) {
            ProxyModelVariant.M0_LOCF -> emptyList()
            ProxyModelVariant.M1_TARGET_ONLY -> listOf(specificFactor(target))
            ProxyModelVariant.M2_SHARED_FACTORS -> sharedFactors
            ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC -> ProxyLatentFactor.entries
        }
        return ProxyPerformanceModelConfig(
            variant = variant,
            target = target,
            factors = factors,
            persistence = factors.associateWith { factor ->
                if (factor.isTargetSpecific()) 0.965 else 0.985
            },
            processNoise = factors.associateWith { factor ->
                if (factor.isTargetSpecific()) 0.035 else 0.020
            }
        )
    }

    fun specificFactor(target: MajorLiftTarget): ProxyLatentFactor = when (target) {
        MajorLiftTarget.BENCH_PRESS -> ProxyLatentFactor.BENCH_SPECIFIC
        MajorLiftTarget.SQUAT -> ProxyLatentFactor.SQUAT_SPECIFIC
        MajorLiftTarget.DEADLIFT -> ProxyLatentFactor.DEADLIFT_SPECIFIC
    }

    private fun ProxyLatentFactor.isTargetSpecific(): Boolean = this in setOf(
        ProxyLatentFactor.BENCH_SPECIFIC,
        ProxyLatentFactor.SQUAT_SPECIFIC,
        ProxyLatentFactor.DEADLIFT_SPECIFIC
    )
}

internal data class ProxyKalmanState(
    val mean: RealVector,
    val covariance: RealMatrix
)

internal data class ProxyKalmanStep(
    val state: ProxyKalmanState,
    val innovation: Double?,
    val innovationVariance: Double?,
    val diagnostics: List<ProxyPerformanceDiagnostic>,
    val updated: Boolean
)

internal class ProxyPerformanceKalmanFilter(
    private val config: ProxyPerformanceModelConfig
) {
    fun initialState(): ProxyKalmanState = ProxyKalmanState(
        mean = ArrayRealVector(config.factors.size),
        covariance = MatrixUtils.createRealIdentityMatrix(config.factors.size)
    )

    fun predict(state: ProxyKalmanState, elapsedDays: Long): ProxyKalmanStep {
        if (elapsedDays <= 0L || config.factors.isEmpty()) {
            return ProxyKalmanStep(state, null, null, emptyList(), updated = false)
        }
        val weeks = elapsedDays / 7.0
        val transition = MatrixUtils.createRealDiagonalMatrix(
            config.factors.map { factor ->
                config.persistence.getValue(factor).pow(weeks)
            }.toDoubleArray()
        )
        val process = MatrixUtils.createRealDiagonalMatrix(
            config.factors.map { factor ->
                config.processNoise.getValue(factor) * weeks
            }.toDoubleArray()
        )
        val predictedMean = transition.operate(state.mean)
        val predictedCovariance = transition.multiply(state.covariance)
            .multiply(transition.transpose())
            .add(process)
        return stabilized(predictedMean, predictedCovariance, fallbackState = state)
    }

    fun update(
        state: ProxyKalmanState,
        observation: Double,
        loading: DoubleArray,
        observationVariance: Double,
        date: java.time.LocalDate? = null,
        workoutEntryId: Long? = null
    ): ProxyKalmanStep {
        if (!observation.isFinite() || !observationVariance.isFinite() || observationVariance <= 0.0 ||
            loading.size != state.mean.dimension || loading.any { value -> !value.isFinite() }
        ) {
            return skipped(
                state,
                ProxyPerformanceDiagnosticCode.NON_FINITE_INPUT,
                "Invalid proxy-performance observation or loading.",
                date,
                workoutEntryId
            )
        }
        if (loading.all { value -> value == 0.0 }) {
            return skipped(
                state,
                ProxyPerformanceDiagnosticCode.OBSERVATION_SKIPPED,
                "Observation has no active loading for this model.",
                date,
                workoutEntryId
            )
        }
        val h = ArrayRealVector(loading, false)
        val innovation = observation - h.dotProduct(state.mean)
        val innovationVariance = h.dotProduct(state.covariance.operate(h)) + observationVariance
        if (!innovationVariance.isFinite() || innovationVariance <= MIN_INNOVATION_VARIANCE) {
            return skipped(
                state,
                ProxyPerformanceDiagnosticCode.SINGULAR_INNOVATION_COVARIANCE,
                "Innovation covariance is singular or non-finite.",
                date,
                workoutEntryId
            )
        }
        val gain = state.covariance.operate(h).mapDivide(innovationVariance)
        val posteriorMean = state.mean.add(gain.mapMultiply(innovation))
        val identity = MatrixUtils.createRealIdentityMatrix(state.mean.dimension)
        val kh = gain.outerProduct(h)
        val residualOperator = identity.subtract(kh)
        val posteriorCovariance = residualOperator.multiply(state.covariance)
            .multiply(residualOperator.transpose())
            .add(gain.outerProduct(gain).scalarMultiply(observationVariance))
        return stabilized(
            mean = posteriorMean,
            covariance = posteriorCovariance,
            innovation = innovation,
            innovationVariance = innovationVariance,
            date = date,
            workoutEntryId = workoutEntryId,
            updated = true,
            fallbackState = state
        )
    }

    private fun stabilized(
        mean: RealVector,
        covariance: RealMatrix,
        innovation: Double? = null,
        innovationVariance: Double? = null,
        date: java.time.LocalDate? = null,
        workoutEntryId: Long? = null,
        updated: Boolean = false,
        fallbackState: ProxyKalmanState
    ): ProxyKalmanStep {
        if (!mean.isFinite() || !covariance.isFinite()) {
            return ProxyKalmanStep(
                state = fallbackState,
                innovation = innovation,
                innovationVariance = innovationVariance,
                diagnostics = listOf(
                    ProxyPerformanceDiagnostic(
                        ProxyPerformanceDiagnosticCode.NON_FINITE_INPUT,
                        "Non-finite Kalman state was rejected.",
                        date,
                        workoutEntryId
                    )
                ),
                updated = false
            )
        }
        val symmetric = covariance.add(covariance.transpose()).scalarMultiply(0.5)
        if (isPositiveDefinite(symmetric)) {
            return ProxyKalmanStep(
                ProxyKalmanState(mean, symmetric),
                innovation,
                innovationVariance,
                emptyList(),
                updated
            )
        }
        JITTER_SEQUENCE.forEach { jitter ->
            val repaired = symmetric.add(
                MatrixUtils.createRealIdentityMatrix(symmetric.rowDimension).scalarMultiply(jitter)
            )
            if (isPositiveDefinite(repaired)) {
                return ProxyKalmanStep(
                    ProxyKalmanState(mean, repaired),
                    innovation,
                    innovationVariance,
                    listOf(
                        ProxyPerformanceDiagnostic(
                            ProxyPerformanceDiagnosticCode.BOUNDED_JITTER_APPLIED,
                            "Applied bounded covariance jitter $jitter.",
                            date,
                            workoutEntryId
                        )
                    ),
                    updated
                )
            }
        }
        return ProxyKalmanStep(
            fallbackState,
            innovation,
            innovationVariance,
            listOf(
                ProxyPerformanceDiagnostic(
                    ProxyPerformanceDiagnosticCode.CHOLESKY_FAILED,
                    "Covariance stabilization failed; observation update was not trusted.",
                    date,
                    workoutEntryId
                )
            ),
            updated = false
        )
    }

    private fun skipped(
        state: ProxyKalmanState,
        code: ProxyPerformanceDiagnosticCode,
        message: String,
        date: java.time.LocalDate?,
        workoutEntryId: Long?
    ): ProxyKalmanStep = ProxyKalmanStep(
        state = state,
        innovation = null,
        innovationVariance = null,
        diagnostics = listOf(ProxyPerformanceDiagnostic(code, message, date, workoutEntryId)),
        updated = false
    )

    private fun isPositiveDefinite(matrix: RealMatrix): Boolean = try {
        CholeskyDecomposition(matrix, 1e-10, 1e-12)
        true
    } catch (_: NonPositiveDefiniteMatrixException) {
        false
    }

    private fun RealVector.isFinite(): Boolean =
        (0 until dimension).all { index -> getEntry(index).isFinite() }

    private fun RealMatrix.isFinite(): Boolean =
        (0 until rowDimension).all { row ->
            (0 until columnDimension).all { column -> getEntry(row, column).isFinite() }
        }

    private companion object {
        const val MIN_INNOVATION_VARIANCE = 1e-12
        val JITTER_SEQUENCE = listOf(1e-10, 1e-9, 1e-8, 1e-7, 1e-6)
    }
}
