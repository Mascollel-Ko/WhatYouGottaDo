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
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.linear.SingularValueDecomposition

internal object StableLinearAlgebra {
    const val SYMMETRY_TOLERANCE = 1e-9
    const val POSITIVE_DEFINITE_TOLERANCE = 1e-12
    const val SINGULAR_VALUE_TOLERANCE = 1e-14
    const val MAX_CONDITION_NUMBER = 1e12
    const val MAX_PERMITTED_JITTER_RATIO = 1e-8
    const val MAX_JITTER_ATTEMPTS = 1
    private const val SMALL_SCALE = 1e-12

    data class CholeskyRegularizationPolicy(
        val maxJitterRatio: Double = MAX_PERMITTED_JITTER_RATIO,
        val eigenToleranceRatio: Double = 1e-8,
        val maxAttempts: Int = MAX_JITTER_ATTEMPTS
    )

    data class CholeskyResult(
        val factor: Array<DoubleArray>,
        val effectiveMatrix: Array<DoubleArray>,
        val originalMatrixWasPositiveDefinite: Boolean,
        val regularized: Boolean,
        val jitter: Double,
        val jitterRatio: Double,
        val numericalRank: Int,
        val attempts: Int,
        val conditionNumber: Double,
        val failureCode: CholeskyFailureCode? = null,
        val strictFailureCode: CholeskyFailureCode? = null,
        val regularizationAttempted: Boolean = false,
        val regularizationSucceeded: Boolean = false,
        val minEigenvalue: Double? = null,
        val matrixScale: Double? = null,
        val diagnostics: List<String>
    )

    enum class CholeskyFailureCode {
        NON_SQUARE_INPUT,
        NON_FINITE_INPUT,
        NON_SYMMETRIC_INPUT,
        RANK_DEFICIENT,
        MATERIALLY_INDEFINITE,
        ILL_CONDITIONED,
        CHOLESKY_DECOMPOSITION_FAILED,
        NON_FINITE_FACTOR,
        NUMERICAL_DIAGNOSTIC_FAILURE,
        EXCESSIVE_JITTER_REQUIRED
    }

    class CholeskyFailureException(
        val code: CholeskyFailureCode,
        message: String
    ) : IllegalArgumentException(message)

    data class SpdSolveResult<T>(
        val solution: T,
        val regularized: Boolean,
        val jitter: Double,
        val jitterRatio: Double,
        val numericalRank: Int,
        val conditionNumber: Double,
        val diagnostics: List<String>
    )

    data class LogDetResult(
        val logDeterminant: Double,
        val regularized: Boolean,
        val jitter: Double,
        val jitterRatio: Double,
        val diagnostics: List<String>
    )

    data class SymmetricEigenResult(
        val values: DoubleArray,
        val vectors: Array<DoubleArray>
    )

    data class GeneralizedEigenResult(
        val eigenvalues: DoubleArray,
        val eigenvectors: Array<DoubleArray>,
        val expectedCount: Int,
        val validCount: Int,
        val residuals: DoubleArray,
        val maxResidual: Double,
        val bOrthogonalityError: Double,
        val bCholesky: CholeskyResult,
        val originalB: Array<DoubleArray>,
        val effectiveB: Array<DoubleArray>,
        val regularized: Boolean,
        val jitter: Double,
        val jitterRatio: Double,
        val failedPairIndices: List<Int>,
        val transformedRelativeAsymmetry: Double,
        val diagnostics: List<String>
    )

    enum class GeneralizedEigenFailureCode {
        NON_FINITE_EIGENVALUE,
        NON_FINITE_EIGENVECTOR,
        INVALID_B_METRIC_NORM,
        NON_FINITE_RESIDUAL,
        RESIDUAL_TOLERANCE_EXCEEDED,
        NON_FINITE_ORTHOGONALITY_ERROR,
        B_ORTHOGONALITY_TOLERANCE_EXCEEDED,
        INCOMPLETE_EIGEN_SPECTRUM,
        NON_FINITE_TRANSFORMED_MATRIX
    }

