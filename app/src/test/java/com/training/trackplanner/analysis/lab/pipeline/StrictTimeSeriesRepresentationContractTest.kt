package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTimeSeriesRepresentationContractTest {
    @Test
    fun diagnosticsPreserveSegmentsAndNeverCompressAcrossGapsOrLifecycleBoundaries() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(26)
        val lifecycle = StrictMetricLifecycle.createValidated(
            notApplicableRanges = listOf(StrictWeekRange(weeks[8], weeks[8])),
            versionDiscontinuityRanges = listOf(StrictWeekRange(weeks[17], weeks[17]))
        )
        val input = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to points(weeks) { index -> if (index % 2 == 0) 1.0 else -1.0 },
                y to points(weeks) { index -> if (index % 2 == 0) 2.0 else -2.0 }
            ),
            mapOf(x to lifecycle)
        )
        val catalog = input.ingest(request(x, y))

        val assessment = SegmentAwareIntegrationAssessmentAuthority.assess(catalog).getValue(x)
        val segments = assessment.segmentDiagnostics.map { it.segment }

        assertEquals(listOf(8, 8, 8), segments.map { it.length })
        assertTrue(segments.none { weeks[8] in it.weeks || weeks[17] in it.weeks })
        assertEquals(weeks[0], segments[0].startWeek)
        assertEquals(weeks[16], segments[1].endWeek)
        assertEquals(IntegrationAssessmentStatus.CONFIRMED_I0, assessment.status)
    }

    @Test
    fun disagreeingEligibleSegmentsAreInconclusive() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(21)
        val values = weeks.mapIndexed { index, week ->
            TrendDataPoint(
                week,
                when {
                    index < 10 -> if (index % 2 == 0) 1.0 else -1.0
                    index == 10 -> null
                    else -> 5.0
                }
            )
        }
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(x to values, y to points(weeks) { if (it % 2 == 0) 2.0 else -2.0 })
        ).ingest(request(x, y))

        val assessment = SegmentAwareIntegrationAssessmentAuthority.assess(catalog).getValue(x)
        val decisions = assessment.segmentDiagnostics.mapNotNull { it.decision }.toSet()

        assertEquals(IntegrationAssessmentStatus.INCONCLUSIVE, assessment.status)
        assertTrue(decisions.size > 1)
        assertTrue(SegmentIntegrationDecision.UNSUPPORTED in decisions)
    }

    @Test
    fun optionalInconclusiveIsExcludedButRequiredInconclusiveFailsWithoutFallback() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val optional = TrendMetricId.STRENGTH_VOLUME
        val weeks = weeks(24)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to points(weeks) { if (it % 2 == 0) 1.0 else -1.0 },
                y to points(weeks) { if (it % 2 == 0) 2.0 else -2.0 },
                optional to points(weeks) { 7.0 }
            )
        ).ingest(request(x, y, listOf(optional)))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)

        val optionalResult = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            request(x, y, listOf(optional))
        ) as CanonicalTransformationPlanResult.Success
        val requiredResult = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            request(optional, y, listOf(x))
        )

        assertEquals(CanonicalSeriesTransformation.EXCLUDED, optionalResult.plan.decisionsByMetric.getValue(optional).transformation)
        assertTrue(requiredResult is CanonicalTransformationPlanResult.Failure)
        assertEquals(
            StrictPreparationFailureCode.INCONCLUSIVE_TRANSFORMATION,
            (requiredResult as CanonicalTransformationPlanResult.Failure).code
        )
    }

    @Test
    fun i1SeriesIsDifferencedExactlyOnceWhileLevelSeriesRemainsAvailable() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(64)
        val walk = (0 until 64).runningFold(0.0) { total, index -> total + if (index % 2 == 0) 1.0 else -0.35 }.dropLast(1)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) },
                y to points(weeks) { if (it % 2 == 0) 1.0 else -1.0 }
            )
        ).ingest(request(x, y))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)

        assertEquals(IntegrationAssessmentStatus.CONFIRMED_I1, assessments.getValue(x).status)
        val plan = (CanonicalTransformationAuthority.createPlan(catalog, assessments, request(x, y)) as CanonicalTransformationPlanResult.Success).plan
        val transformed = TransformedPreparedCatalog.createValidated(catalog, plan).seriesByMetric.getValue(x)

        assertEquals(CanonicalSeriesTransformation.FIRST_DIFFERENCE, transformed.transformation)
        assertEquals(64, catalog.seriesByMetric.getValue(x).cells.size)
        assertEquals(64, transformed.cells.size)
        assertEquals(StrictCellState.MISSING, transformed.cells.first().state)
        assertEquals(walk[1] - walk[0], transformed.cells[1].value!!, 0.0)
        assertEquals(catalog.seriesByMetric.getValue(x).fingerprint, transformed.sourceLevelFingerprint)
    }

    @Test
    fun estimatorRepresentationsKeepI1LevelsAndDifferencesDistinct() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(64)
        val walk = (0 until 64).runningFold(0.0) { total, index -> total + if (index % 2 == 0) 1.0 else -0.35 }.dropLast(1)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to points(weeks) { if (it % 2 == 0) 1.0 else -1.0 },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) }
            )
        ).ingest(request(x, y))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)
        val transformationPlan = (CanonicalTransformationAuthority.createPlan(catalog, assessments, request(x, y)) as CanonicalTransformationPlanResult.Success).plan

        val representation = EstimatorRepresentationPlan.createValidated(transformationPlan, assessments).decisionsByMetric.getValue(y)
        val scale = ResponseScalePlan.createValidated(transformationPlan.decisionsByMetric.getValue(y))

        assertEquals(EstimatorSeriesRepresentation.CANONICAL_STATIONARY, representation.bvarRepresentation)
        assertEquals(EstimatorSeriesRepresentation.VALIDATED_LEVEL, representation.johansenRepresentation)
        assertEquals(EstimatorSeriesRepresentation.VALIDATED_LEVEL_AND_ALIGNED_FIRST_DIFFERENCE, representation.vecmRepresentation)
        assertEquals(ResponseDisplayScale.CUMULATIVE_LEVEL_RESPONSE, scale.displayScale)
        assertEquals(UncertaintyTransformationPolicy.TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS, scale.uncertaintyTransformationPolicy)
    }

    @Test
    fun responseScaleFingerprintTracksExplicitLogDifferenceInterpretation() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(64)
        val walk = (0 until 64).runningFold(10.0) { total, index -> total + if (index % 2 == 0) 1.0 else -0.35 }.dropLast(1)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to points(weeks) { if (it % 2 == 0) 1.0 else -1.0 },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) }
            )
        ).ingest(request(x, y))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)
        val defaultPlan = (CanonicalTransformationAuthority.createPlan(catalog, assessments, request(x, y)) as CanonicalTransformationPlanResult.Success).plan
        val logPlan = (CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            request(x, y),
            StrictPreparationPolicy.createValidated(explicitTransformations = mapOf(y to CanonicalSeriesTransformation.LOG_DIFFERENCE))
        ) as CanonicalTransformationPlanResult.Success).plan
        val defaultScale = ResponseScalePlan.createValidated(defaultPlan.decisionsByMetric.getValue(y))
        val logScale = ResponseScalePlan.createValidated(logPlan.decisionsByMetric.getValue(y))

        assertEquals(ResponseDisplayScale.APPROXIMATE_PERCENT_RESPONSE, logScale.displayScale)
        assertEquals(InverseTransformationRule.CUMULATIVE_EXPONENTIAL, logScale.inverseTransformationRule)
        assertNotEquals(defaultScale.fingerprint, logScale.fingerprint)
    }

    @Test
    fun shockPosteriorRequiresMultipleWeightedDrawSpecificSeries() {
        val calendar = CanonicalCalendar.createValidated(weeks(3))
        val posterior = IdentifiedShockPosterior.createValidated(
            sourceMetric = TrendMetricId.BADMINTON_TRAINING,
            orderedEndogenousMetrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            structuralOrdering = "temporal",
            normalizationPolicy = "one-standard-deviation",
            posteriorDrawIds = listOf("d1", "d2"),
            drawWeights = mapOf("d1" to 0.4, "d2" to 0.6),
            shockSeriesByDraw = mapOf("d1" to listOf(0.1, 0.2, 0.3), "d2" to listOf(0.2, 0.1, 0.4)),
            calendar = calendar,
            sourceCovarianceDrawFingerprintByDraw = mapOf("d1" to "cov1", "d2" to "cov2"),
            sourceBvarPosteriorFingerprint = "bvar",
            sourceContextFingerprint = "context",
            sourceSystemViewFingerprint = "view",
            rejectedDrawDiagnostics = listOf(RejectedShockDrawDiagnostic("d3", "non-SPD covariance"))
        )

        assertEquals(listOf("d1", "d2"), posterior.posteriorDrawIds)
        assertEquals("cov2", posterior.sourceCovarianceDrawFingerprintByDraw.getValue("d2"))
        assertTrue(
            runCatching {
                IdentifiedShockPosterior.createValidated(
                    TrendMetricId.BADMINTON_TRAINING,
                    listOf(TrendMetricId.BADMINTON_TRAINING),
                    "temporal",
                    "one-standard-deviation",
                    listOf("mean"),
                    mapOf("mean" to 1.0),
                    mapOf("mean" to listOf(0.1, 0.2, 0.3)),
                    calendar,
                    mapOf("mean" to "cov"),
                    "bvar",
                    "context",
                    "view"
                )
            }.isFailure
        )
    }

    private fun request(
        x: TrendMetricId,
        y: TrendMetricId,
        optional: List<TrendMetricId> = emptyList()
    ) = StrictPreparationRequest(x, listOf(y), optionalCandidates = optional, horizons = setOf(1, 2))

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }

    private fun points(weeks: List<LocalDate>, value: (Int) -> Double): List<TrendDataPoint> =
        weeks.mapIndexed { index, week -> TrendDataPoint(week, value(index)) }
}
