package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import com.training.trackplanner.analysis.lab.pipeline.BvarPreparedView
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

class BvarPosteriorSamplerTest {
    private val fixture = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_b_bvar_reference.json").readText())

    @Test
    fun largestRemainderAllocationPreservesModelWeights() {
        val result = estimate(draws = 24)
        val allocation = result.posteriorDraws.groupBy { it.modelId }.mapValues { it.value.size }

        assertEquals(result.modelPosterior.joint.map { it.modelId }.toSet(), allocation.keys)
        result.modelPosterior.joint.forEach { summary ->
            val drawWeight = result.posteriorDraws.filter { it.modelId == summary.modelId }.sumOf { it.weight }
            assertClose(summary.posteriorProbability, drawWeight, 1e-12)
            assertTrue(allocation.getValue(summary.modelId) >= 1)
        }
        assertClose(1.0, result.posteriorDraws.sumOf { it.weight }, 1e-12)
    }

    @Test
    fun directDrawsAreDeterministicAndGridOrderInvariant() {
        val first = estimate(draws = 20, lambdas = listOf(0.2, 0.1))
        val second = estimate(draws = 20, lambdas = listOf(0.1, 0.2))

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.posteriorDraws.map { it.drawId }, second.posteriorDraws.map { it.drawId })
        assertEquals(first.posteriorDraws.map { it.covarianceFingerprint }, second.posteriorDraws.map { it.covarianceFingerprint })
        assertEquals(first.posteriorDraws.map { it.coefficientFingerprint }, second.posteriorDraws.map { it.coefficientFingerprint })
        assertTrue(first.posteriorDraws.isNotEmpty())
    }

    @Test
    fun covarianceAndCoefficientDrawsAreFiniteSpdAndCorrectlySized() {
        val result = estimate(draws = 12)

        result.posteriorDraws.forEach { draw ->
            assertEquals(2, draw.covariance.size)
            assertEquals(2, draw.covariance[0].size)
            assertTrue(StableLinearAlgebra.isStrictlyPositiveDefinite(draw.covariance))
            assertTrue(draw.covariance.all { row -> row.all(Double::isFinite) })
            val q = 1 + 2 * draw.lag
            assertEquals(q, draw.coefficients.size)
            assertEquals(2, draw.coefficients[0].size)
            assertTrue(draw.coefficients.all { row -> row.all(Double::isFinite) })
        }
    }

    @Test
    fun rngAndSeedDerivationHavePublishedStableVectors() {
        val prepared = preparedK2()
        val system = BvarEndogenousSystem.createValidated(X, listOf(X, Y))
        val grid = BvarModelGridSpec.createValidated(2, listOf(0.1))
        val prior = BvarPriorSpec.defaultFor(grid)
        val input = com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput.createValidated(
            prepared.view,
            prepared.rowPlan,
            prepared.scalingPlan,
            prior.fingerprint,
            system.orderedMetrics
        )
        val sample = BvarPhaseBDesignMaterializer.materialize(input, system, grid, prior)
        val parameter = BvarPosteriorAlgebra.fitCandidate(input, sample, 2, 1, 0.1, prior)
        val seed = seedFor(input, parameter.modelId.id, drawOrdinal = 0, attempt = 0, componentTag = "matrix-normal")
        val rng = PhaseBDeterministicRandom(seed)

        assertEquals(-7840024802634231347L, seed)
        assertEquals(-8572662023369843021L, rng.nextLong())
        assertClose(-0.5007157454255773, rng.normal(), 1e-15)
        assertClose(0.6802257972009362, rng.normal(), 1e-15)
    }

    @Test
    fun productionDrawSpecRejectsTestOnlySmallCounts() {
        assertTrue(runCatching { BvarDrawSpec.production(24) }.isFailure)
        assertEquals(24, BvarDrawSpec.testOnly(24).acceptedDrawCount)
    }

    @Test
    fun changingPreparedInputChangesDrawIdentity() {
        val baseline = estimate(draws = 12)
        val changed = estimate(draws = 12, xShift = 0.01)

        assertNotEquals(baseline.inputFingerprint, changed.inputFingerprint)
        assertNotEquals(baseline.posteriorDraws.map { it.drawId }, changed.posteriorDraws.map { it.drawId })
    }

    private fun estimate(
        draws: Int,
        lambdas: List<Double> = listOf(0.1, 0.2),
        xShift: Double = 0.0
    ): BvarPhaseBResult {
        val prepared = preparedK2(xShift)
        val result = BvarPhaseBEstimator().estimatePosteriorMixture(
            view = prepared.view,
            rowPlan = prepared.rowPlan,
            scalingPlan = prepared.scalingPlan,
            shockSourceMetric = X,
            orderedEndogenousMetrics = listOf(X, Y),
            pMax = 2,
            lambdaGrid = lambdas,
            drawSpec = BvarDrawSpec.testOnly(draws)
        )
        assertTrue(result is BvarPhaseBEstimationResult.Success)
        return (result as BvarPhaseBEstimationResult.Success).result
    }

    private fun preparedK2(xShift: Double = 0.0): PreparedBundle {
        val raw = fixture.getJSONArray("raw_k2").matrix()
        val weeks = weeks(raw.size)
        val request = StrictPreparationRequest(X, listOf(Y), horizons = setOf(1))
        val series = mapOf(
            X to weeks.mapIndexed { index, week -> TrendDataPoint(week, raw[index][0] + xShift) },
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