    class GeneralizedEigenFailureException(
        val code: GeneralizedEigenFailureCode,
        message: String
    ) : IllegalArgumentException(message)

    data class JohansenFormEigenResult(
        val eigenResult: GeneralizedEigenResult,
        val s00SolveResult: SpdSolveResult<Array<DoubleArray>>,
        val s11CholeskyResult: CholeskyResult,
        val aMatrix: Array<DoubleArray>,
        val diagnostics: List<String>
    )

    data class OrdinaryLeastSquaresResult(
        val coefficients: DoubleArray,
        val tValues: DoubleArray,
        val residualSumSquares: Double,
        val aic: Double,
        val rank: Int,
        val conditionNumber: Double
    )

    fun solveSpd(a: Array<DoubleArray>, b: DoubleArray): SpdSolveResult<DoubleArray> {
        val factor = regularizedCholesky(a)
        val solution = decompositionFromFactor(factor.factor).solver.solve(MatrixUtils.createRealVector(b)).toArray()
        return factor.solveResult(solution)
    }

    fun solveSpd(a: Array<DoubleArray>, matrixB: Array<DoubleArray>): SpdSolveResult<Array<DoubleArray>> {
        val factor = regularizedCholesky(a)
        val solution = decompositionFromFactor(factor.factor).solver.solve(matrix(matrixB)).data
        return factor.solveResult(solution)
    }

    fun solveSpdStrict(a: Array<DoubleArray>, matrixB: Array<DoubleArray>): SpdSolveResult<Array<DoubleArray>> {
        val factor = strictCholesky(a)
        val solution = decompositionFromFactor(factor.factor).solver.solve(matrix(matrixB)).data
        return factor.solveResult(solution)
    }

    fun solveLeastSquares(a: Array<DoubleArray>, b: DoubleArray): DoubleArray =
        leastSquaresSolver(matrix(a)).solve(MatrixUtils.createRealVector(b)).toArray()

    fun solveLeastSquares(a: Array<DoubleArray>, matrixB: Array<DoubleArray>): Array<DoubleArray> =
        leastSquaresSolver(matrix(a)).solve(matrix(matrixB)).data

    fun ordinaryLeastSquares(x: Array<DoubleArray>, y: DoubleArray): OrdinaryLeastSquaresResult {
        require(x.isNotEmpty() && x.size == y.size)
        require(x.all { it.size == x[0].size && it.all(Double::isFinite) } && y.all(Double::isFinite))
        val columns = x[0].size
        val rank = rank(x)
        if (rank < columns) throw IllegalArgumentException("OLS design rank $rank is below $columns")
        val condition = effectiveConditionNumber(x)
        if (!condition.isFinite() || condition > MAX_CONDITION_NUMBER) {
            throw IllegalArgumentException("OLS design condition $condition exceeds $MAX_CONDITION_NUMBER")
        }
        val coefficients = solveLeastSquares(x, y)
        val residuals = DoubleArray(y.size) { row -> y[row] - x[row].indices.sumOf { column -> x[row][column] * coefficients[column] } }
        val ssr = residuals.sumOf { it * it }
        if (!ssr.isFinite() || ssr <= 0.0 || y.size <= columns) throw IllegalArgumentException("OLS residual variance is not estimable")
        val xtx = Array(columns) { row -> DoubleArray(columns) { column -> x.indices.sumOf { index -> x[index][row] * x[index][column] } } }
        val inverse = solveLeastSquares(xtx, identity(columns))
        val sigma2 = ssr / (y.size - columns)
        val tValues = DoubleArray(columns) { index ->
            val variance = sigma2 * inverse[index][index]
            if (!variance.isFinite() || variance <= 0.0) throw IllegalArgumentException("OLS coefficient variance is not positive")
            coefficients[index] / sqrt(variance)
        }
        val aic = y.size * (ln(2.0 * Math.PI) + 1.0 + ln(ssr / y.size)) + 2.0 * columns
        if (!aic.isFinite()) throw IllegalArgumentException("OLS AIC is not finite")
        return OrdinaryLeastSquaresResult(coefficients, tValues, ssr, aic, rank, condition)
    }

