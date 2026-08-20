package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBvarV07BoundaryTest {
    @Test
    fun `multi lag boundary preserves one root common rows scaling candidates and tau calibration`() {
        val fixture = fixture()
        val input = fixture.input

        assertEquals(fixture.context.fingerprint, input.view.rootContextFingerprint)
        assertEquals(input.feasibleLags, input.comparisonPlan.plansByLag.keys)
        assertTrue(input.comparisonPlan.plansByLag.values.all { plan ->
            plan.rows.map(PreparedRowIdentity::sourceWeek) == input.comparisonPlan.commonSourceWeeks
        })
        assertEquals(input.comparisonPlan.commonSourceWeeks, input.scalingPlan.baseScalingPlan.trainingRows)
        assertEquals(input.view.candidateMetrics.toSet(), input.sourceGrouping.sourceByFeature.keys)
        input.tauZeroByLag.forEach { (lag, tauZero) ->
            assertEquals(
                input.priorActiveSourceTarget,
                TauZeroCalibration.effectiveOpenSources(
                    tauZero,
                    input.sourceGrouping.sourceCount,
                    input.comparisonPlan.commonSourceWeeks.size,
                    lag
                ),
                1e-9
            )
        }
    }

    @Test
    fun `source grouping version changes the validated future boundary identity`() {
        val fixture = fixture()
        val alternateGrouping = CandidateSourceGrouping.createValidated(
            fixture.input.view,
            groupingVersion = "test-source-grouping-v2"
        )
        val alternate = FutureBvarComparisonInput.createValidated(
            fixture.input.view,
            fixture.input.comparisonPlan,
            fixture.input.scalingPlan,
            alternateGrouping,
            fixture.input.priorActiveSourcePolicy
        )

        assertNotEquals(fixture.input.sourceGrouping.fingerprint, alternateGrouping.fingerprint)
        assertNotEquals(fixture.input.fingerprint, alternate.fingerprint)
    }

    @Test
    fun `materialized lag designs share identical Y and give every candidate equal lag support`() {
        val fixture = fixture()
        val design = BvarDesignMatrixMaterializer.materialize(fixture.context, fixture.input)
        val referenceY = design.designsByLag.getValue(1).y

        assertTrue(design.designsByLag.values.all { it.y.contentDeepEquals(referenceY) })
        design.designsByLag.forEach { (lag, lagDesign) ->
            val candidateGroups = lagDesign.columns
                .filter { it.role == StrictBvarDesignRole.CANDIDATE_SOURCE }
                .groupBy { it.source }
            assertTrue(candidateGroups.values.all { columns ->
                columns.map { it.lag }.distinct().sorted() == (1..lag).toList()
            })
        }
    }

    @Test
    fun `future comparison boundary rejects mixed context row scaling and source identities`() {
        val first = fixture()
        val second = fixture(phaseShift = 0.37)

        assertThrows(IllegalArgumentException::class.java) {
            FutureBvarComparisonInput.createValidated(
                first.input.view,
                second.input.comparisonPlan,
                first.input.scalingPlan,
                first.input.sourceGrouping,
                first.input.priorActiveSourcePolicy
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FutureBvarComparisonInput.createValidated(
                first.input.view,
                first.input.comparisonPlan,
                second.input.scalingPlan,
                first.input.sourceGrouping,
                first.input.priorActiveSourcePolicy
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FutureBvarComparisonInput.createValidated(
                first.input.view,
                first.input.comparisonPlan,
                first.input.scalingPlan,
                second.input.sourceGrouping,
                first.input.priorActiveSourcePolicy
            )
        }
    }

    @Test
    fun `materialized design rejects lag matrices from another prepared input`() {
        val first = fixture()
        val second = fixture(phaseShift = 0.51)
        val firstDesign = BvarDesignMatrixMaterializer.materialize(first.context, first.input)
        val secondDesign = BvarDesignMatrixMaterializer.materialize(second.context, second.input)

        assertThrows(IllegalArgumentException::class.java) {
            PreparedBvarComparisonDesign.createValidated(
                input = firstDesign.input,
                responseFeatures = firstDesign.responseFeatures,
                focalFeature = firstDesign.focalFeature,
                focalSource = firstDesign.focalSource,
                responseScalePlans = firstDesign.responseScalePlans,
                responseScalingStatistics = firstDesign.responseScalingStatistics,
                maximumResponseHorizon = firstDesign.maximumResponseHorizon,
                designsByLag = secondDesign.designsByLag
            )
        }
    }

    private fun fixture(phaseShift: Double = 0.0): Fixture {
        val x = TrendMetricId.BADMINTON_PRACTICE_LOAD
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val optional = TrendMetricId.STRENGTH_VOLUME
        val weeks = (0 until 20).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }
        val request = StrictPreparationRequest(x, listOf(y), optionalCandidates = listOf(optional))
        val input = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, sin(index * 0.7 + phaseShift) + index % 3) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, cos(index * 0.4 + phaseShift) + (index % 2) * 0.2) },
                optional to weeks.mapIndexed { index, week ->
                    TrendDataPoint(week, sin(index * 0.31 + phaseShift) + cos(index * 0.17))
                }
            )
        )
        val policy = StrictPreparationPolicy.createValidated(
            shortHistoryTransformations = request.allMetrics.associateWith { CanonicalSeriesTransformation.LEVEL }
        )
        val context = (StrictTimeSeriesPreparationPipeline.prepare(input, request, policy) as StrictPreparationResult.Success).context
        val sourceByFeature = request.allMetrics.associateWith { AnalysisSourceKey.parse("feature:${it.stableId}") }
        val view = BvarPreparedView.fromV07(context, listOf(optional), sourceByFeature)
        val comparison = RowPlanner.planLagComparison(context, view, requestedPmax = 4)
        val scaling = ScalingPlanner.planForComparison(context, view, comparison)
        val grouping = CandidateSourceGrouping.createValidated(view, groupingVersion = "fixture-source-grouping-v1")
        val future = FutureBvarComparisonInput.createValidated(
            view,
            comparison,
            scaling,
            grouping,
            PriorActiveSourcePolicy.fractional()
        )
        return Fixture(context, future)
    }

    private data class Fixture(
        val context: PreparedAnalysisContext,
        val input: FutureBvarComparisonInput
    )
}
