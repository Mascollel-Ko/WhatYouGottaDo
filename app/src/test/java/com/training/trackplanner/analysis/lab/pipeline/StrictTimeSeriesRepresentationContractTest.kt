package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTimeSeriesRepresentationContractTest {
    private val integrationFixtures = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_a_integration_reference.json").readText())
        .getJSONObject("fixtures")

    @Test
    fun diagnosticsPreserveSegmentsAndNeverCompressAcrossGapsOrLifecycleBoundaries() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(98)
        val lifecycle = StrictMetricLifecycle.createValidated(
            notApplicableRanges = listOf(StrictWeekRange(weeks[32], weeks[32])),
            versionDiscontinuityRanges = listOf(StrictWeekRange(weeks[65], weeks[65]))
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

        assertEquals(listOf(32, 32, 32), segments.map { it.length })
        assertTrue(segments.none { weeks[32] in it.weeks || weeks[65] in it.weeks })
        assertEquals(weeks[0], segments[0].startWeek)
        assertEquals(weeks[64], segments[1].endWeek)
        assertTrue(assessment.segmentDiagnostics.all { it.eligible })
    }

    @Test
    fun disagreeingEligibleSegmentsAreInconclusive() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val left = fixtureValues("two_segments_i0_left")
        val right = fixtureValues("two_segments_i1_left")
        val weeks = weeks(left.size + 1 + right.size)
        val values = weeks.mapIndexed { index, week ->
            TrendDataPoint(
                week,
                when {
                    index < left.size -> left[index]
                    index == left.size -> null
                    else -> right[index - left.size - 1]
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
    }

    @Test
    fun optionalInconclusiveIsExcludedButRequiredInconclusiveFailsWithoutFallback() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val optional = TrendMetricId.STRENGTH_VOLUME
        val supported = fixtureValues("stationary_ar_03")
        val weeks = weeks(supported.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index] * 2.0) },
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
        val walk = fixtureValues("random_walk")
        val weeks = weeks(walk.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, fixtureValues("stationary_ar_03")[index]) }
            )
        ).ingest(request(x, y))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)

        assertEquals(IntegrationAssessmentStatus.SUPPORTED_I1, assessments.getValue(x).status)
        val plan = (CanonicalTransformationAuthority.createPlan(catalog, assessments, request(x, y)) as CanonicalTransformationPlanResult.Success).plan
        val transformed = TransformedPreparedCatalog.createValidated(catalog, plan).seriesByMetric.getValue(x)

        assertEquals(CanonicalSeriesTransformation.FIRST_DIFFERENCE, transformed.transformation)
        assertEquals(80, catalog.seriesByMetric.getValue(x).cells.size)
        assertEquals(80, transformed.cells.size)
        assertEquals(StrictCellState.MISSING, transformed.cells.first().state)
        assertEquals(walk[1] - walk[0], transformed.cells[1].value!!, 0.0)
        assertEquals(catalog.seriesByMetric.getValue(x).fingerprint, transformed.sourceLevelFingerprint)
    }

    @Test
    fun estimatorRepresentationsKeepI1LevelsAndDifferencesDistinct() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val walk = fixtureValues("random_walk")
        val weeks = weeks(walk.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, fixtureValues("stationary_ar_03")[index]) },
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
    fun contradictoryExplicitTransformationFailsStrictPreparation() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val walk = fixtureValues("random_walk")
        val weeks = weeks(walk.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, fixtureValues("stationary_ar_03")[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) }
            )
        ).ingest(request(x, y))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)
        val mismatch = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            request(x, y),
            StrictPreparationPolicy.createValidated(explicitTransformations = mapOf(y to CanonicalSeriesTransformation.LEVEL))
        )

        assertTrue(mismatch is CanonicalTransformationPlanResult.Failure)
        assertEquals(
            StrictPreparationFailureCode.TRANSFORMATION_ASSESSMENT_CONFLICT,
            (mismatch as CanonicalTransformationPlanResult.Failure).code
        )
    }

    @Test
    fun shockPosteriorRequiresMultipleWeightedDrawSpecificSeries() {
        val sourceIdentity = bvarPosteriorSourceIdentity()
        val shockSize = sourceIdentity.eligibleSourceWeeks.size
        val posterior = IdentifiedShockPosterior.createValidated(
            sourceMetric = TrendMetricId.BADMINTON_TRAINING,
            orderedEndogenousMetrics = sourceIdentity.orderedEndogenousMetrics,
            structuralOrdering = "temporal",
            normalizationPolicy = "one-standard-deviation",
            posteriorDrawIds = listOf("d1", "d2"),
            drawWeights = mapOf("d1" to 0.4, "d2" to 0.6),
            shockSeriesByDraw = mapOf("d1" to List(shockSize) { 0.1 }, "d2" to List(shockSize) { 0.2 }),
            sourceCovarianceDrawFingerprintByDraw = mapOf("d1" to "cov1", "d2" to "cov2"),
            sourceIdentity = sourceIdentity,
            rejectedDrawDiagnostics = listOf(RejectedShockDrawDiagnostic("d3", "non-SPD covariance"))
        )

        assertEquals(listOf("d1", "d2"), posterior.posteriorDrawIds)
        assertEquals("cov2", posterior.sourceCovarianceDrawFingerprintByDraw.getValue("d2"))
        assertEquals(sourceIdentity.eligibleSourceWeeks, posterior.eligibleSourceWeeks)
        assertEquals(sourceIdentity.sourceRowPlanFingerprint, posterior.sourceRowPlanFingerprint)
        assertTrue(
            runCatching {
                IdentifiedShockPosterior.createValidated(
                    TrendMetricId.BADMINTON_TRAINING,
                    sourceIdentity.orderedEndogenousMetrics,
                    "temporal",
                    "one-standard-deviation",
                    listOf("mean"),
                    mapOf("mean" to 1.0),
                    mapOf("mean" to List(shockSize) { 0.1 }),
                    mapOf("mean" to "cov"),
                    sourceIdentity
                )
            }.isFailure
        )
    }

    @Test
    fun futureBlpInputRequiresShockPosteriorToCoverPreparedSourceRows() {
        val context = context()
        val view = BlpPreparedView.from(context)
        val rowPlan = RowPlanner.plan(context, view, 1, setOf(1), 1, HorizonPolicy.PER_HORIZON)
        val validPosterior = shockPosterior(context)

        val validInput = FutureBlpInput.createValidated(view, rowPlan, validPosterior, HorizonPolicy.PER_HORIZON)

        assertEquals(rowPlan.fingerprint, validInput.rowPlan.fingerprint)
        assertTrue(rowPlan.rows.all { it.sourceWeek in validPosterior.eligibleSourceWeeks })
        assertTrue(
            runCatching {
                FutureBlpInput.createValidated(view, rowPlan, shockPosterior(context, sourceMetric = TrendMetricId.FATIGUE_COMPOSITE), HorizonPolicy.PER_HORIZON)
            }.isFailure
        )
        assertTrue(
            runCatching {
                FutureBlpInput.createValidated(view, rowPlan, shockPosterior(context, dropLastEligibleWeek = true), HorizonPolicy.PER_HORIZON)
            }.isFailure
        )
        assertTrue(
            runCatching {
                FutureBlpInput.createValidated(view, rowPlan, validPosterior, HorizonPolicy.NOT_APPLICABLE)
            }.isFailure
        )
    }

    private fun request(
        x: TrendMetricId,
        y: TrendMetricId,
        optional: List<TrendMetricId> = emptyList()
    ) = StrictPreparationRequest(x, listOf(y), optionalCandidates = optional, horizons = setOf(1, 2))

    private fun context(): PreparedAnalysisContext {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val supported = fixtureValues("stationary_ar_03")
        val weeks = weeks(supported.size)
        val request = request(x, y)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index] * 2.0) }
            )
        ).ingest(request)
        return (PreparedAnalysisContext.createValidated(request, catalog) as StrictPreparationResult.Success).context
    }

    private fun bvarPosteriorSourceIdentity(
        context: PreparedAnalysisContext = context(),
        sourceMetric: TrendMetricId = context.request.xMetric,
        dropLastEligibleWeek: Boolean = false
    ): BvarPosteriorSourceIdentity {
        val view = BvarPreparedView.from(context)
        val rowPlan = RowPlanner.plan(context, view, 1, setOf(1), 1, HorizonPolicy.DECLARED_REFERENCE_HORIZON)
        val scaling = ScalingPlanner.plan(context, view, rowPlan, rowPlan.rows.take(12).map { it.sourceWeek })
        val input = FutureBvarInput.createValidated(view, rowPlan, scaling, "prior")
        val eligibleWeeks = rowPlan.rows.map { it.sourceWeek }.let { if (dropLastEligibleWeek) it.dropLast(1) else it }
        return BvarPosteriorSourceIdentity.createValidated(input, sourceMetric, "bvar-posterior", eligibleWeeks)
    }

    private fun shockPosterior(
        context: PreparedAnalysisContext,
        sourceMetric: TrendMetricId = context.request.xMetric,
        dropLastEligibleWeek: Boolean = false
    ): IdentifiedShockPosterior {
        val sourceIdentity = bvarPosteriorSourceIdentity(context, sourceMetric, dropLastEligibleWeek)
        val shockSize = sourceIdentity.eligibleSourceWeeks.size
        return IdentifiedShockPosterior.createValidated(
            sourceMetric = sourceMetric,
            orderedEndogenousMetrics = sourceIdentity.orderedEndogenousMetrics,
            structuralOrdering = "temporal",
            normalizationPolicy = "one-standard-deviation",
            posteriorDrawIds = listOf("d1", "d2"),
            drawWeights = mapOf("d1" to 0.5, "d2" to 0.5),
            shockSeriesByDraw = mapOf("d1" to List(shockSize) { 0.1 }, "d2" to List(shockSize) { 0.2 }),
            sourceCovarianceDrawFingerprintByDraw = mapOf("d1" to "cov1", "d2" to "cov2"),
            sourceIdentity = sourceIdentity
        )
    }

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }

    private fun points(weeks: List<LocalDate>, value: (Int) -> Double): List<TrendDataPoint> =
        weeks.mapIndexed { index, week -> TrendDataPoint(week, value(index)) }

    private fun fixtureValues(name: String): List<Double> =
        integrationFixtures.getJSONObject(name).getJSONArray("values").toList().map { (it as Number).toDouble() }

    private fun JSONArray.toList(): List<Any> = (0 until length()).map(::get)

    private fun existingFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(cwd) { it.parentFile }.map { File(it, path) }.first(File::exists)
    }
}