    fun strictCholesky(a: Array<DoubleArray>): CholeskyResult {
        validateMatrix(a)
        val prepared = prepareSymmetric(a)
        val rank = rank(prepared)
        if (rank != prepared.size) {
            throw CholeskyFailureException(CholeskyFailureCode.RANK_DEFICIENT, "strict SPD matrix rank $rank is below ${prepared.size}")
        }
        val condition = conditionNumber(prepared)
        if (!condition.isFinite()) {
            throw CholeskyFailureException(CholeskyFailureCode.NUMERICAL_DIAGNOSTIC_FAILURE, "strict SPD condition number is not finite")
        }
        if (condition > MAX_CONDITION_NUMBER) {
            throw CholeskyFailureException(CholeskyFailureCode.ILL_CONDITIONED, "strict SPD condition number $condition exceeds $MAX_CONDITION_NUMBER")
        }
        val decomposition = try {
            CholeskyDecomposition(matrix(prepared), SYMMETRY_TOLERANCE, POSITIVE_DEFINITE_TOLERANCE)
        } catch (error: RuntimeException) {
            throw CholeskyFailureException(CholeskyFailureCode.CHOLESKY_DECOMPOSITION_FAILED, error.message ?: "strict Cholesky decomposition failed")
        }
        val factor = decomposition.l.data
        if (!factor.isFiniteMatrix()) {
            throw CholeskyFailureException(CholeskyFailureCode.NON_FINITE_FACTOR, "strict Cholesky factor contains non-finite values")
        }
        return CholeskyResult(
            factor = factor,
            effectiveMatrix = prepared,
            originalMatrixWasPositiveDefinite = true,
            regularized = false,
            jitter = 0.0,
            jitterRatio = 0.0,
            numericalRank = rank,
            attempts = 1,
            conditionNumber = condition,
            regularizationSucceeded = false,
            diagnostics = listOf("strict SPD")
        )
    }

    fun regularizedCholesky(
        a: Array<DoubleArray>,
        policy: CholeskyRegularizationPolicy = CholeskyRegularizationPolicy()
    ): CholeskyResult {
        val strictFailure = runCatching { strictCholesky(a) }.fold(
            onSuccess = { return it },
            onFailure = { it as? CholeskyFailureException }
        )
        validateMatrix(a)
        val prepared = prepareSymmetric(a)
        val singularValues = singularValues(prepared)
        val numericalRank = rankFromSingularValues(singularValues, prepared.size, prepared.size)
        if (numericalRank < prepared.size) {
            throw CholeskyFailureException(CholeskyFailureCode.RANK_DEFICIENT, "SPD matrix is rank deficient")
        }
        val spectrum = symmetricEigen(prepared).values
        val scale = diagonalScale(prepared)
        val minEigen = spectrum.minOrNull() ?: throw IllegalArgumentException("empty SPD matrix")
        if (minEigen < -scale * policy.eigenToleranceRatio) {
            throw CholeskyFailureException(CholeskyFailureCode.MATERIALLY_INDEFINITE, "matrix is materially indefinite")
        }
        if (policy.maxAttempts <= 0) throw IllegalArgumentException("regularization attempts disabled")
        val jitter = max(-minEigen + scale * POSITIVE_DEFINITE_TOLERANCE * 10.0, SMALL_SCALE * 10.0)
        val jitterRatio = jitter / scale
        if (jitterRatio > policy.maxJitterRatio) {
            throw CholeskyFailureException(CholeskyFailureCode.EXCESSIVE_JITTER_REQUIRED, "jitter ratio $jitterRatio exceeds ${policy.maxJitterRatio}")
        }
        val jittered = Array(prepared.size) { row ->
            DoubleArray(prepared[row].size) { column -> prepared[row][column] + if (row == column) jitter else 0.0 }
        }
        val decomposition = try {
            CholeskyDecomposition(matrix(jittered), SYMMETRY_TOLERANCE, POSITIVE_DEFINITE_TOLERANCE)
        } catch (error: RuntimeException) {
            throw CholeskyFailureException(CholeskyFailureCode.CHOLESKY_DECOMPOSITION_FAILED, error.message ?: "regularized Cholesky decomposition failed")
        }
        val factor = decomposition.l.data
        if (!factor.isFiniteMatrix()) {
            throw CholeskyFailureException(CholeskyFailureCode.NON_FINITE_FACTOR, "regularized Cholesky factor contains non-finite values")
        }
        val condition = conditionNumber(jittered)
        if (!condition.isFinite()) {
            throw CholeskyFailureException(CholeskyFailureCode.NUMERICAL_DIAGNOSTIC_FAILURE, "regularized condition number is not finite")
        }
        return CholeskyResult(
            factor = factor,
            effectiveMatrix = jittered,
            originalMatrixWasPositiveDefinite = false,
            regularized = true,
            jitter = jitter,
            jitterRatio = jitterRatio,
            numericalRank = numericalRank,
            attempts = 1,
            conditionNumber = condition,
            strictFailureCode = strictFailure?.code,
            regularizationAttempted = true,
            regularizationSucceeded = true,
            minEigenvalue = minEigen,
            matrixScale = scale,
            diagnostics = listOf("bounded diagonal regularization", "strict failure=${strictFailure?.code}")
        )
    }

