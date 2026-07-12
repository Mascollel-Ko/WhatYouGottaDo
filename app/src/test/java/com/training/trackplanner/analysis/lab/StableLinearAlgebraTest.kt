package com.training.trackplanner.analysis.lab

import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableLinearAlgebraTest {
    private val fixture = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_a_linear_algebra.json").readText())
    private val tolerance = fixture.getJSONObject("provenance").getDouble("tolerance")

    @Test
    fun commonsMathDependencyLoads() {
        assertTrue(StableLinearAlgebra.singularValues(arrayOf(doubleArrayOf(1.0))).single() == 1.0)
    }

    @Test
    fun spdSolveMultiRhsAndLogDetMatchPythonFixture() {
        val item = fixture.getJSONObject("spd_solve")
        val a = item.matrix("a")
        val vectorSolve = StableLinearAlgebra.solveSpd(a, item.vector("b"))
        val matrixSolve = StableLinearAlgebra.solveSpd(a, item.matrix("matrix_b"))
        val logDet = StableLinearAlgebra.logDetSpd(a)
        assertArrayClose(item.vector("x"), vectorSolve.solution)
        assertMatrixClose(item.matrix("matrix_x"), matrixSolve.solution)
        assertEquals(item.getDouble("log_det"), logDet.logDeterminant, 1e-8)
        assertFalse(vectorSolve.regularized)
        assertFalse(logDet.regularized)
    }

    @Test
    fun rankDeficientLeastSquaresUsesStableFallback() {
        val item = fixture.getJSONObject("rank_deficient_least_squares")
        assertArrayClose(item.vector("x"), StableLinearAlgebra.solveLeastSquares(item.matrix("a"), item.vector("b")), 1e-7)
        assertEquals(item.getInt("rank"), StableLinearAlgebra.rank(item.matrix("a")))
    }

    @Test
    fun singularValuesRankAndConditionNumberMatchFixture() {
        val item = fixture.getJSONObject("svd")
        val a = item.matrix("a")
        assertArrayClose(item.vector("singular_values"), StableLinearAlgebra.singularValues(a), 1e-8)
        assertEquals(item.getInt("rank"), StableLinearAlgebra.rank(a))
        assertEquals(item.getDouble("condition_number"), StableLinearAlgebra.conditionNumber(a), 1e-7)
        val deficient = fixture.getJSONObject("rank_deficient_condition_number")
        assertEquals(deficient.getInt("rank"), StableLinearAlgebra.rank(deficient.matrix("a")))
        assertTrue(StableLinearAlgebra.conditionNumber(deficient.matrix("a")).isInfinite())
    }

    @Test
    fun symmetricEigenReturnsFullDescendingSpectrum() {
        val item = fixture.getJSONObject("symmetric_eigen")
        val result = StableLinearAlgebra.symmetricEigen(item.matrix("a"))
        assertArrayClose(item.vector("values_desc"), result.values, 1e-8)
        result.values.indices.forEach { index ->
            val residual = eigenResidual(item.matrix("a"), result.values[index], result.vectors[index])
            assertTrue(residual < 1e-8)
        }
    }

    @Test
    fun generalizedEigenMatchesScipySpectrumAndBNormalization() {
        val item = fixture.getJSONObject("generalized_symmetric_eigen")
        val a = item.matrix("a")
        val b = item.matrix("b")
        val result = StableLinearAlgebra.generalizedSymmetricEigenStrict(a, b)
        assertEquals(a.size, result.expectedCount)
        assertEquals(result.expectedCount, result.validCount)
        assertArrayClose(item.vector("values_desc"), result.eigenvalues, 1e-7)
        result.eigenvectors.forEachIndexed { index, vector ->
            assertEquals(1.0, bInner(vector, b), 1e-7)
            assertTrue(result.residuals[index] < 1e-7)
        }
        assertTrue(result.bOrthogonalityError < 1e-7)
        assertMatrixClose(b, result.effectiveB)
        assertFalse(result.regularized)
        assertTrue(runCatching { StableLinearAlgebra.generalizedSymmetricEigenStrict(a, b, residualTolerance = 1e-30) }.isFailure)
    }

    @Test
    fun regularizedGeneralizedEigenUsesEffectiveBForValidation() {
        val item = fixture.getJSONObject("regularized_generalized_symmetric_eigen")
        val result = StableLinearAlgebra.generalizedSymmetricEigenRegularized(item.matrix("a"), item.matrix("b"))

        assertTrue(result.regularized)
        assertMatrixClose(item.matrix("effective_b"), result.effectiveB)
        result.eigenvectors.forEachIndexed { index, vector ->
            assertEquals(1.0, bInner(vector, result.effectiveB), 1e-7)
            assertTrue(result.residuals[index] < 1e-7)
        }
    }

    @Test
    fun johansenFormPrimitiveMatchesScipySpectrum() {
        val item = fixture.getJSONObject("johansen_form")
        val result = StableLinearAlgebra.johansenFormEigen(
            item.matrix("s00"),
            item.matrix("s01"),
            item.matrix("s10"),
            item.matrix("s11")
        )
        assertEquals(result.eigenResult.expectedCount, result.eigenResult.validCount)
        assertArrayClose(item.vector("values_desc"), result.eigenResult.eigenvalues, 1e-7)
        assertTrue(result.eigenResult.residuals.all { it < 1e-7 })
        assertFalse(result.s00SolveResult.regularized)
        assertFalse(result.s11CholeskyResult.regularized)
        assertEquals(result.s00SolveResult.numericalRank, item.matrix("s00").size)
    }

    @Test
    fun johansenFormPrimitiveRejectsRankDeficientInputsInStrictMode() {
        val rankDeficientS00 = fixture.getJSONObject("johansen_rank_deficient_s00")
        assertTrue(
            runCatching {
                StableLinearAlgebra.johansenFormEigen(
                    rankDeficientS00.matrix("s00"),
                    rankDeficientS00.matrix("s01"),
                    rankDeficientS00.matrix("s10"),
                    rankDeficientS00.matrix("s11")
                )
            }.isFailure
        )
        val rankDeficientS11 = fixture.getJSONObject("johansen_rank_deficient_s11")
        assertTrue(
            runCatching {
                StableLinearAlgebra.johansenFormEigen(
                    rankDeficientS11.matrix("s00"),
                    rankDeficientS11.matrix("s01"),
                    rankDeficientS11.matrix("s10"),
                    rankDeficientS11.matrix("s11")
                )
            }.isFailure
        )
    }

    @Test
    fun asymmetryAndNonPositiveDefiniteInputsFail() {
        assertFalse(StableLinearAlgebra.isSymmetric(fixture.getJSONObject("asymmetric_failure").matrix("a")))
        assertTrue(
            runCatching {
                StableLinearAlgebra.generalizedSymmetricEigen(
                    fixture.getJSONObject("non_pd_b_failure").matrix("a"),
                    fixture.getJSONObject("non_pd_b_failure").matrix("b")
                )
            }.isFailure
        )
    }

    @Test
    fun boundedJitterIsRecordedAndUnboundedJitterIsRejected() {
        val strict = StableLinearAlgebra.strictCholesky(fixture.getJSONObject("strict_spd").matrix("a"))
        assertTrue(strict.originalMatrixWasPositiveDefinite)
        assertFalse(strict.regularized)
        assertFalse(StableLinearAlgebra.isStrictlyPositiveDefinite(fixture.getJSONObject("regularizable_numerical_noise").matrix("a")))
        val factor = StableLinearAlgebra.regularizedCholesky(fixture.getJSONObject("regularizable_numerical_noise").matrix("a"))
        assertFalse(factor.originalMatrixWasPositiveDefinite)
        assertTrue(factor.regularized)
        assertTrue(factor.jitter > 0.0)
        assertTrue(factor.jitterRatio <= StableLinearAlgebra.MAX_PERMITTED_JITTER_RATIO)
        assertEquals(factor.effectiveMatrix.size, factor.numericalRank)
        assertTrue(runCatching { StableLinearAlgebra.regularizedCholesky(fixture.getJSONObject("materially_indefinite").matrix("a")) }.isFailure)
    }

    @Test
    fun singularPsdIsNotRegularizedIntoSpd() {
        val singular = fixture.getJSONObject("exact_singular_psd").matrix("a")
        val failure = runCatching { StableLinearAlgebra.regularizedCholesky(singular) }.exceptionOrNull()
        assertTrue(failure is StableLinearAlgebra.CholeskyFailureException)
        assertEquals(StableLinearAlgebra.CholeskyFailureCode.RANK_DEFICIENT, (failure as StableLinearAlgebra.CholeskyFailureException).code)
    }

    private fun existingFile(path: String): File = listOf(File(path), File("../$path")).first(File::exists)

    private fun JSONObject.matrix(name: String): Array<DoubleArray> = getJSONArray(name).matrix()
    private fun JSONObject.vector(name: String): DoubleArray = getJSONArray(name).vector()

    private fun JSONArray.matrix(): Array<DoubleArray> =
        Array(length()) { row -> getJSONArray(row).vector() }

    private fun JSONArray.vector(): DoubleArray =
        DoubleArray(length()) { index -> getDouble(index) }

    private fun assertArrayClose(expected: DoubleArray, actual: DoubleArray, allowed: Double = tolerance) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index -> assertEquals(expected[index], actual[index], allowed) }
    }

    private fun assertMatrixClose(expected: Array<DoubleArray>, actual: Array<DoubleArray>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { row -> assertArrayEquals(expected[row], actual[row], tolerance) }
    }

    private fun eigenResidual(a: Array<DoubleArray>, value: Double, vector: DoubleArray): Double {
        val av = multiply(a, vector)
        val lv = vector.map { it * value }.toDoubleArray()
        return norm(av.indices.map { av[it] - lv[it] }.toDoubleArray()) / norm(av).coerceAtLeast(1.0)
    }

    private fun bInner(vector: DoubleArray, b: Array<DoubleArray>): Double {
        val bv = multiply(b, vector)
        return vector.indices.sumOf { index -> vector[index] * bv[index] }
    }

    private fun multiply(a: Array<DoubleArray>, v: DoubleArray): DoubleArray =
        DoubleArray(a.size) { row -> v.indices.sumOf { column -> a[row][column] * v[column] } }

    private fun norm(values: DoubleArray): Double = sqrt(values.sumOf { it * it })
}
