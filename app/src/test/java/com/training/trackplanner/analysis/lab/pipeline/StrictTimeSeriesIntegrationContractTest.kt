package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.lab.TimeSeriesAlignmentService
import com.training.trackplanner.analysis.lab.TimeSeriesObservation
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTimeSeriesIntegrationContractTest {
    private val fixture = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_a_integration_reference.json").readText())
    private val fixtures = fixture.getJSONObject("fixtures")

    @Test
    fun adfAndKpssMatchStatsmodelsIntermediateStatistics() {
        listOf("stationary_ar_03", "random_walk", "near_unit_root_ar_095").forEach { name ->
            val expected = fixtures.getJSONObject(name)
            val diagnostic = diagnosticFor(expected.values()).segmentDiagnostics.single()
            assertAdf(expected.getJSONObject("adf"), diagnostic.levelAdf)
            assertKpss(expected.getJSONObject("kpss"), diagnostic.levelKpss)
        }
    }

    @Test
    fun constantAndSingularSeriesAreInconclusiveNotCoerced() {
        listOf("constant_series", "singular_adf_regression").forEach { name ->
            val assessment = diagnosticFor(fixtures.getJSONObject(name).values())

            assertEquals(IntegrationAssessmentStatus.INCONCLUSIVE, assessment.status)
            assertEquals(SegmentIntegrationDecision.INCONCLUSIVE, assessment.segmentDiagnostics.single().decision)
            assertNotNull(assessment.segmentDiagnostics.single().exclusionReason)
        }
    }

    @Test
    fun segmentMinimumIsThirtyTwoWeeksAndSegmentsAreNotConcatenated() {
        val insufficient = diagnosticFor(fixtures.getJSONObject("insufficient_31_week_segment").values())
        val exact = diagnosticFor(fixtures.getJSONObject("exactly_32_week_segment").values())
        val split = diagnosticFor(
            fixtures.getJSONObject("lifecycle_separated_left").values() +
                listOf(null) +
                fixtures.getJSONObject("lifecycle_separated_right").values()
        )

        assertEquals(IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE, insufficient.status)
        assertTrue(exact.segmentDiagnostics.single().eligible)
        assertEquals(listOf(40, 40), split.segmentDiagnostics.map { it.segment.length })
    }

    @Test
    fun requiredAndOptionalInconclusivePoliciesAreFixed() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val optional = TrendMetricId.STRENGTH_VOLUME
        val supported = fixtureValues("stationary_ar_03")
        val weeks = weeks(supported.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index] * 2.0) },
                optional to points(weeks) { 3.0 }
            )
        ).ingest(StrictPreparationRequest(x, listOf(y), optionalCandidates = listOf(optional)))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)
        val optionalResult = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            StrictPreparationRequest(x, listOf(y), optionalCandidates = listOf(optional))
        )
        val requiredResult = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            StrictPreparationRequest(optional, listOf(y))
        )

        assertTrue(optionalResult is CanonicalTransformationPlanResult.Success)
        assertEquals(
            CanonicalSeriesTransformation.EXCLUDED,
            (optionalResult as CanonicalTransformationPlanResult.Success).plan.decisionsByMetric.getValue(optional).transformation
        )
        assertTrue(requiredResult is CanonicalTransformationPlanResult.Failure)
        assertEquals(StrictPreparationFailureCode.INCONCLUSIVE_TRANSFORMATION, (requiredResult as CanonicalTransformationPlanResult.Failure).code)
    }

    @Test
    fun explicitTransformationMustMatchCanonicalAssessment() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val supported = fixtureValues("stationary_ar_03")
        val weeks = weeks(supported.size)
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, supported[index] * 2.0) }
            )
        ).ingest(StrictPreparationRequest(x, listOf(y)))
        val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(catalog)
        val result = CanonicalTransformationAuthority.createPlan(
            catalog,
            assessments,
            StrictPreparationRequest(x, listOf(y)),
            StrictPreparationPolicy.createValidated(explicitTransformations = mapOf(x to CanonicalSeriesTransformation.FIRST_DIFFERENCE))
        )

        assertTrue(result is CanonicalTransformationPlanResult.Failure)
        assertEquals(StrictPreparationFailureCode.TRANSFORMATION_ASSESSMENT_CONFLICT, (result as CanonicalTransformationPlanResult.Failure).code)
    }

    @Test
    fun scalingRejectsConstantNearConstantAndTooShortTrainingSamples() {
        val context = contextForScaling()
        val view = BvarPreparedView.from(context)
        val rows = RowPlanner.plan(context, view, 1, setOf(1), 1, HorizonPolicy.DECLARED_REFERENCE_HORIZON)
        val constant = rows.rows.take(3).map { it.sourceWeek }
        val tooShort = rows.rows.take(2).map { it.sourceWeek }

        assertEquals(
            ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES,
            (runCatching { ScalingPlanner.plan(context, view, rows, constant) }.exceptionOrNull() as ScalingPlanFailureException).code
        )
        assertEquals(
            ScalingFailureCode.TOO_FEW_TRAINING_VALUES,
            (runCatching { ScalingPlanner.plan(context, view, rows, tooShort) }.exceptionOrNull() as ScalingPlanFailureException).code
        )
    }

    @Test
    fun strictHorizonRangeIsOneThroughEightAndNoHorizonRowsUseNoZeroSentinel() {
        assertTrue(runCatching { StrictPreparationRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), horizons = setOf(1)) }.isSuccess)
        assertTrue(runCatching { StrictPreparationRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), horizons = setOf(8)) }.isSuccess)
        listOf(emptySet(), setOf(0), setOf(9), setOf(-1), setOf(1, 9)).forEach { horizons ->
            assertTrue(runCatching { StrictPreparationRequest(TrendMetricId.BADMINTON_TRAINING, listOf(TrendMetricId.FATIGUE_COMPOSITE), horizons = horizons) }.isFailure)
        }
        val context = contextForScaling()
        val view = BvarPreparedView.from(context)
        val rows = RowPlanner.planWithoutHorizon(context, view, 1)

        assertEquals(HorizonPolicy.NOT_APPLICABLE, rows.specification.horizonPolicy)
        assertTrue(rows.specification.requestedHorizons.isEmpty())
        assertEquals(null, rows.specification.referenceHorizon)
    }

    @Test
    fun conflictingRawRevisionsRemainConflictProvenanceAtIngestionBoundary() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val week = LocalDate.parse("2026-01-05")
        val alignment = requireNotNull(TimeSeriesAlignmentService().alignObservations(
            listOf(x, y),
            listOf(
                TimeSeriesObservation(x, week, 1.0, source = "resolver-a"),
                TimeSeriesObservation(x, week, 2.0, source = "resolver-b"),
                TimeSeriesObservation(y, week, 1.0, source = "resolver")
            )
        ))
        val input = RawTimeSeriesInput.fromResolvedAlignment(alignment)
        val catalog = input.ingest(StrictPreparationRequest(x, listOf(y)))
        val cell = catalog.seriesByMetric.getValue(x).cells.single()

        assertEquals(StrictCellState.CONFLICT, cell.state)
        assertEquals(2, cell.provenance.size)
        assertTrue(cell.provenance.all { it.conflictSelectionRule == "UNRESOLVED_CONFLICT" })
    }

    private fun diagnosticFor(values: List<Double?>): IntegrationOrderAssessment {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val weeks = weeks(values.size)
        val input = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index]) },
                y to points(weeks) { if (it % 2 == 0) 1.0 else -1.0 }
            )
        )
        return SegmentAwareIntegrationAssessmentAuthority.assess(input.ingest(StrictPreparationRequest(x, listOf(y)))).getValue(x)
    }

    private fun contextForScaling(): PreparedAnalysisContext {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val values = fixtureValues("stationary_ar_03").toMutableList().also {
            it[1] = it[0]
            it[2] = it[0]
            it[3] = it[0]
        }
        val weeks = weeks(values.size)
        val request = StrictPreparationRequest(x, listOf(y))
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index] * 2.0) }
            )
        ).ingest(request)
        return (PreparedAnalysisContext.createValidated(request, catalog) as StrictPreparationResult.Success).context
    }

    private fun assertAdf(expected: JSONObject, actual: AdfReferenceDiagnostic?) {
        require(expected.getString("status") == "OK")
        requireNotNull(actual)
        assertEquals(expected.getDouble("statistic"), actual.statistic, 1e-6)
        assertEquals(expected.getDouble("p_value"), actual.pValue, 1e-6)
        assertEquals(expected.getInt("selected_lag"), actual.selectedLag)
        assertEquals(expected.getInt("effective_nobs"), actual.effectiveObservationCount)
        assertEquals(expected.getDouble("best_aic"), actual.bestAic, 1e-6)
        val critical = expected.getJSONObject("critical_values")
        listOf("1%", "5%", "10%").forEach { key -> assertEquals(critical.getDouble(key), actual.criticalValues.getValue(key), 1e-6) }
    }

    private fun assertKpss(expected: JSONObject, actual: KpssReferenceDiagnostic?) {
        require(expected.getString("status") == "OK")
        requireNotNull(actual)
        assertEquals(expected.getDouble("statistic"), actual.statistic, 1e-6)
        assertEquals(expected.getDouble("p_value"), actual.pValue, 1e-6)
        assertEquals(expected.getInt("selected_lag"), actual.selectedLag)
        val critical = expected.getJSONObject("critical_values")
        listOf("1%", "2.5%", "5%", "10%").forEach { key -> assertEquals(critical.getDouble(key), actual.criticalValues.getValue(key), 1e-12) }
    }

    private fun JSONObject.values(): List<Double?> =
        getJSONArray("values").toList().map { (it as Number).toDouble() }

    private fun fixtureValues(name: String): List<Double> =
        fixtures.getJSONObject(name).getJSONArray("values").toList().map { (it as Number).toDouble() }

    private fun JSONArray.toList(): List<Any> = (0 until length()).map(::get)

    private fun existingFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(cwd) { it.parentFile }.map { File(it, path) }.first(File::exists)
    }

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }

    private fun points(weeks: List<LocalDate>, value: (Int) -> Double): List<TrendDataPoint> =
        weeks.mapIndexed { index, week -> TrendDataPoint(week, value(index)) }
}