    fun cholesky(a: Array<DoubleArray>): CholeskyResult = regularizedCholesky(a)

    fun isStrictlyPositiveDefinite(a: Array<DoubleArray>): Boolean = runCatching { strictCholesky(a) }.isSuccess

    fun isRegularizableWithinTolerance(
        a: Array<DoubleArray>,
        policy: CholeskyRegularizationPolicy = CholeskyRegularizationPolicy()
    ): Boolean = runCatching { regularizedCholesky(a, policy) }.isSuccess

    fun isPositiveDefinite(a: Array<DoubleArray>): Boolean = isStrictlyPositiveDefinite(a)

    fun rank(a: Array<DoubleArray>): Int = rankFromSingularValues(singularValues(a), a.size, a.firstOrNull()?.size ?: 0)

    fun conditionNumber(a: Array<DoubleArray>): Double {
        val values = singularValues(a)
        val rows = a.size
        val columns = a.firstOrNull()?.size ?: 0
        if (rankFromSingularValues(values, rows, columns) < minOf(rows, columns)) return Double.POSITIVE_INFINITY
        val maxSingular = values.maxOrNull() ?: return Double.POSITIVE_INFINITY
        val minSingular = values.minOrNull() ?: return Double.POSITIVE_INFINITY
        return if (minSingular <= 0.0) Double.POSITIVE_INFINITY else maxSingular / minSingular
    }

    fun effectiveConditionNumber(a: Array<DoubleArray>): Double {
        val values = singularValues(a)
        val maxSingular = values.maxOrNull() ?: return Double.POSITIVE_INFINITY
        val minSingular = values.filter { it > maxSingular * SINGULAR_VALUE_TOLERANCE }.minOrNull() ?: return Double.POSITIVE_INFINITY
        return maxSingular / minSingular
    }

    fun singularValues(a: Array<DoubleArray>): DoubleArray = SingularValueDecomposition(matrix(a)).singularValues

    fun symmetricEigen(a: Array<DoubleArray>): SymmetricEigenResult {
        val prepared = prepareSymmetric(a)
        val decomposition = EigenDecomposition(matrix(prepared))
        val values = decomposition.realEigenvalues
        val order = values.indices.sortedByDescending { values[it] }
        return SymmetricEigenResult(
            values = DoubleArray(order.size) { index -> decomposition.getRealEigenvalue(order[index]) },
            vectors = Array(order.size) { index -> decomposition.getEigenvector(order[index]).toArray() }
        )
    }

