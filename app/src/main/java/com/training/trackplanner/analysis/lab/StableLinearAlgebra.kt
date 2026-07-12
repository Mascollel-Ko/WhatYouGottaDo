package com.training.trackplanner.analysis.lab

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.EigenDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.QRDecomposition
import org.apache.commons.math3.linear.RRQRDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition

internal object StableLinearAlgebra {
    const val SYMMETRY_TOLERANCE = 1e-9
    const val POSITIVE_DEFINITE_TOLERANCE = 1e-12
    const val SINGULAR_VALUE_TOLERANCE = 1e-10
    const val MAX_CONDITION_NUMBER = 1e12
    const val MAX_PERMITTED_JITTER_RATIO = 1e-8
    private const val SMALL_SCALE = 1e-12

    data class CholeskyFactor(
        val lower: Array<DoubleArray>,
        val jitter: Double,
        val jitterRatio: Double
    )

    data class SymmetricEigenResult(
        val values: DoubleArray,
        val vectors: Array<DoubleArray>
    )

    data class GeneralizedEigenPair(
        val value: Double,
        val vector: DoubleArray,
        val residual: Double
    )

    fun solveSpd(a: Array<DoubleArray>, b: DoubleArray): DoubleArray =
        spd(a).solver.solve(MatrixUtils.createRealVector(b)).toArray()

    fun solveSpd(a: Array<DoubleArray>, matrixB: Array<DoubleArray>): Array<DoubleArray> =
        spd(a).solver.solve(matrix(matrixB)).data

    fun solveLeastSquares(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val m = matrix(a)
        val solver = leastSquaresSolver(m)
        return solver.solve(MatrixUtils.createRealVector(b)).toArray()
    }

    fun solveLeastSquares(a: Array<DoubleArray>, matrixB: Array<DoubleArray>): Array<DoubleArray> {
        val m = matrix(a)
        val solver = leastSquaresSolver(m)
        return solver.solve(matrix(matrixB)).data
    }

    fun cholesky(a: Array<DoubleArray>): CholeskyFactor {
        val prepared = prepareSymmetric(a)
        val base = tryCholesky(prepared, 0.0)
        if (base != null) return base

        val singular = singularValues(a)
        val rank = rankFromSingularValues(singular)
        if (rank < a.size || conditionNumber(singular) > MAX_CONDITION_NUMBER) {
            throw IllegalArgumentException("SPD matrix is rank deficient or ill-conditioned")
        }
        val scale = diagonalScale(a)
        val jitter = max(scale * MAX_PERMITTED_JITTER_RATIO * 0.5, SMALL_SCALE)
        val jittered = Array(prepared.size) { row ->
            DoubleArray(prepared[row].size) { column -> prepared[row][column] + if (row == column) jitter else 0.0 }
        }
        return tryCholesky(jittered, jitter) ?: throw IllegalArgumentException("SPD matrix failed bounded jitter")
    }

    fun rank(a: Array<DoubleArray>): Int = rankFromSingularValues(singularValues(a))

    fun conditionNumber(a: Array<DoubleArray>): Double = conditionNumber(singularValues(a))

    fun singularValues(a: Array<DoubleArray>): DoubleArray = SingularValueDecomposition(matrix(a)).singularValues

    fun symmetricEigen(a: Array<DoubleArray>): SymmetricEigenResult {
        val prepared = prepareSymmetric(a)
        val decomposition = EigenDecomposition(matrix(prepared))
        val order = decomposition.realEigenvalues.indices.sortedByDescending { decomposition.realEigenvalues[it] }
        return SymmetricEigenResult(
            values = DoubleArray(order.size) { index -> decomposition.getRealEigenvalue(order[index]) },
            vectors = Array(order.size) { index -> decomposition.getEigenvector(order[index]).toArray() }
        )
    }

    fun generalizedSymmetricEigen(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        residualTolerance: Double = 1e-7
    ): List<GeneralizedEigenPair> {
        val factor = cholesky(b).lower
        val y = solveLower(factor, prepareSymmetric(a))
        val c = transpose(solveLower(factor, transpose(y)))
        val asymmetry = relativeAsymmetry(c)
        if (asymmetry > SYMMETRY_TOLERANCE) {
            throw IllegalArgumentException("Whitened matrix is asymmetric: $asymmetry")
        }
        val eigen = symmetricEigen(symmetrize(c))
        return eigen.values.indices.mapNotNull { index ->
            val q = eigen.vectors[index]
            val v = solveUpper(transpose(factor), q)
            val normalized = normalizeBMetric(v, b)
            val residual = generalizedResidual(a, b, eigen.values[index], normalized)
            if (residual <= residualTolerance) {
                GeneralizedEigenPair(eigen.values[index], normalized, residual)
            } else {
                null
            }
        }
    }

    fun johansenFormEigen(
        s00: Array<DoubleArray>,
        s01: Array<DoubleArray>,
        s10: Array<DoubleArray>,
        s11: Array<DoubleArray>
    ): List<GeneralizedEigenPair> {
        val solved = solveSpd(s00, s01)
        return generalizedSymmetricEigen(multiply(s10, solved), s11)
    }

    fun logDetSpd(a: Array<DoubleArray>): Double {
        val lower = cholesky(a).lower
        return 2.0 * lower.indices.sumOf { index -> ln(lower[index][index]) }
    }

    fun isSymmetric(a: Array<DoubleArray>, tolerance: Double = SYMMETRY_TOLERANCE): Boolean =
        relativeAsymmetry(a) <= tolerance

