package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictBayesianV07Sampler
import com.training.trackplanner.analysis.lab.strictbayes.StrictSamplingPolicy
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.AnalysisWeekState
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState
import com.training.trackplanner.analysis.lab.weekly.WeeklyFeatureCell
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBayesianLabAutomaticExecutionTest {
    @Test
    fun `finite posterior remains available after strict and relaxed diagnostic misses`() = runTest {
        val snapshot = snapshot()
        val impossible = StrictSamplingPolicy.testing(
            stabilization = 20,
            production = 40,
            maximumRhat = 1.000000000001,
            minimumEss = 1_000_000.0,
            maximumMcseToSd = 0.000001
        )
        val relaxedImpossible = StrictSamplingPolicy.testing(
            stabilization = 20,
            production = 40,
            maximumRhat = 1.000000000001,
            minimumEss = 1_000_000.0,
            maximumMcseToSd = 0.000001,
            reliabilityMode = StrictSamplingReliabilityMode.RELAXED
        )
        val service = StrictBayesianLabService(
            dispatcher = Dispatchers.Unconfined,
            samplerFactory = { design, _, retry -> StrictBayesianV07Sampler(design, impossible, retry) },
            relaxedSamplingPolicy = { relaxedImpossible }
        )
        val request = StrictLabAnalysisRequest(X, listOf(Y), listOf(CONSTANT_CONTROL), 2)
        val preflight = service.preflight(snapshot, request)

        assertTrue(preflight.canAnalyze)
        val outcome = service.execute(snapshot, request, preflight)

        assertTrue(outcome is StrictLabExecutionOutcome.Available)
        val result = (outcome as StrictLabExecutionOutcome.Available).result
        assertEquals(StrictSamplingDiagnosticClassification.LIMITED, result.samplingAssessment?.classification)
        assertTrue(result.responses.flatMap { it.points }.all { it.estimate.isFinite() })
        assertEquals(X, result.effectiveRequest.xFeature)
        assertEquals(listOf(Y), result.effectiveRequest.yFeatures)
        assertEquals(2, result.effectiveRequest.requestedHorizon)
        assertTrue(CONSTANT_CONTROL !in result.effectiveRequest.controls)
        assertTrue(result.adjustmentTrace.events.any { it.type == AnalysisAdjustmentType.REMOVE_CONTROL })
        assertTrue(result.adjustmentTrace.events.any { it.type == AnalysisAdjustmentType.SAMPLING_ASSESSMENT_RELAXED })
        assertTrue(result.adjustmentTrace.events.filterNot { it.modelStructureChanged }.all { event ->
            event.beforeFingerprint == event.afterFingerprint
        })
    }

    private fun snapshot(): WeeklyAnalysisFeatureSnapshot {
        val weeks = (0 until 18).map { START.plusWeeks(it.toLong()) }
        val descriptors = mapOf(
            X to AnalysisFeatureDescriptor(X, AnalysisSourceKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD), "Load", AnalysisFeatureFamily.TRAINING_FLOW),
            Y to AnalysisFeatureDescriptor(Y, AnalysisSourceKey.metric(TrendMetricId.FATIGUE_COMPOSITE), "Fatigue", AnalysisFeatureFamily.RECOVERY_CHECK_IN),
            CONSTANT_CONTROL to AnalysisFeatureDescriptor(
                CONSTANT_CONTROL,
                AnalysisSourceKey.metric(TrendMetricId.SLEEP_HOURS),
                "Sleep",
                AnalysisFeatureFamily.RECOVERY_CHECK_IN
            )
        )
        val cells = mapOf(
            X to weeks.mapIndexed { index, week -> cell(X, week, sin(index * 0.47) + index * 0.03) },
            Y to weeks.mapIndexed { index, week -> cell(Y, week, cos(index * 0.31) - index * 0.02) },
            CONSTANT_CONTROL to weeks.map { week -> cell(CONSTANT_CONTROL, week, 7.0) }
        )
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks,
            weeks.associateWith { AnalysisWeekState.CLOSED },
            descriptors,
            cells,
            emptyList(),
            1L,
            "metadata-v1",
            setOf("calculator-v1")
        )
    }

    private fun cell(feature: AnalysisFeatureKey, week: LocalDate, value: Double) =
        WeeklyFeatureCell(feature, week, WeeklyCellState.OBSERVED, value, "fixture")

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 1, 5)
        val X = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val CONSTANT_CONTROL = AnalysisFeatureKey.metric(TrendMetricId.SLEEP_HOURS)
    }
}
