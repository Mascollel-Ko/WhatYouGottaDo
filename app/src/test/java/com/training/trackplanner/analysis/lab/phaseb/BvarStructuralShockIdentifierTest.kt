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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BvarStructuralShockIdentifierTest {
    private val fixture = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_b_bvar_reference.json").readText())

    @Test
    fun fixedDrawResidualsAndStructuralShocksMatchPythonReference() {
        val prepared = preparedK2()
        val system = BvarEndogenousSystem.createValidated(X, listOf(X, Y))
        val grid = BvarModelGridSpec.createValidated(2, listOf(0.1))
        val prior = BvarPriorSpec.defaultFor(grid)
        val input = FutureBvarInput.createValidated(prepared.view, prepared.rowPlan, prepared.scalingPlan, prior.fingerprint, system.orderedMetrics)
        val sample = BvarPhaseBDesignMaterializer.materialize(input, system, grid, prior)
        val fixed = fixture.getJSONObject("fixed_structural_shock")
        val draw = BvarPosteriorDraw(
            drawId = "fixed",
            modelId = "fixed",
            lag = 1,
            lambda = 0.1,
            coefficients = fixed.getJSONArray("b").matrix(),
            covariance = fixed.getJSONArray("sigma").matrix(),
            weight = 1.0,
            covarianceFingerprint = matrixFingerprint(fixed.getJSONArray("sigma").matrix()),
            coefficientFingerprint = matrixFingerprint(fixed.getJSONArray("b").matrix()),
            diagnostics = emptyList()
        )

        assertMatrixClose(fixed.getJSONArray("residuals").matrix(), BvarStructuralShockIdentifier.reducedFormResiduals(sample, draw), 1e-8)
        val sourceShock = BvarStructuralShockIdentifier.sourceShockSeries(sample, draw, 0, input, system)
        val expectedShock = fixed.getJSONArray("shocks").matrix().map { it[0] }
        expectedShock.indices.forEach { index -> assertClose(expectedShock[index], sourceShock[index], 1e-8) }
    }

    @Test
    fun finalEstimateBuildsStrictIdentifiedShockPosterior() {
        val prepared = preparedK2()
        val result = BvarPhaseBEstimator().estimate(
            view = prepared.view,
            rowPlan = prepared.rowPlan,
            scalingPlan = prepared.scalingPlan,
            shockSourceMetric = X,
            orderedEndogenousMetrics = listOf(X, Y),
            pMax = 2,
            lambdaGrid = listOf(0.1, 0.2),
            drawSpec = BvarDrawSpec.testOnly(24)
        )

        assertTrue(result is BvarPhaseBEstimationResult.Success)
        val phaseB = (result as BvarPhaseBEstimationResult.Success).result
        assertNotNull(phaseB.identifiedShockPosterior)
        val shocks = phaseB.identifiedShockPosterior!!
        assertEquals(X, shocks.sourceMetric)
        assertEquals(listOf(X, Y), shocks.orderedEndogenousMetrics)
        assertEquals(prepared.rowPlan.rows.map { it.sourceWeek }, shocks.eligibleSourceWeeks)
        assertEquals(phaseB.posteriorMixtureFingerprint, shocks.sourceIdentity.sourceBvarPosteriorFingerprint)
        assertEquals(phaseB.posteriorDraws.map { it.drawId }.sorted(), shocks.posteriorDrawIds)
        assertClose(1.0, shocks.drawWeights.values.sum(), 1e-12)
        shocks.posteriorDrawIds.forEach { drawId ->
            assertEquals(prepared.rowPlan.rows.size, shocks.shockSeriesByDraw.getValue(drawId).size)
            assertTrue(shocks.shockSeriesByDraw.getValue(drawId).all(Double::isFinite))
        }
    }

    @Test
    fun changedCholeskyOrderChangesShockPosteriorIdentityAndShockValues() {
        val prepared = preparedK2()
        val forward = (BvarPhaseBEstimator().estimate(
            prepared.view,
            prepared.rowPlan,
            prepared.scalingPlan,
            X,
            listOf(X, Y),
            pMax = 2,
            lambdaGrid = listOf(0.1),
            drawSpec = BvarDrawSpec.testOnly(12)
        ) as BvarPhaseBEstimationResult.Success).result
        val reversed = (BvarPhaseBEstimator().estimate(
            prepared.view,
            prepared.rowPlan,
            prepared.scalingPlan,
            X,
            listOf(Y, X),
            pMax = 2,
            lambdaGrid = listOf(0.1),
            drawSpec = BvarDrawSpec.testOnly(12)
        ) as BvarPhaseBEstimationResult.Success).result

        assertNotEquals(forward.identifiedShockPosterior!!.fingerprint, reversed.identifiedShockPosterior!!.fingerprint)
        assertNotEquals(
            forward.identifiedShockPosterior.shockSeriesByDraw.values.first(),
            reversed.identifiedShockPosterior.shockSeriesByDraw.values.first()
        )
    }

    private fun preparedK2(): PreparedBundle {
        val raw = fixture.getJSONArray("raw_k2").matrix()
        val weeks = weeks(raw.size)
        val request = StrictPreparationRequest(X, listOf(Y), horizons = setOf(1))
        val series = mapOf(
            X to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][0]) },
            Y to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][1]) }
        )
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
            expected[row].indices.forEach { column -> assertClose(expected[row][column], actual[row][column], tolerance) }
        }
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double) {
        assertTrue("expected=$expected actual=$actual tolerance=$tolerance", abs(expected - actual) <= tolerance)
    }

    private fun existingFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(cwd) { it.parentFile }.map { File(it, path) }.first(File::exists)
    }

    private companion object {
        val X: TrendMetricId = TrendMetricId.BADMINTON_TRAINING
        val Y: TrendMetricId = TrendMetricId.FATIGUE_COMPOSITE
    }
}