    fun isPositiveDefinite(a: Array<DoubleArray>): Boolean = try {
        cholesky(a)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    fun relativeAsymmetry(a: Array<DoubleArray>): Double {
        val m = matrix(a)
        val numerator = m.subtract(m.transpose()).frobeniusNorm
        return numerator / max(1.0, m.frobeniusNorm)
    }

    internal fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(right[0].size) { column -> left[row].indices.sumOf { index -> left[row][index] * right[index][column] } } }

    internal fun transpose(a: Array<DoubleArray>): Array<DoubleArray> =
        Array(a[0].size) { row -> DoubleArray(a.size) { column -> a[column][row] } }

    private fun leastSquaresSolver(a: RealMatrix) = try {
        val rrqr = RRQRDecomposition(a, SINGULAR_VALUE_TOLERANCE)
        if (rrqr.getRank(SINGULAR_VALUE_TOLERANCE) >= a.columnDimension) rrqr.solver else SingularValueDecomposition(a).solver
    } catch (_: RuntimeException) {
        try {
            QRDecomposition(a).solver
        } catch (_: RuntimeException) {
            SingularValueDecomposition(a).solver
        }
    }

    private fun spd(a: Array<DoubleArray>): CholeskyDecomposition {
        val lower = cholesky(a).lower
        val lowerMatrix = matrix(lower)
        return CholeskyDecomposition(
        lowerMatrix.multiply(lowerMatrix.transpose()),
        SYMMETRY_TOLERANCE,
        POSITIVE_DEFINITE_TOLERANCE
        )
    }

    private fun tryCholesky(a: Array<DoubleArray>, jitter: Double): CholeskyFactor? = try {
        val decomposition = CholeskyDecomposition(matrix(a), SYMMETRY_TOLERANCE, POSITIVE_DEFINITE_TOLERANCE)
        CholeskyFactor(decomposition.l.data, jitter, if (jitter == 0.0) 0.0 else jitter / diagonalScale(a))
    } catch (_: RuntimeException) {
        null
    }

    private fun prepareSymmetric(a: Array<DoubleArray>): Array<DoubleArray> {
        val asymmetry = relativeAsymmetry(a)
        if (asymmetry > SYMMETRY_TOLERANCE) throw IllegalArgumentException("Matrix is asymmetric: $asymmetry")
        return symmetrize(a)
    }

    private fun symmetrize(a: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { row -> DoubleArray(a[row].size) { column -> (a[row][column] + a[column][row]) / 2.0 } }

    private fun solveLower(lower: Array<DoubleArray>, rhs: Array<DoubleArray>): Array<DoubleArray> {
        val out = Array(rhs.size) { DoubleArray(rhs[0].size) }
        for (row in rhs.indices) {
            for (column in rhs[0].indices) {
                out[row][column] = (rhs[row][column] - (0 until row).sumOf { k -> lower[row][k] * out[k][column] }) / lower[row][row]
            }
        }
        return out
    }

    private fun solveLower(lower: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val out = DoubleArray(rhs.size)
        for (row in rhs.indices) {
            out[row] = (rhs[row] - (0 until row).sumOf { column -> lower[row][column] * out[column] }) / lower[row][row]
        }
        return out
    }

    private fun solveUpper(upper: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val out = DoubleArray(rhs.size)
        for (row in rhs.indices.reversed()) {
            out[row] = (rhs[row] - (row + 1 until rhs.size).sumOf { column -> upper[row][column] * out[column] }) / upper[row][row]
        }
        return out
    }

    private fun normalizeBMetric(v: DoubleArray, b: Array<DoubleArray>): DoubleArray {
        val bv = multiply(b, column(v)).map { it[0] }.toDoubleArray()
        val norm = sqrt(v.indices.sumOf { index -> v[index] * bv[index] }).coerceAtLeast(SMALL_SCALE)
        return DoubleArray(v.size) { index -> v[index] / norm }
    }

    private fun generalizedResidual(a: Array<DoubleArray>, b: Array<DoubleArray>, lambda: Double, v: DoubleArray): Double {
        val av = multiply(a, column(v)).map { it[0] }.toDoubleArray()
        val lbv = multiply(b, column(v)).map { row -> lambda * row[0] }.toDoubleArray()
        val numerator = sqrt(av.indices.sumOf { index -> (av[index] - lbv[index]) * (av[index] - lbv[index]) })
        val aNorm = sqrt(av.sumOf { it * it })
        val bNorm = sqrt(lbv.sumOf { it * it })
        return numerator / max(1.0, max(aNorm, bNorm))
    }

    private fun rankFromSingularValues(values: DoubleArray): Int {
        val threshold = (values.maxOrNull() ?: 0.0) * SINGULAR_VALUE_TOLERANCE
        return values.count { it > threshold }
    }

    private fun conditionNumber(values: DoubleArray): Double {
        val maxSingular = values.maxOrNull() ?: return Double.POSITIVE_INFINITY
        val minSingular = values.filter { it > maxSingular * SINGULAR_VALUE_TOLERANCE }.minOrNull() ?: return Double.POSITIVE_INFINITY
        return maxSingular / minSingular
    }

    private fun diagonalScale(a: Array<DoubleArray>): Double =
        max(a.indices.map { abs(a[it][it]) }.average().takeIf { it.isFinite() } ?: 0.0, SMALL_SCALE)

    private fun matrix(a: Array<DoubleArray>): RealMatrix = MatrixUtils.createRealMatrix(a)

    private fun column(values: DoubleArray): Array<DoubleArray> = Array(values.size) { row -> doubleArrayOf(values[row]) }
}
