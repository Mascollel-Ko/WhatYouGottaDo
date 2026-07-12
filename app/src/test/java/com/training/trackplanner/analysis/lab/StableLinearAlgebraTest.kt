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
        assertArrayClose(item.vector("x"), StableLinearAlgebra.solveSpd(a, item.vector("b")))
        assertMatrixClose(item.matrix("matrix_x"), StableLinearAlgebra.solveSpd(a, item.matrix("matrix_b")))
        assertEquals(item.getDouble("log_det"), StableLinearAlgebra.logDetSpd(a), 1e-8)
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
        val result = StableLinearAlgebra.generalizedSymmetricEigen(a, b)
        assertArrayClose(item.vector("values_desc"), result.map { it.value }.toDoubleArray(), 1e-7)
        result.forEach { pair ->
            assertEquals(1.0, bInner(pair.vector, b), 1e-7)
            assertTrue(pair.residual < 1e-7)
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
        assertArrayClose(item.vector("values_desc"), result.map { it.value }.toDoubleArray(), 1e-7)
        assertTrue(result.all { it.residual < 1e-7 })
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
        val factor = StableLinearAlgebra.cholesky(arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, -1e-9)))
        assertTrue(factor.jitter > 0.0)
        assertTrue(factor.jitterRatio <= StableLinearAlgebra.MAX_PERMITTED_JITTER_RATIO)
        assertTrue(runCatching { StableLinearAlgebra.cholesky(fixture.getJSONObject("near_singular_spd").matrix("a")) }.isFailure)
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
