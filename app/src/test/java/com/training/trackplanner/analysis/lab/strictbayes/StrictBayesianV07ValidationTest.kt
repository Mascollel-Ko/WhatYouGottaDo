package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.BvarDesignMatrixMaterializer
import com.training.trackplanner.analysis.lab.pipeline.BvarPreparedView
import com.training.trackplanner.analysis.lab.pipeline.CandidateSourceGrouping
import com.training.trackplanner.analysis.lab.pipeline.CanonicalSeriesTransformation
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarComparisonInput
import com.training.trackplanner.analysis.lab.pipeline.PreparedAnalysisContext
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarComparisonDesign
import com.training.trackplanner.analysis.lab.pipeline.PriorActiveSourcePolicy
import com.training.trackplanner.analysis.lab.pipeline.RawTimeSeriesInput
import com.training.trackplanner.analysis.lab.pipeline.RowPlanner
import com.training.trackplanner.analysis.lab.pipeline.ScalingPlanner
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationPolicy
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationRequest
import com.training.trackplanner.analysis.lab.pipeline.StrictPreparationResult
import com.training.trackplanner.analysis.lab.pipeline.StrictSeriesKey
import com.training.trackplanner.analysis.lab.pipeline.StrictTimeSeriesPreparationPipeline
import com.training.trackplanner.analysis.lab.pipeline.TauZeroCalibration
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBayesianV07ValidationTest {
    @Test
    fun `global sparsity calibration is exact and responds monotonically to p0 misspecification`() {
        val targets = listOf(0.5, 1.5, 3.0)
        val calibrated = targets.map { target ->
            TauZeroCalibration.calibrate(sourceCount = 8, comparisonRowCount = 48, lag = 3, priorActiveSourceTarget = target)
        }

        assertTrue(calibrated.zipWithNext().all { (lower, upper) -> lower < upper })
        calibrated.zip(targets).forEach { (tau, target) ->
            assertEquals(target, TauZeroCalibration.effectiveOpenSources(tau, 8, 48, 3), 1e-9)
        }
    }

    @Test
    fun `kernel transition is invariant for the same prepared identity state and seed`() {
        val design = syntheticDesign(signal = true)
        val kernel = StrictBayesianV07Kernel(design)
        val initial = kernel.initialState(initialLag = 2)

        val first = kernel.step(initial, StrictRandom(91023L))
        val repeated = kernel.step(initial, StrictRandom(91023L))
        val different = kernel.step(initial, StrictRandom(91024L))

        assertEquals(first.state, repeated.state)
        assertEquals(first.draw.lag, repeated.draw.lag)
        assertEquals(first.draw.omegaByLag, repeated.draw.omegaByLag)
        assertArrayEquals(first.draw.sigmaDiagonal, repeated.draw.sigmaDiagonal, 0.0)
        first.draw.responseByFeature.forEach { (feature, values) ->
            assertArrayEquals(values, repeated.draw.responseByFeature.getValue(feature), 0.0)
        }
        assertNotEquals(first.state, different.state)
    }

    @Test
    fun `functional diagnostic gate accepts mixed stationary chains and rejects shifted chains`() {
        val stationary = List(4) { chain ->
            DoubleArray(240) { index -> sin((index + chain * 17) * 0.19) + cos((index + chain * 7) * 0.07) }
        }
        val shifted = List(4) { chain ->
            DoubleArray(240) { index -> sin(index * 0.19) + chain * 4.0 }
        }

        assertTrue(StrictChainDiagnostics.statistics(stationary).rhat < 1.05)
        assertTrue(StrictChainDiagnostics.statistics(shifted).rhat > 1.10)
    }

    @Test
    fun `app and validation policies share posterior trajectory settings and differ only in reliability strictness`() {
        val app = StrictSamplingPolicy.appRuntime()
        val validation = StrictSamplingPolicy.validation()

        assertEquals(app.chains, validation.chains)
        assertEquals(app.stabilizationMinimum, validation.stabilizationMinimum)
        assertEquals(app.diagnosticWindow, validation.diagnosticWindow)
        assertEquals(app.blockSize, validation.blockSize)
        assertEquals(app.stabilizationCap, validation.stabilizationCap)
        assertEquals(app.productionMinimum, validation.productionMinimum)
        assertEquals(app.productionMaximum, validation.productionMaximum)
        assertEquals(app.precisionExtensionMaximum, validation.precisionExtensionMaximum)
        assertEquals(app.maximumRhat, validation.maximumRhat, 0.0)
        assertTrue(validation.minimumEss > app.minimumEss)
        assertTrue(validation.maximumMcseToSd < app.maximumMcseToSd)
        assertNotEquals(app.fingerprint, validation.fingerprint)
    }

    @Test
    fun `lag two signal outranks lag one under common rows and common scaling`() {
        val design = syntheticDesign(signal = true)
        val kernel = StrictBayesianV07Kernel(design)
        val state = kernel.initialState()
        val logWeights = design.designsByLag.mapValues { (lag, lagDesign) ->
            kernel.collapsedPosterior(lag, lagDesign, state).logWeight
        }
        val maximum = logWeights.values.max()
        val weights = logWeights.mapValues { (_, value) -> exp(value - maximum) }
        val total = weights.values.sum()
        val probabilities = weights.mapValues { (_, value) -> value / total }

        assertTrue(probabilities.getValue(2) > probabilities.getValue(1))
    }

    @Test
    fun `single signal with a collinear peer remains stronger than an unrelated source`() {
        val design = syntheticDesign(signal = true)
        val outcome = StrictBayesianV07Sampler(
            design,
            StrictSamplingPolicy.testing(stabilization = 50, production = 100)
        ).sample()

        assertTrue(outcome is StrictBayesianV07Outcome.Success)
        val summaries = (outcome as StrictBayesianV07Outcome.Success).result.sourceSummaries
        val signal = summaries.getValue(SIGNAL_SOURCE).contribution.median
        val correlated = summaries.getValue(CORRELATED_SOURCE).contribution.median
        val unrelated = summaries.getValue(NULL_SOURCE).contribution.median
        assertTrue(maxOf(signal, correlated) > unrelated)
        assertTrue((signal + correlated) / 2.0 > unrelated)
    }

    @Test
    fun `complete null simulation keeps all reported tails finite and bounded`() {
        val design = syntheticDesign(signal = false)
        val outcome = StrictBayesianV07Sampler(
            design,
            StrictSamplingPolicy.testing(stabilization = 50, production = 100)
        ).sample()

        assertTrue(outcome is StrictBayesianV07Outcome.Success)
        val summaries = (outcome as StrictBayesianV07Outcome.Success).result.sourceSummaries.values
        assertTrue(summaries.all { it.contribution.upper80.isFinite() && it.contribution.upper80 < 10.0 })
        assertTrue(summaries.all { it.openness.upper80 in 0.0..1.0 })
    }

    @Test
    fun `regularized horseshoe reference caps extreme local variance without changing ordinary finite regime`() {
        val tauSquared = 0.04
        val slabSquared = 4.0
        val localSquared = listOf(0.01, 1.0, 1e12)
        val ordinary = localSquared.map { tauSquared * it }
        val regularized = localSquared.map { lambdaSquared ->
            tauSquared * (slabSquared * lambdaSquared / (slabSquared + tauSquared * lambdaSquared))
        }

        assertTrue(regularized.zip(ordinary).all { (bounded, plain) -> bounded <= plain })
        assertEquals(ordinary.first(), regularized.first(), 1e-6)
        assertTrue(regularized.last().isFinite() && regularized.last() <= slabSquared)
        assertTrue(ordinary.last() > regularized.last())
    }

    private fun syntheticDesign(signal: Boolean): PreparedBvarComparisonDesign {
        val weeks = (0 until 28).map { START.plusWeeks(it.toLong()) }
        val xValues = DoubleArray(weeks.size) { index ->
            sin(index * 0.37) + 0.55 * cos(index * 0.11) + 0.08 * sin(index * 1.31)
        }
        val correlatedValues = DoubleArray(weeks.size) { index ->
            xValues[index] * 0.98 + 0.02 * cos(index * 0.83)
        }
        val nullValues = DoubleArray(weeks.size) { index ->
            sin(index * 0.73) - 0.4 * cos(index * 0.29)
        }
        val responseValues = DoubleArray(weeks.size)
        for (index in responseValues.indices) {
            val independent = sin(index * 0.53) + 0.2 * cos(index * 1.17)
            responseValues[index] = if (signal && index >= 2) {
                0.85 * xValues[index - 2] + 0.12 * responseValues[index - 1] + independent * 0.05
            } else {
                independent
            }
        }
        val request = StrictPreparationRequest(
            X,
            listOf(Y),
            optionalCandidates = listOf(CORRELATED, NULL),
            horizons = setOf(1, 2, 3)
        )
        val raw = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                X to weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues[index]) },
                Y to weeks.mapIndexed { index, week -> TrendDataPoint(week, responseValues[index]) },
                CORRELATED to weeks.mapIndexed { index, week -> TrendDataPoint(week, correlatedValues[index]) },
                NULL to weeks.mapIndexed { index, week -> TrendDataPoint(week, nullValues[index]) }
            )
        )
        val policy = StrictPreparationPolicy.createValidated(
            shortHistoryTransformations = request.allMetrics.associateWith { CanonicalSeriesTransformation.LEVEL }
        )
        val context = (StrictTimeSeriesPreparationPipeline.prepare(raw, request, policy) as StrictPreparationResult.Success).context
        return materialize(context, request)
    }

    private fun materialize(
        context: PreparedAnalysisContext,
        request: StrictPreparationRequest
    ): PreparedBvarComparisonDesign {
        val sources: Map<StrictSeriesKey, AnalysisSourceKey> = mapOf(
            X to SIGNAL_SOURCE,
            CORRELATED to CORRELATED_SOURCE,
            NULL to NULL_SOURCE
        )
        val view = BvarPreparedView.fromV07(context, listOf(CORRELATED, NULL), sources)
        val rows = RowPlanner.planLagComparison(context, view, requestedPmax = 3)
        val scaling = ScalingPlanner.planForComparison(context, view, rows)
        val grouping = CandidateSourceGrouping.createValidated(view, groupingVersion = "validation-fixture-v1")
        val input = FutureBvarComparisonInput.createValidated(
            view,
            rows,
            scaling,
            grouping,
            PriorActiveSourcePolicy.fractional()
        )
        return BvarDesignMatrixMaterializer.materialize(context, input)
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2024, 1, 1)
        val X = TrendMetricId.BADMINTON_PRACTICE_LOAD
        val Y = TrendMetricId.FATIGUE_COMPOSITE
        val CORRELATED = TrendMetricId.STRENGTH_VOLUME
        val NULL = TrendMetricId.STRENGTH_INTENSITY
        val SIGNAL_SOURCE = AnalysisSourceKey.parse("source:signal")
        val CORRELATED_SOURCE = AnalysisSourceKey.parse("source:correlated")
        val NULL_SOURCE = AnalysisSourceKey.parse("source:null")
    }
}
