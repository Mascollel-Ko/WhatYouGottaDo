package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.IntegrationAssessmentStatus
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningResult
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarV07PlanningAuthority
import com.training.trackplanner.analysis.lab.pipeline.StrictFeatureSelection
import com.training.trackplanner.analysis.lab.pipeline.WeeklySnapshotPhaseAAdapter
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.AnalysisWeekState
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState
import com.training.trackplanner.analysis.lab.weekly.WeeklyFeatureCell
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBayesianLabAvailabilityTest {
    private val stationaryFixture = JSONObject(
        existingFile("tools/time_series_reference/fixtures/phase_a_integration_reference.json").readText()
    ).getJSONObject("fixtures").getJSONObject("stationary_ar_03").getJSONArray("values").toList()
        .map { (it as Number).toDouble() }

    @Test
    fun `legacy 8 24 and diagnostic 32 thresholds do not define strict model readiness`() = runTest {
        listOf(12, 16, 18, 24, 31, 32, 40).forEach { weekCount ->
            val snapshot = snapshot(weekCount)
            val request = request()
            val service = StrictBayesianLabService(Dispatchers.Unconfined)

            val preflight = service.preflight(snapshot, request)
            val planned = StrictBvarV07PlanningAuthority.plan(
                WeeklySnapshotPhaseAAdapter.adapt(snapshot, request.toSelection())
            )

            assertTrue("T=$weekCount was blocked by ${preflight.blockers}", preflight.canAnalyze)
            assertTrue("T=$weekCount failed: $planned", planned is StrictBvarPlanningResult.Success)
            val context = (planned as StrictBvarPlanningResult.Success).context
            val xAssessment = context.integrationAssessmentsByMetric.getValue(X)
            if (weekCount < 32) {
                assertEquals(IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE, xAssessment.status)
                assertTrue(xAssessment.segmentDiagnostics.none { it.eligible })
            } else {
                assertTrue(xAssessment.segmentDiagnostics.any { it.eligible })
            }
        }
    }

    @Test
    fun `strict picker exposes varying data without legacy min points eight`() {
        val catalog = StrictLabFeatureCatalog.from(snapshot(3))

        assertTrue(catalog.xFeatures.first { it.key == X }.enabled)
        assertTrue(catalog.responseFeatures.first { it.key == Y }.enabled)
        assertEquals(3, catalog.xFeatures.first { it.key == X }.availableWeeks)
    }

    @Test
    fun `phase A variation failure carries feature and closed week diagnostics`() = runTest {
        val snapshot = snapshot(12, constantX = true)
        val request = request()
        val service = StrictBayesianLabService(Dispatchers.Unconfined)
        val forcedExecutionPreflight = service.preflight(snapshot, request).copy(blockers = emptyList())

        val outcome = service.execute(snapshot, request, forcedExecutionPreflight)

        assertTrue(outcome is StrictLabExecutionOutcome.Unavailable)
        val failure = (outcome as StrictLabExecutionOutcome.Unavailable).failure
        assertEquals(StrictFailureStage.PHASE_A, failure.stage)
        assertEquals(X.value, failure.affectedFeatureOrSource)
        assertEquals(12, failure.availableClosedWeeks)
        assertTrue(
            failure.observations.any {
                it.name == "${X.value}.distinctFiniteValues" && it.observedValue == "1" && it.passed == false
            }
        )
    }

    @Test
    fun `display label changes do not change strict snapshot identity`() {
        val original = snapshot(12, xLabel = "Badminton load")
        val localized = snapshot(12, xLabel = "배드민턴 부하")

        assertEquals(original.fingerprint, localized.fingerprint)
        assertEquals(original.descriptors.getValue(X).featureKey, localized.descriptors.getValue(X).featureKey)
        assertFalse(original.descriptors.getValue(X).displayName == localized.descriptors.getValue(X).displayName)
    }

    @Test
    fun `conditional rpe uses an on carrier and exposed-row scaling without changing common rows`() {
        val weeks = (0 until 16).map { START.plusWeeks(it.toLong()) }
        val rpe = AnalysisFeatureKey.exercise("barbell_back_squat", "mean_rpe")
        val on = AnalysisFeatureKey.exercise("barbell_back_squat", "on")
        val descriptors = mapOf(
            rpe to AnalysisFeatureDescriptor(rpe, AnalysisSourceKey.parse("exercise:barbell_back_squat"), "Squat RPE", AnalysisFeatureFamily.CONDITIONAL_RPE),
            on to AnalysisFeatureDescriptor(on, AnalysisSourceKey.parse("exercise:barbell_back_squat"), "Squat exposure", AnalysisFeatureFamily.EXPOSURE_INDICATOR),
            Y to AnalysisFeatureDescriptor(Y, AnalysisSourceKey.metric(TrendMetricId.FATIGUE_COMPOSITE), "Fatigue", AnalysisFeatureFamily.RECOVERY_CHECK_IN)
        )
        val cells = mapOf(
            rpe to weeks.mapIndexed { index, week ->
                if (index % 2 == 0) cell(rpe, week, 5.0 + index * 0.1)
                else WeeklyFeatureCell(rpe, week, WeeklyCellState.NOT_APPLICABLE, null, "fixture")
            },
            on to weeks.mapIndexed { index, week ->
                if (index % 2 == 0) cell(on, week, 1.0)
                else WeeklyFeatureCell(on, week, WeeklyCellState.STRUCTURAL_ZERO, 0.0, "fixture")
            },
            Y to weeks.mapIndexed { index, week -> cell(Y, week, stationaryFixture[index] + index * 0.02) }
        )
        val snapshot = WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks,
            weeks.associateWith { AnalysisWeekState.CLOSED },
            descriptors,
            cells,
            emptyList(),
            2L,
            "metadata-v1",
            setOf("calculator-v1")
        )
        val bundle = WeeklySnapshotPhaseAAdapter.adapt(
            snapshot,
            StrictFeatureSelection(rpe, listOf(Y), emptyList(), requestedHorizon = 2)
        )
        val planned = StrictBvarV07PlanningAuthority.plan(bundle, requestedPmax = 2)

        assertTrue(planned is StrictBvarPlanningResult.Success)
        val input = (planned as StrictBvarPlanningResult.Success).input
        assertEquals(on, bundle.conditionalOnFeatureByFeature.getValue(rpe))
        assertEquals(on, input.scalingPlan.conditionalOnFeatureByFeature.getValue(rpe))
        assertEquals(input.comparisonPlan.commonSourceWeeks, input.scalingPlan.baseScalingPlan.trainingRows)
        val expectedMean = input.comparisonPlan.commonSourceWeeks
            .map { weeks.indexOf(it) }
            .filter { it % 2 == 0 }
            .map { 5.0 + it * 0.1 }
            .average()
        assertEquals(
            expectedMean,
            input.scalingPlan.baseScalingPlan.statisticsByMetric.getValue(rpe).mean,
            1e-12
        )
    }

    private fun request() = StrictLabAnalysisRequest(X, listOf(Y), emptyList(), requestedHorizon = 2)

    private fun StrictLabAnalysisRequest.toSelection() = StrictFeatureSelection(
        xFeature,
        yFeatures,
        controls,
        requestedHorizon
    )

    private fun snapshot(
        weekCount: Int,
        xLabel: String = "Badminton load",
        constantX: Boolean = false
    ): WeeklyAnalysisFeatureSnapshot {
        val weeks = (0 until weekCount).map { START.plusWeeks(it.toLong()) }
        val descriptors = mapOf(
            X to AnalysisFeatureDescriptor(X, AnalysisSourceKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD), xLabel, AnalysisFeatureFamily.TRAINING_FLOW),
            Y to AnalysisFeatureDescriptor(Y, AnalysisSourceKey.metric(TrendMetricId.FATIGUE_COMPOSITE), "Fatigue", AnalysisFeatureFamily.RECOVERY_CHECK_IN)
        )
        val cells = mapOf(
            X to weeks.mapIndexed { index, week ->
                cell(X, week, if (constantX) 1.0 else stationaryFixture[index])
            },
            Y to weeks.mapIndexed { index, week -> cell(Y, week, stationaryFixture[index] * 1.7 + (index % 3) * 0.05) }
        )
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks = weeks,
            weekStateByStart = weeks.associateWith { AnalysisWeekState.CLOSED },
            descriptors = descriptors,
            cellsByFeature = cells,
            exerciseAggregates = emptyList(),
            sourceRevision = 1L,
            metadataRevision = "metadata-v1",
            calculatorVersionSet = setOf("calculator-v1")
        )
    }

    private fun cell(key: AnalysisFeatureKey, week: LocalDate, value: Double) = WeeklyFeatureCell(
        featureKey = key,
        weekStart = week,
        state = WeeklyCellState.OBSERVED,
        value = value,
        provenance = "fixture"
    )

    private fun JSONArray.toList(): List<Any> = (0 until length()).map(::get)

    private fun existingFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(cwd) { it.parentFile }.map { File(it, path) }.first(File::exists)
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2025, 1, 6)
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
    }
}