    fun generalizedSymmetricEigen(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        residualTolerance: Double = 1e-7,
        orthogonalityTolerance: Double = 1e-7
    ): GeneralizedEigenResult = generalizedSymmetricEigenRegularized(a, b, residualTolerance, orthogonalityTolerance)

    fun generalizedSymmetricEigenStrict(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        residualTolerance: Double = 1e-7,
        orthogonalityTolerance: Double = 1e-7
    ): GeneralizedEigenResult = generalizedSymmetricEigenWithB(a, b, residualTolerance, orthogonalityTolerance, ::strictCholesky)

    fun generalizedSymmetricEigenRegularized(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        residualTolerance: Double = 1e-7,
        orthogonalityTolerance: Double = 1e-7
    ): GeneralizedEigenResult = generalizedSymmetricEigenWithB(a, b, residualTolerance, orthogonalityTolerance) { regularizedCholesky(it) }

    private fun generalizedSymmetricEigenWithB(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        residualTolerance: Double,
        orthogonalityTolerance: Double,
        bFactor: (Array<DoubleArray>) -> CholeskyResult
    ): GeneralizedEigenResult {
        validateMatrix(a)
        validateMatrix(b)
        val originalB = prepareSymmetric(b)
        val bCholesky = bFactor(originalB)
        val effectiveB = bCholesky.effectiveMatrix
        val lower = bCholesky.factor
        val y = applyLowerTriangular(lower, prepareSymmetric(a))
        val c = transpose(applyLowerTriangular(lower, transpose(y)))
        val asymmetry = relativeAsymmetry(c)
        if (!asymmetry.isFinite() || !c.isFiniteMatrix()) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_TRANSFORMED_MATRIX, "generalized eigen transformed matrix is non-finite")
        }
        if (asymmetry > SYMMETRY_TOLERANCE) {
            throw IllegalArgumentException("Whitened matrix is asymmetric: $asymmetry")
        }
        val eigen = symmetricEigen(symmetrize(c))
        val expected = a.size
        if (eigen.values.size != expected || eigen.vectors.size != expected) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.INCOMPLETE_EIGEN_SPECTRUM, "expected $expected eigenpairs, got ${eigen.values.size}/${eigen.vectors.size}")
        }
        if (eigen.values.any { !it.isFinite() }) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_EIGENVALUE, "non-finite generalized eigenvalue")
        }
        if (eigen.vectors.any { !it.isFiniteVector() }) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_EIGENVECTOR, "non-finite generalized eigenvector")
        }
        val vectors = Array(expected) { index ->
            normalizeBMetric(applyUpperTriangular(transpose(lower), eigen.vectors[index]), effectiveB)
        }
        val residuals = DoubleArray(expected) { index -> generalizedResidual(a, effectiveB, eigen.values[index], vectors[index]) }
        if (residuals.any { !it.isFinite() }) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_RESIDUAL, "non-finite generalized eigen residual")
        }
        val maxResidual = residuals.maxOrNull() ?: Double.POSITIVE_INFINITY
        val failedPairIndices = residuals.indices.filter { residuals[it] > residualTolerance }
        val validCount = expected - failedPairIndices.size
        val bOrthogonality = bOrthogonalityError(vectors, effectiveB)
        val diagnostics = bCholesky.diagnostics + listOf(
            "relative asymmetry=$asymmetry",
            "max residual=$maxResidual",
            "B-orthogonality error=$bOrthogonality"
        )
        if (!maxResidual.isFinite()) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_RESIDUAL, "maximum residual is non-finite")
        }
        if (validCount != expected) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.RESIDUAL_TOLERANCE_EXCEEDED, "invalid generalized eigen residuals: ${residuals.joinToString()}")
        }
        if (!bOrthogonality.isFinite()) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_ORTHOGONALITY_ERROR, "B-orthogonality error is non-finite")
        }
        if (bOrthogonality > orthogonalityTolerance) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.B_ORTHOGONALITY_TOLERANCE_EXCEEDED, "B-orthogonality error $bOrthogonality exceeds $orthogonalityTolerance")
        }
        return GeneralizedEigenResult(
            eigenvalues = eigen.values,
            eigenvectors = vectors,
            expectedCount = expected,
            validCount = validCount,
            residuals = residuals,
            maxResidual = maxResidual,
            bOrthogonalityError = bOrthogonality,
            bCholesky = bCholesky,
            originalB = originalB,
            effectiveB = effectiveB,
            regularized = bCholesky.regularized,
            jitter = bCholesky.jitter,
            jitterRatio = bCholesky.jitterRatio,
            failedPairIndices = failedPairIndices,
            transformedRelativeAsymmetry = asymmetry,
            diagnostics = diagnostics
        )
    }

    fun johansenFormEigen(
        s00: Array<DoubleArray>,
        s01: Array<DoubleArray>,
        s10: Array<DoubleArray>,
        s11: Array<DoubleArray>
    ): JohansenFormEigenResult {
        val solved = solveSpdStrict(s00, s01)
        val a = multiply(s10, solved.solution)
        val eigen = generalizedSymmetricEigenStrict(a, s11)
        return JohansenFormEigenResult(
            eigenResult = eigen,
            s00SolveResult = solved,
            s11CholeskyResult = eigen.bCholesky,
            aMatrix = a,
            diagnostics = solved.diagnostics + eigen.diagnostics
        )
    }

    fun logDetSpd(a: Array<DoubleArray>): LogDetResult {
        val factor = regularizedCholesky(a)
        val logDet = 2.0 * factor.factor.indices.sumOf { index -> ln(factor.factor[index][index]) }
        return LogDetResult(
            logDeterminant = logDet,
            regularized = factor.regularized,
            jitter = factor.jitter,
            jitterRatio = factor.jitterRatio,
            diagnostics = factor.diagnostics
        )
    }

    fun isSymmetric(a: Array<DoubleArray>, tolerance: Double = SYMMETRY_TOLERANCE): Boolean =
        relativeAsymmetry(a) <= tolerance

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

    private fun decompositionFromFactor(lower: Array<DoubleArray>): CholeskyDecomposition {
        val lowerMatrix = matrix(lower)
        return CholeskyDecomposition(
            lowerMatrix.multiply(lowerMatrix.transpose()),
            SYMMETRY_TOLERANCE,
            POSITIVE_DEFINITE_TOLERANCE
        )
    }

    internal fun applyLowerTriangular(lower: Array<DoubleArray>, rhs: Array<DoubleArray>): Array<DoubleArray> {
        val lowerMatrix = matrix(lower)
        return Array(rhs[0].size) { column ->
            val vector = MatrixUtils.createRealVector(DoubleArray(rhs.size) { row -> rhs[row][column] })
            MatrixUtils.solveLowerTriangularSystem(lowerMatrix, vector)
            vector.toArray()
        }.let(::columnsToRows)
    }

    private fun applyUpperTriangular(upper: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val vector = MatrixUtils.createRealVector(rhs.copyOf())
        MatrixUtils.solveUpperTriangularSystem(matrix(upper), vector)
        return vector.toArray()
    }

    private fun prepareSymmetric(a: Array<DoubleArray>): Array<DoubleArray> {
        val asymmetry = relativeAsymmetry(a)
        if (asymmetry > SYMMETRY_TOLERANCE) throw CholeskyFailureException(CholeskyFailureCode.NON_SYMMETRIC_INPUT, "Matrix is asymmetric: $asymmetry")
        return symmetrize(a)
    }

    private fun symmetrize(a: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { row -> DoubleArray(a[row].size) { column -> (a[row][column] + a[column][row]) / 2.0 } }

    private fun normalizeBMetric(v: DoubleArray, b: Array<DoubleArray>): DoubleArray {
        val bv = multiply(b, column(v)).map { it[0] }.toDoubleArray()
        val normSquared = v.indices.sumOf { index -> v[index] * bv[index] }
        if (!normSquared.isFinite() || normSquared <= 0.0) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.INVALID_B_METRIC_NORM, "invalid B-metric norm")
        }
        val norm = sqrt(normSquared)
        val normalized = DoubleArray(v.size) { index -> v[index] / norm }
        if (!normalized.isFiniteVector()) {
            throw GeneralizedEigenFailureException(GeneralizedEigenFailureCode.NON_FINITE_EIGENVECTOR, "normalized generalized eigenvector is non-finite")
        }
        return normalized
    }

    private fun generalizedResidual(a: Array<DoubleArray>, b: Array<DoubleArray>, lambda: Double, v: DoubleArray): Double {
        val av = multiply(a, column(v)).map { it[0] }.toDoubleArray()
        val lbv = multiply(b, column(v)).map { row -> lambda * row[0] }.toDoubleArray()
        val numerator = sqrt(av.indices.sumOf { index -> (av[index] - lbv[index]) * (av[index] - lbv[index]) })
        val aNorm = sqrt(av.sumOf { it * it })
        val bNorm = sqrt(lbv.sumOf { it * it })
        return numerator / max(1.0, max(aNorm, bNorm))
    }

    private fun bOrthogonalityError(vectors: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        val error = Array(vectors.size) { row ->
            DoubleArray(vectors.size) { column ->
                val bv = multiply(b, column(vectors[column])).map { it[0] }.toDoubleArray()
                vectors[row].indices.sumOf { index -> vectors[row][index] * bv[index] } - if (row == column) 1.0 else 0.0
            }
        }
        return matrix(error).frobeniusNorm / max(1.0, sqrt(vectors.size.toDouble()))
    }

    private fun rankFromSingularValues(values: DoubleArray, rows: Int, columns: Int): Int {
        val threshold = (values.maxOrNull() ?: 0.0) * max(rows, columns) * SINGULAR_VALUE_TOLERANCE
        return values.count { it > threshold }
    }

    private fun diagonalScale(a: Array<DoubleArray>): Double =
        max(a.indices.map { abs(a[it][it]) }.average().takeIf { it.isFinite() } ?: 0.0, SMALL_SCALE)

    private fun matrix(a: Array<DoubleArray>): RealMatrix = MatrixUtils.createRealMatrix(a)

    private fun identity(size: Int): Array<DoubleArray> =
        Array(size) { row -> DoubleArray(size) { column -> if (row == column) 1.0 else 0.0 } }

    private fun validateMatrix(a: Array<DoubleArray>) {
        if (a.isEmpty() || a.any { it.size != a.size }) {
            throw CholeskyFailureException(CholeskyFailureCode.NON_SQUARE_INPUT, "matrix must be non-empty and square")
        }
        if (a.any { row -> row.any { !it.isFinite() } }) {
            throw CholeskyFailureException(CholeskyFailureCode.NON_FINITE_INPUT, "matrix contains non-finite values")
        }
    }

    private fun column(values: DoubleArray): Array<DoubleArray> = Array(values.size) { row -> doubleArrayOf(values[row]) }

    private fun columnsToRows(columns: Array<DoubleArray>): Array<DoubleArray> =
        Array(columns[0].size) { row -> DoubleArray(columns.size) { column -> columns[column][row] } }

    private fun <T> CholeskyResult.solveResult(solution: T): SpdSolveResult<T> =
        SpdSolveResult(
            solution = solution,
            regularized = regularized,
            jitter = jitter,
            jitterRatio = jitterRatio,
            numericalRank = numericalRank,
            conditionNumber = conditionNumber,
            diagnostics = diagnostics
        )

    private fun Array<DoubleArray>.isFiniteMatrix(): Boolean = all { row -> row.isFiniteVector() }

    private fun DoubleArray.isFiniteVector(): Boolean = all(Double::isFinite)
}
