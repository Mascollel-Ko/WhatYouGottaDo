package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.BvarDesignMatrixMaterializer
import com.training.trackplanner.analysis.lab.pipeline.BvarPreparedView
import com.training.trackplanner.analysis.lab.pipeline.CandidateSourceGrouping
import com.training.trackplanner.analysis.lab.pipeline.CanonicalSeriesTransformation
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarComparisonInput
import com.training.trackplanner.analysis.lab.pipeline.InverseTransformationRule
import com.training.trackplanner.analysis.lab.pipeline.PreparedAnalysisContext
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarComparisonDesign
import com.training.trackplanner.analysis.lab.pipeline.PriorActiveSourcePolicy
import com.training.trackplanner.analysis.lab.pipeline.RawTimeSeriesInput
import com.training.trackplanner.analysis.lab.pipeline.RowPlanner
import com.training.trackplanner.analysis.lab.pipeline.ScalingPlanner
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationPolicy
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationRequest
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationResult
import com.training.trackplanner.analysis.lab.pipeline.StrictTimeSeriesPreparationPipeline
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBayesianV07KernelTest {
    @Test
    fun `observation space collapsed weight matches coefficient space reference`() {
        val design = fixtureDesign()
        val kernel = StrictBayesianV07Kernel(design)
        val state = kernel.initialState()
        val lagDesign = design.designsByLag.getValue(2)
        val observed = kernel.collapsedPosterior(2, lagDesign, state)
        val x = Array2DRowRealMatrix(lagDesign.x)
        val y = Array2DRowRealMatrix(lagDesign.y)
        val d = MatrixUtils.createRealDiagonalMatrix(observed.diagonalPrior)
        val precision = MatrixUtils.inverse(d).add(x.transpose().multiply(x))
        val vn = CholeskyDecomposition(precision).solver.inverse
        val xty = x.transpose().multiply(y)
        val sn = MatrixUtils.createRealIdentityMatrix(y.columnDimension)
            .add(y.transpose().multiply(y))
            .subtract(xty.transpose().multiply(vn).multiply(xty))
        val logDetA = observed.diagonalPrior.sumOf(::ln) + ln(CholeskyDecomposition(precision).determinant)
        val nu0 = y.columnDimension + 2
        val reference = -ln(design.designsByLag.size.toDouble()) -
            0.5 * y.columnDimension * logDetA -
            0.5 * (nu0 + y.rowDimension) * ln(CholeskyDecomposition(sn).determinant)

        assertEquals(reference, observed.logWeight, 1e-7)
        assertMatrixClose(sn.data, observed.sn, 1e-7)
    }

    @Test
    fun `kernel draws a fresh finite selected lag state and normalized conditional weights`() {
        val design = fixtureDesign()
        val kernel = StrictBayesianV07Kernel(design)
        val step = kernel.step(kernel.initialState(), StrictRandom(31L))

        assertTrue(step.state.lag in design.designsByLag)
        assertEquals(1.0, step.draw.omegaByLag.values.sum(), 1e-12)
        assertTrue(step.draw.sigmaDiagonal.all { it.isFinite() && it > 0.0 })
        assertTrue(step.draw.contributionBySource.values.all { it.isFinite() && it >= 0.0 })
        assertTrue(step.draw.opennessBySource.values.all { it in 0.0..1.0 })
        assertEquals(design.maximumResponseHorizon, step.draw.responseByFeature.getValue(TrendMetricId.FATIGUE_COMPOSITE).size)
        assertTrue(step.draw.responseByFeature.values.flatMap { it.asList() }.all(Double::isFinite))
    }

    @Test
    fun `short deterministic sampler reports Rao Blackwellized lag probabilities separately from visits`() {
        val design = fixtureDesign()
        val outcome = StrictBayesianV07Sampler(
            design,
            StrictSamplingPolicy.testing(stabilization = 40, production = 60)
        ).sample()

        assertTrue(outcome is StrictBayesianV07Outcome.Success)
        val result = (outcome as StrictBayesianV07Outcome.Success).result
        assertEquals(1.0, result.officialLagProbability.values.sum(), 1e-9)
        assertEquals(1.0, result.lagVisitationFrequency.values.sum(), 1e-9)
        assertTrue(result.officialLagProbability != result.lagVisitationFrequency)
        assertEquals(design.maximumResponseHorizon, result.responses.getValue(TrendMetricId.FATIGUE_COMPOSITE).size)
    }

    @Test
    fun `user response inverse transformations are applied draw by draw`() {
        val raw = doubleArrayOf(0.1, -0.2, 0.3)

        assertTrue(
            StrictResponseTransformation.apply(raw, InverseTransformationRule.IDENTITY)
                .contentEquals(raw)
        )
        assertArrayEquals(
            doubleArrayOf(0.1, -0.1, 0.2),
            StrictResponseTransformation.apply(raw, InverseTransformationRule.CUMULATIVE_SUM),
            1e-12
        )
        val exponential = StrictResponseTransformation.apply(raw, InverseTransformationRule.EXPONENTIAL)
        assertEquals(kotlin.math.exp(0.1) - 1.0, exponential[0], 1e-12)
        val cumulativeExponential = StrictResponseTransformation.apply(raw, InverseTransformationRule.CUMULATIVE_EXPONENTIAL)
        assertEquals((kotlin.math.exp(0.2) - 1.0) * 100.0, cumulativeExponential[2], 1e-12)
    }

    private fun fixtureDesign(): PreparedBvarComparisonDesign {
        val x = TrendMetricId.BADMINTON_PRACTICE_LOAD
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val optional = TrendMetricId.STRENGTH_VOLUME
        val weeks = (0 until 20).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }
        val request = StrictPreparationRequest(x, listOf(y), optionalCandidates = listOf(optional))
        val raw = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, sin(index * 0.71) + (index % 3) * 0.1) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, cos(index * 0.37) + sin(index * 0.11)) },
                optional to weeks.mapIndexed { index, week -> TrendDataPoint(week, sin(index * 0.23) - cos(index * 0.19)) }
            )
        )
        val policy = StrictPreparationPolicy.createValidated(
            shortHistoryTransformations = request.allMetrics.associateWith { CanonicalSeriesTransformation.LEVEL }
        )
        val context = (StrictTimeSeriesPreparationPipeline.prepare(raw, request, policy) as StrictPreparationResult.Success).context
        return materialize(context, request, optional)
    }

    private fun materialize(
        context: PreparedAnalysisContext,
        request: StrictPreparationRequest,
        optional: TrendMetricId
    ): PreparedBvarComparisonDesign {
        val sources = request.allMetrics.associateWith { AnalysisSourceKey.parse("feature:${it.stableId}") }
        val view = BvarPreparedView.fromV07(context, listOf(optional), sources)
        val rows = RowPlanner.planLagComparison(context, view, requestedPmax = 2)
        val scaling = ScalingPlanner.planForComparison(context, view, rows)
        val grouping = CandidateSourceGrouping.createValidated(view, groupingVersion = "kernel-fixture-v1")
        val input = FutureBvarComparisonInput.createValidated(
            view,
            rows,
            scaling,
            grouping,
            PriorActiveSourcePolicy.fractional()
        )
        return BvarDesignMatrixMaterializer.materialize(context, input)
    }

    private fun assertMatrixClose(expected: Array<DoubleArray>, actual: Array<DoubleArray>, tolerance: Double) {
        expected.indices.forEach { row ->
            expected[row].indices.forEach { column ->
                assertEquals(expected[row][column], actual[row][column], tolerance)
            }
        }
    }
}
