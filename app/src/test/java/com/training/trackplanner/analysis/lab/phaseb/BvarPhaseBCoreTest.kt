package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.pipeline.BvarPreparedView
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput
import com.training.trackplanner.analysis.lab.pipeline.PreparedAnalysisContext
import com.training.trackplanner.analysis.lab.pipeline.RawTimeSeriesInput
import com.training.trackplanner.analysis.lab.pipeline.RowPlanner
import com.training.trackplanner.analysis.lab.pipeline.ScalingPlanner
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationRequest
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationResult
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BvarPhaseBCoreTest {
    private val fixture = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_b_bvar_reference.json").readText())

    @Test
    fun posteriorCoreMatchesPythonReferenceOnPreparedCommonSample() {
        val prepared = preparedK2()
        val result = BvarPhaseBEstimator().estimatePosteriorCore(
            view = prepared.view,
            rowPlan = prepared.rowPlan,
            scalingPlan = prepared.scalingPlan,
            shockSourceMetric = X,
            orderedEndogenousMetrics = listOf(X, Y),
            pMax = 2,
            lambdaGrid = listOf(0.2, 0.1)
        )

        assertTrue(result is BvarPhaseBEstimationResult.Success)
        val phaseB = (result as BvarPhaseBEstimationResult.Success).result
        assertEquals(62, phaseB.designIdentity.commonRowCount)
        assertEquals(listOf(X, Y), phaseB.designIdentity.orderedMetrics)
        assertEquals(PHASE_B_COEFFICIENT_ORDERING_VERSION, phaseB.designIdentity.coefficientOrderingVersion)

        val expectedModel = fixture.getJSONArray("posterior_models").objects()
            .single { it.getInt("p") == 1 && nearly(it.getDouble("lambda"), 0.1) }
        val actualModel = phaseB.posteriorParameters.single { it.modelId.lag == 1 && nearly(it.modelId.lambda, 0.1) }
        assertMatrixClose(expectedModel.getJSONArray("bn").matrix(), actualModel.bN, 1e-8)
        assertMatrixClose(expectedModel.getJSONArray("vn").matrix(), actualModel.vN, 1e-8)
        assertMatrixClose(expectedModel.getJSONArray("sn").matrix(), actualModel.sN, 1e-8)
        assertClose(expectedModel.getDouble("nuN"), actualModel.nuN, 1e-10)
        assertClose(expectedModel.getDouble("log_marginal_likelihood"), actualModel.logMarginalLikelihood, 1e-8)

        val expectedProbabilities = fixture.getJSONArray("posterior_models").objects()
            .associate { "p=${it.getInt("p")}|lambda=${canonicalDecimal(it.getDouble("lambda"))}" to it.getDouble("posterior_probability") }
        phaseB.modelPosterior.joint.forEach { summary ->
            assertClose(expectedProbabilities.getValue(summary.modelId), summary.posteriorProbability, 1e-10)
        }
        assertClose(1.0, phaseB.modelPosterior.joint.sumOf { it.posteriorProbability }, 1e-10)
        assertClose(fixture.getJSONObject("marginal_lag").getDouble("1"), phaseB.modelPosterior.marginalLag.getValue(1), 1e-10)
        assertClose(fixture.getJSONObject("marginal_lambda").getDouble("0.1"), phaseB.modelPosterior.marginalLambda.getValue(0.1), 1e-10)
    }

    @Test
    fun priorUsesZeroOwnLagMeanAndDocumentedLagVariance() {
        val prepared = preparedK2()
        val grid = BvarModelGridSpec.createValidated(2, listOf(0.2))
        val prior = BvarPriorSpec.defaultFor(grid)
        val input = FutureBvarInput.createValidated(
            prepared.view,
            prepared.rowPlan,
            prepared.scalingPlan,
            prior.fingerprint,
            listOf(X, Y)
        )
        val sample = BvarPhaseBDesignMaterializer.materialize(
            input,
            BvarEndogenousSystem.createValidated(X, listOf(X, Y)),
            grid,
            prior
        )
        val parameters = BvarPosteriorAlgebra.fitCandidate(input, sample, k = 2, lag = 2, lambda = 0.2, prior = prior)

        assertEquals(0.0, parameters.b0[1][0], 0.0)
        assertEquals(100.0, parameters.v0[0][0], 0.0)
        assertClose(0.04, parameters.v0[1][1], 1e-12)
        assertClose(0.04 / 16.0, parameters.v0[3][3], 1e-12)
        assertMatrixClose(arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0)), parameters.s0, 0.0)
        assertEquals(4.0, parameters.nu0, 0.0)
    }

    @Test
    fun strictInputValidationRejectsDimensionLagGridAndControlLeakage() {
        val prepared = preparedK2()

        assertTrue(
            BvarPhaseBEstimator().estimatePosteriorCore(
                prepared.view,
                prepared.rowPlan,
                prepared.scalingPlan,
                X,
                listOf(X),
                pMax = 2,
                lambdaGrid = listOf(0.1)
            ) is BvarPhaseBEstimationResult.Failure
        )
        assertTrue(runCatching { BvarModelGridSpec.createValidated(0, listOf(0.1)) }.isFailure)
        assertTrue(runCatching { BvarModelGridSpec.createValidated(1, listOf(0.1, 0.10)) }.isFailure)
        assertTrue(runCatching { BvarModelGridSpec.createValidated(1, listOf(-0.1)) }.isFailure)

        val withControl = preparedK2WithControl()
        val controlResult = BvarPhaseBEstimator().estimatePosteriorCore(
            withControl.view,
            withControl.rowPlan,
            withControl.scalingPlan,
            X,
            listOf(X, Y, CONTROL),
            pMax = 2,
            lambdaGrid = listOf(0.1)
        )
        assertTrue(controlResult is BvarPhaseBEstimationResult.Failure)
        assertEquals(BvarPhaseBFailureCode.INVALID_ENDOGENOUS_ORDERING, (controlResult as BvarPhaseBEstimationResult.Failure).failure.code)
    }

    @Test
    fun declaredVariableOrderChangesIdentity() {
        val prepared = preparedK2()
        val grid = BvarModelGridSpec.createValidated(2, listOf(0.1))
        val prior = BvarPriorSpec.defaultFor(grid)
        val forward = FutureBvarInput.createValidated(prepared.view, prepared.rowPlan, prepared.scalingPlan, prior.fingerprint, listOf(X, Y))
        val reversed = FutureBvarInput.createValidated(prepared.view, prepared.rowPlan, prepared.scalingPlan, prior.fingerprint, listOf(Y, X))

        assertNotEquals(forward.fingerprint, reversed.fingerprint)
    }

    private fun preparedK2(): PreparedBundle {
        val raw = fixture.getJSONArray("raw_k2").matrix()
        val weeks = weeks(raw.size)
        val request = StrictPreparationRequest(X, listOf(Y), horizons = setOf(1))
        return preparedBundle(
            request,
            mapOf(
                X to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][0]) },
                Y to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][1]) }
            )
        )
    }

    private fun preparedK2WithControl(): PreparedBundle {
        val raw = fixture.getJSONArray("raw_k2").matrix()
        val weeks = weeks(raw.size)
        val request = StrictPreparationRequest(X, listOf(Y), controls = listOf(CONTROL), horizons = setOf(1))
        return preparedBundle(
            request,
            mapOf(
                X to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][0]) },
                Y to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][1]) },
                CONTROL to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][0] * 0.5 + raw[index][1] * 0.25) }
            )
        )
    }

    private fun preparedBundle(
        request: StrictPreparationRequest,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): PreparedBundle {
        val context = when (val result = PreparedAnalysisContext.createValidated(request, RawTimeSeriesInput.fromTrendSeries(series).ingest(request))) {
            is StrictPreparationResult.Success -> result.context
            is StrictPreparationResult.Failure -> error("strict preparation failed: ${result.code} ${result.diagnostics}")
        }
        val view = BvarPreparedView.from(context)
        val rowPlan = RowPlanner.planWithoutHorizon(context, view, 2)
        val scalingPlan = ScalingPlanner.plan(context, view, rowPlan, rowPlan.rows.map { it.sourceWeek })
        return PreparedBundle(view, rowPlan, scalingPlan)
    }

    private data class PreparedBundle(
        val view: BvarPreparedView,
        val rowPlan: com.training.trackplanner.analysis.lab.pipeline.PreparedRowPlan,
        val scalingPlan: com.training.trackplanner.analysis.lab.pipeline.PreparedScalingPlan
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.matrix(): Array<DoubleArray> = Array(length()) { row ->
        val values = getJSONArray(row)
        DoubleArray(values.length()) { column -> values.getDouble(column) }
    }

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }

    private fun assertMatrixClose(expected: Array<DoubleArray>, actual: Array<DoubleArray>, tolerance: Double) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { row ->
            assertEquals(expected[row].size, actual[row].size)
            expected[row].indices.forEach { column ->
                assertClose(expected[row][column], actual[row][column], tolerance)
            }
        }
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double) {
        assertTrue("expected=$expected actual=$actual tolerance=$tolerance", abs(expected - actual) <= tolerance)
    }

    private fun nearly(left: Double, right: Double): Boolean = abs(left - right) <= 1e-12

    private fun existingFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(cwd) { it.parentFile }.map { File(it, path) }.first(File::exists)
    }

    private companion object {
        val X: TrendMetricId = TrendMetricId.BADMINTON_TRAINING
        val Y: TrendMetricId = TrendMetricId.FATIGUE_COMPOSITE
        val CONTROL: TrendMetricId = TrendMetricId.SLEEP_HOURS
    }
}
