package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal class StrictBayesianNumericalException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal class StrictRandom(seed: Long) {
    private val random = Random(seed)
    private var spareGaussian: Double? = null

    fun uniform(): Double = random.nextDouble().coerceIn(Double.MIN_VALUE, 1.0 - 1e-16)

    fun gaussian(): Double {
        spareGaussian?.let { value ->
            spareGaussian = null
            return value
        }
        val radius = sqrt(-2.0 * ln(uniform()))
        val angle = 2.0 * PI * uniform()
        spareGaussian = radius * sin(angle)
        return radius * cos(angle)
    }

    fun gamma(shape: Double, scale: Double = 1.0): Double {
        require(shape > 0.0 && scale > 0.0)
        if (shape < 1.0) {
            return gamma(shape + 1.0, scale) * uniform().pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            val x = gaussian()
            val vBase = 1.0 + c * x
            if (vBase <= 0.0) continue
            val v = vBase * vBase * vBase
            val u = uniform()
            if (u < 1.0 - 0.0331 * x.pow(4.0) || ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) {
                return scale * d * v
            }
        }
    }

    /** Inverse-gamma using the shape/scale convention in Document A. */
    fun inverseGamma(shape: Double, scale: Double): Double {
        require(shape > 0.0 && scale > 0.0)
        val precision = gamma(shape, 1.0 / scale)
        return 1.0 / precision
    }

    fun categorical(probabilities: DoubleArray): Int {
        require(probabilities.isNotEmpty() && probabilities.all { it >= 0.0 && it.isFinite() })
        val draw = uniform() * probabilities.sum()
        var cumulative = 0.0
        probabilities.forEachIndexed { index, probability ->
            cumulative += probability
            if (draw <= cumulative) return index
        }
        return probabilities.lastIndex
    }
}

internal object StrictBayesianMatrix {
    fun identity(size: Int): Array<DoubleArray> =
        Array(size) { row -> DoubleArray(size) { column -> if (row == column) 1.0 else 0.0 } }

    fun transpose(value: Array<DoubleArray>): Array<DoubleArray> = StableLinearAlgebra.transpose(value)

    fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        StableLinearAlgebra.multiply(left, right)

    fun subtract(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(left[0].size) { column -> left[row][column] - right[row][column] } }

    fun add(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(left[0].size) { column -> left[row][column] + right[row][column] } }

    fun symmetrize(value: Array<DoubleArray>): Array<DoubleArray> =
        Array(value.size) { row ->
            DoubleArray(value.size) { column -> (value[row][column] + value[column][row]) / 2.0 }
        }

    fun observationCovariance(x: Array<DoubleArray>, diagonalPrior: DoubleArray): Array<DoubleArray> {
        require(x.isNotEmpty() && x[0].size == diagonalPrior.size)
        return Array(x.size) { row ->
            DoubleArray(x.size) { column ->
                var value = if (row == column) 1.0 else 0.0
                for (coefficient in diagonalPrior.indices) {
                    value += x[row][coefficient] * diagonalPrior[coefficient] * x[column][coefficient]
                }
                value
            }
        }
    }

    fun solveSpdStrict(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> = try {
        StableLinearAlgebra.solveSpdStrict(a, b).solution
    } catch (failure: Throwable) {
        throw StrictBayesianNumericalException("NUMERICAL_SPD_FAILURE", failure)
    }

    fun strictSpdFactor(value: Array<DoubleArray>): Array<DoubleArray> = try {
        StableLinearAlgebra.strictCholesky(value).factor
    } catch (failure: Throwable) {
        throw StrictBayesianNumericalException("NUMERICAL_SPD_FAILURE", failure)
    }

    fun logDetSpdStrict(value: Array<DoubleArray>): Double {
        val factor = strictSpdFactor(value)
        return 2.0 * factor.indices.sumOf { ln(factor[it][it]) }
    }

    fun covarianceScalePosterior(yTilde: Array<DoubleArray>, solved: Array<DoubleArray>): Array<DoubleArray> {
        val cross = multiply(transpose(yTilde), solved)
        return symmetrize(add(identity(cross.size), cross))
    }

    fun drawInverseWishart(scale: Array<DoubleArray>, degreesOfFreedom: Int, random: StrictRandom): Array<DoubleArray> {
        val dimension = scale.size
        require(dimension > 0 && degreesOfFreedom > dimension - 1)
        val scaleFactor = strictSpdFactor(scale)
        val bartlett = Array(dimension) { DoubleArray(dimension) }
        for (row in 0 until dimension) {
            bartlett[row][row] = sqrt(random.gamma((degreesOfFreedom - row) / 2.0, 2.0))
            for (column in 0 until row) bartlett[row][column] = random.gaussian()
        }
        val inverseBartlettTranspose = StableLinearAlgebra.invertUpperTriangularStrict(transpose(bartlett))
        val factor = multiply(scaleFactor, inverseBartlettTranspose)
        return symmetrize(multiply(factor, transpose(factor)))
    }

    fun drawMatrixNormalDiagonalRows(
        diagonalRowCovariance: DoubleArray,
        columnCovariance: Array<DoubleArray>,
        random: StrictRandom
    ): Array<DoubleArray> {
        val columnFactorTranspose = transpose(strictSpdFactor(columnCovariance))
        val standard = Array(diagonalRowCovariance.size) {
            DoubleArray(columnCovariance.size) { random.gaussian() }
        }
        val correlated = multiply(standard, columnFactorTranspose)
        return Array(correlated.size) { row ->
            DoubleArray(correlated[0].size) { column ->
                sqrt(diagonalRowCovariance[row]) * correlated[row][column]
            }
        }
    }

    fun drawMatrixNormalIdentityRows(
        rows: Int,
        columnCovariance: Array<DoubleArray>,
        random: StrictRandom
    ): Array<DoubleArray> = drawMatrixNormalDiagonalRows(DoubleArray(rows) { 1.0 }, columnCovariance, random)

    fun diagonalLeftMultiply(diagonal: DoubleArray, matrix: Array<DoubleArray>): Array<DoubleArray> =
        Array(matrix.size) { row -> DoubleArray(matrix[0].size) { column -> diagonal[row] * matrix[row][column] } }

    fun softmax(logWeights: DoubleArray): DoubleArray {
        require(logWeights.isNotEmpty() && logWeights.all(Double::isFinite))
        val maximum = logWeights.max()
        val shifted = DoubleArray(logWeights.size) { kotlin.math.exp(logWeights[it] - maximum) }
        val total = shifted.sum()
        if (!total.isFinite() || total <= 0.0) throw StrictBayesianNumericalException("NONFINITE_STATE: lag weights")
        return DoubleArray(shifted.size) { shifted[it] / total }
    }

}
