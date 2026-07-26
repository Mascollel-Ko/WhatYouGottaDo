package com.training.trackplanner.analysis.strengthperformance

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.MatrixUtils

data class StrengthProxyTransferRecord(
    val eventUuid: String,
    val sessionKey: String,
    val exerciseStableKey: String,
    val targetKey: StrengthPerformanceTargetKey,
    val factorLoadings: Map<StrengthFactorKey, Double>,
    val transferCoefficient: Double,
    val innovationResidualLog: Double,
    val innovationVariance: Double,
    val transferLogVariance: Double,
    val evidenceFingerprint: String
) {
    val observationVariance: Double
        get() = innovationVariance + transferLogVariance
}

object StrengthProxyTransfer {
    fun vector(
        schema: List<StrengthFactorKey>,
        loading: StrengthProxyLoadingSpec
    ): DoubleArray = DoubleArray(schema.size) { index ->
        val factor = schema[index]
        loading.factorLoadings[factor]?.times(loading.transferCoefficient)?.times(SHARED_LOADING_SCALE) ?: 0.0
    }.also { vector ->
        require(
            schema.indices.none { index ->
                schema[index].value.startsWith("strength.factor.target.") && vector[index] != 0.0
            }
        ) { "Proxy transfer must not update target-specific factors." }
    }

    fun record(
        eventUuid: String,
        observation: StrengthExerciseSessionObservation,
        localState: StrengthExerciseLocalState,
        localHistory: StrengthExerciseLocalHistory,
        loading: StrengthProxyLoadingSpec
    ): StrengthProxyTransferRecord? {
        if (loading.proxyMode != StrengthProxyMode.LOCAL_INNOVATION_SHARED_ONLY) return null
        if (!localHistory.proxyTransferEligible ||
            localState.twoSidedObservationCount < loading.minimumLocalHistoryCount
        ) return null
        val residual = localHistory.innovationResidualLog ?: return null
        val variance = localHistory.innovationVariance ?: return null
        return StrengthProxyTransferRecord(
            eventUuid = eventUuid,
            sessionKey = observation.sessionKey,
            exerciseStableKey = observation.exerciseStableKey,
            targetKey = loading.targetKey,
            factorLoadings = loading.factorLoadings,
            transferCoefficient = loading.transferCoefficient,
            innovationResidualLog = residual,
            innovationVariance = variance,
            transferLogVariance = loading.transferLogVariance,
            evidenceFingerprint = fingerprint(
                eventUuid,
                observation.evidenceFingerprint,
                loading.targetKey.value,
                loading.configVersion,
                residual.toBits().toString(),
                variance.toBits().toString()
            )
        )
    }

    fun update(
        state: StrengthPosteriorState,
        records: List<StrengthProxyTransferRecord>,
        loadingByRecord: Map<String, StrengthProxyLoadingSpec>
    ): StrengthPosteriorState {
        if (records.isEmpty()) return state
        val ordered = records.sortedBy(StrengthProxyTransferRecord::evidenceFingerprint)
        val rows = ordered.map { record ->
            val loading = checkNotNull(loadingByRecord[record.evidenceFingerprint])
            vector(state.orderedFactorSchema, loading)
        }
        require(rows.all { row -> row.any { it != 0.0 } })

        val h = Array2DRowRealMatrix(rows.map(DoubleArray::copyOf).toTypedArray(), false)
        val priorMean = ArrayRealVector(state.mean, false)
        val priorCovariance = Array2DRowRealMatrix(state.covariance, false)
        val residual = ArrayRealVector(ordered.map(StrengthProxyTransferRecord::innovationResidualLog).toDoubleArray(), false)
        val r = MatrixUtils.createRealDiagonalMatrix(
            ordered.map(StrengthProxyTransferRecord::observationVariance).toDoubleArray()
        )
        val innovationCovariance = h.multiply(priorCovariance).multiply(h.transpose()).add(r)
        val solver = CholeskyDecomposition(innovationCovariance, 1e-10, 1e-12).solver
        val gain = priorCovariance.multiply(h.transpose()).multiply(solver.inverse)
        val posteriorMean = priorMean.add(gain.operate(residual))
        val identityMinusKh = MatrixUtils.createRealIdentityMatrix(state.mean.size).subtract(gain.multiply(h))
        val posteriorCovariance = identityMinusKh.multiply(priorCovariance)
            .multiply(identityMinusKh.transpose())
            .add(gain.multiply(r).multiply(gain.transpose()))
        val mean = posteriorMean.toArray()
        val covariance = ScalarGridPosteriorEngine.stabilizeCovariance(posteriorCovariance.data)
        require(mean.all(Double::isFinite))
        return state.copyDeep(mean = mean, covariance = covariance)
    }

    private const val SHARED_LOADING_SCALE = 0.10
}
