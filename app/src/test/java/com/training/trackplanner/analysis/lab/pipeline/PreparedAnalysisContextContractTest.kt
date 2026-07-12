package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedAnalysisContextContractTest {
    private val integrationFixtures = JSONObject(existingFile("tools/time_series_reference/fixtures/phase_a_integration_reference.json").readText())
        .getJSONObject("fixtures")

    @Test
    fun contextOwnsOneCalendarEveryPreparationDecisionAndCandidateEligibilityOnly() {
        val optional = TrendMetricId.STRENGTH_VOLUME
        val context = context(optionalValues = { 7.0 }, optional = listOf(optional))

        assertEquals(context.request.allMetrics, context.validatedLevelSeriesByMetric.keys)
        assertEquals(context.request.allMetrics, context.integrationAssessmentsByMetric.keys)
        assertEquals(context.request.allMetrics, context.canonicalTransformationPlan.decisionsByMetric.keys)
        assertEquals(context.request.allMetrics, context.estimatorRepresentationPlan.decisionsByMetric.keys)
        assertEquals(context.request.yMetrics.toSet(), context.responseScalePlansByMetric.keys)
        assertTrue(optional in context.candidateCatalog.excludedCandidates)
        assertTrue(optional !in context.candidateCatalog.eligibleCandidates)
        assertTrue(context.diagnostics.any { "PHASE E" in it })
    }

    @Test
    fun requiredInconclusiveMetricFailsBeforeContextExists() {
        val yValues = fixtureValues("stationary_ar_03")
        val weeks = weeks(yValues.size)
        val request = request()
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                request.xMetric to points(weeks) { 3.0 },
                request.yMetrics.single() to weeks.mapIndexed { index, week -> TrendDataPoint(week, yValues[index]) }
            )
        ).ingest(request)

        val result = PreparedAnalysisContext.createValidated(request, catalog)

        assertTrue(result is StrictPreparationResult.Failure)
        assertEquals(
            StrictPreparationFailureCode.INCONCLUSIVE_TRANSFORMATION,
            (result as StrictPreparationResult.Failure).code
        )
    }

    @Test
    fun estimatorViewsShareRootIdentityAndKeepLevelAndTransformedRepresentationsSeparate() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val walk = fixtureValues("random_walk")
        val weeks = weeks(walk.size)
        val request = StrictPreparationRequest(x, listOf(y), horizons = setOf(1, 2))
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, fixtureValues("stationary_ar_03")[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, walk[index]) }
            )
        ).ingest(request)
        val context = (PreparedAnalysisContext.createValidated(request, catalog) as StrictPreparationResult.Success).context

        val bvar = BvarPreparedView.from(context)
        val blp = BlpPreparedView.from(context)
        val johansen = JohansenPreparedView.from(context, listOf(y))
        val vecm = VecmPreparedView.from(context, listOf(y))

        assertTrue(listOf(bvar, blp, johansen, vecm).all { it.rootContextFingerprint == context.fingerprint })
        assertTrue(y in johansen.levelSeriesByMetric && y !in johansen.transformedSeriesByMetric)
        assertTrue(y in vecm.levelSeriesByMetric && y in vecm.transformedSeriesByMetric)
        assertNotEquals(context.validatedLevelSeriesByMetric.getValue(y).fingerprint, context.transformedSeriesByMetric.getValue(y).fingerprint)
    }

    @Test
    fun roleAwareRowsDoNotRequireFutureControlValuesAndHorizonsChangeIdentity() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y = TrendMetricId.FATIGUE_COMPOSITE
        val control = TrendMetricId.SLEEP_HOURS
        val values = fixtureValues("stationary_ar_03")
        val weeks = weeks(values.size)
        val request = StrictPreparationRequest(x, listOf(y), controls = listOf(control), horizons = setOf(1, 2))
        val catalog = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                x to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index]) },
                y to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index] * 2.0) },
                control to weeks.mapIndexed { index, week -> TrendDataPoint(week, if (index == weeks.lastIndex) null else values[index] * 3.0) }
            )
        ).ingest(request)
        val context = (PreparedAnalysisContext.createValidated(request, catalog) as StrictPreparationResult.Success).context
        val view = BlpPreparedView.from(context)

        val horizonOne = RowPlanner.plan(context, view, 1, setOf(1, 2), 1, HorizonPolicy.PER_HORIZON)
        val horizonTwo = RowPlanner.plan(context, view, 1, setOf(1, 2), 2, HorizonPolicy.PER_HORIZON)

        assertTrue(horizonOne.rows.any { it.sourceWeek == weeks[weeks.lastIndex - 1] })
        assertNotEquals(horizonOne.specification.fingerprint, horizonTwo.specification.fingerprint)
        assertNotEquals(horizonOne.fingerprint, horizonTwo.fingerprint)
        val controlRequirement = horizonOne.specification.requirements.single { it.metric == control }
        assertTrue(controlRequirement.requiredTargetOffsets.isEmpty())
    }

    @Test
    fun scalingUsesOnlyDeclaredTrainingRows() {
        val cleanValues = fixtureValues("stationary_ar_03")
        val dirtyValues = cleanValues.toMutableList().also { it[23] = 0.5 }
        val clean = context(xValues = cleanValues)
        val dirty = context(xValues = dirtyValues)
        val cleanView = BvarPreparedView.from(clean)
        val dirtyView = BvarPreparedView.from(dirty)
        val cleanRows = RowPlanner.plan(clean, cleanView, 1, setOf(1), 1, HorizonPolicy.DECLARED_REFERENCE_HORIZON)
        val dirtyRows = RowPlanner.plan(dirty, dirtyView, 1, setOf(1), 1, HorizonPolicy.DECLARED_REFERENCE_HORIZON)
        val training = cleanRows.rows.map { it.sourceWeek }.take(10)

        val cleanScaling = ScalingPlanner.plan(clean, cleanView, cleanRows, training)
        val dirtyScaling = ScalingPlanner.plan(dirty, dirtyView, dirtyRows, training)
        val changedRows = ScalingPlanner.plan(clean, cleanView, cleanRows, training.drop(1))

        assertEquals(cleanScaling.statisticsByMetric, dirtyScaling.statisticsByMetric)
        assertNotEquals(cleanScaling.fingerprint, changedRows.fingerprint)
        assertEquals(training.sorted(), cleanScaling.trainingRows)
    }

    @Test
    fun strictIdentityConstructorsCannotAcceptStaleFingerprints() {
        val types = listOf(
            PreparedAnalysisContext::class.java,
            PreparedCandidateCatalog::class.java,
            PreparedRowSpecification::class.java,
            PreparedRowPlan::class.java,
            PreparedScalingPlan::class.java
        )

        assertTrue(types.all { type -> type.declaredConstructors.filterNot { it.isSynthetic }.all { Modifier.isPrivate(it.modifiers) } })
    }

    @Test
    fun futureVariableSelectionViewContainsEligibilityButRowRankingIsDisabled() {
        val optional = TrendMetricId.STRENGTH_VOLUME
        val context = context(
            optionalValues = { index -> fixtureValues("stationary_ar_03")[index] * 4.0 },
            optional = listOf(optional)
        )
        val view = CandidateEligibilityView.from(context)

        assertEquals(listOf(optional), view.metrics)
        assertTrue(
            runCatching {
                RowPlanner.plan(context, view, 1, setOf(1), 1, HorizonPolicy.PER_HORIZON)
            }.isFailure
        )
    }

    private fun context(
        xValues: List<Double>? = null,
        optionalValues: ((Int) -> Double)? = null,
        optional: List<TrendMetricId> = emptyList()
    ): PreparedAnalysisContext {
        val values = fixtureValues("stationary_ar_03")
        val weeks = weeks(values.size)
        val request = request(optional)
        val series = mutableMapOf(
            request.xMetric to weeks.mapIndexed { index, week -> TrendDataPoint(week, xValues?.get(index) ?: values[index]) },
            request.yMetrics.single() to weeks.mapIndexed { index, week -> TrendDataPoint(week, values[index] * 2.0) }
        )
        optional.firstOrNull()?.let { metric -> series[metric] = points(weeks, optionalValues ?: { if (it % 2 == 0) 3.0 else -3.0 }) }
        val catalog = RawTimeSeriesInput.fromTrendSeries(series).ingest(request)
        return (PreparedAnalysisContext.createValidated(request, catalog) as StrictPreparationResult.Success).context
    }

    private fun request(optional: List<TrendMetricId> = emptyList()) = StrictPreparationRequest(
        TrendMetricId.BADMINTON_TRAINING,
        listOf(TrendMetricId.FATIGUE_COMPOSITE),
        optionalCandidates = optional,
        horizons = setOf(1, 2)
    )

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
