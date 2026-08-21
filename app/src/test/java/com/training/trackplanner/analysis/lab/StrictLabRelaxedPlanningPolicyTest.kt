package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.AnalysisWeekState
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState
import com.training.trackplanner.analysis.lab.weekly.WeeklyFeatureCell
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictLabRelaxedPlanningPolicyTest {
    @Test
    fun `twelve closed weeks can use the canonical strict and initial relaxed paths`() {
        val snapshot = snapshot(includeSparseControl = false)
        val request = StrictLabAnalysisRequest(X, listOf(Y), emptyList(), 2)

        val strict = StrictLabRelaxedPlanningPolicy.plan(snapshot, request, StrictLabAnalysisMode.STRICT)
        val relaxed = StrictLabRelaxedPlanningPolicy.plan(snapshot, request, StrictLabAnalysisMode.RELAXED)

        assertTrue(strict is StrictLabPlanningOutcome.Success)
        assertTrue(relaxed is StrictLabPlanningOutcome.Success)
        strict as StrictLabPlanningOutcome.Success
        relaxed as StrictLabPlanningOutcome.Success
        assertTrue(strict.planned.input.comparisonPlan.commonSourceWeeks.size >= 3)
        assertTrue(relaxed.planned.input.comparisonPlan.commonSourceWeeks.size >= 3)
        assertEquals(request, relaxed.effectiveRequest)
        assertNotEquals(strict.bundle.fingerprint, relaxed.bundle.fingerprint)
        assertTrue(relaxed.bundle.policy.fingerprint.isNotBlank())
    }

    @Test
    fun `relaxed removes only prefit sparse controls and rebuilds the canonical phase A identity`() {
        val snapshot = snapshot(includeSparseControl = true)
        val request = StrictLabAnalysisRequest(X, listOf(Y), listOf(SPARSE_CONTROL, COMPLETE_CONTROL), 2)

        val strict = StrictLabRelaxedPlanningPolicy.plan(snapshot, request, StrictLabAnalysisMode.STRICT)
        val relaxed = StrictLabRelaxedPlanningPolicy.plan(snapshot, request, StrictLabAnalysisMode.RELAXED)

        assertTrue(strict is StrictLabPlanningOutcome.Failure)
        strict as StrictLabPlanningOutcome.Failure
        assertEquals(StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN, strict.planned.code)
        assertEquals(request.controls, strict.effectiveRequest.controls)
        assertTrue(strict.planned.attemptedCommonRowsByPmax.isNotEmpty())

        assertTrue(relaxed is StrictLabPlanningOutcome.Success)
        relaxed as StrictLabPlanningOutcome.Success
        assertEquals(X, relaxed.effectiveRequest.xFeature)
        assertEquals(listOf(Y), relaxed.effectiveRequest.yFeatures)
        assertEquals(2, relaxed.effectiveRequest.requestedHorizon)
        assertEquals(listOf(COMPLETE_CONTROL), relaxed.effectiveRequest.controls)
        assertEquals(listOf(SPARSE_CONTROL), relaxed.relaxationTrace.removedControls)
        assertTrue(StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS in relaxed.relaxationTrace.appliedRoutes)
        assertTrue(relaxed.relaxationTrace.planningDetails.first().contains("full request exhausted"))
        assertTrue(relaxed.relaxationTrace.planningDetails.any { it.contains(RELAXED_CONTROL_REDUCTION_POLICY_VERSION) })
        assertTrue(SPARSE_CONTROL !in relaxed.planned.input.view.candidateMetrics)
        assertTrue(relaxed.planned.input.comparisonPlan.commonSourceWeeks.size >= 3)
        assertEquals(
            relaxed.planned.input.comparisonPlan.commonSourceWeeks,
            relaxed.planned.input.scalingPlan.baseScalingPlan.trainingRows
        )
        assertTrue(relaxed.planned.input.tauZeroByLag.values.all { it.isFinite() && it > 0.0 })
        assertNotEquals(strict.bundle.fingerprint, relaxed.bundle.fingerprint)
        assertNotEquals(strict.bundle.request.controls, relaxed.bundle.request.controls)
    }

    @Test
    fun `control removal order depends on availability not observed values`() {
        val first = snapshot(includeSparseControl = true, valueShift = 0.0)
        val changed = snapshot(includeSparseControl = true, valueShift = 1000.0)
        val controls = listOf(COMPLETE_CONTROL, SPARSE_CONTROL)

        assertEquals(
            listOf(SPARSE_CONTROL, COMPLETE_CONTROL),
            StrictLabRelaxedPlanningPolicy.controlRemovalOrder(first, controls)
        )
        assertEquals(
            StrictLabRelaxedPlanningPolicy.controlRemovalOrder(first, controls),
            StrictLabRelaxedPlanningPolicy.controlRemovalOrder(changed, controls)
        )
    }

    private fun snapshot(
        includeSparseControl: Boolean,
        valueShift: Double = 0.0
    ): WeeklyAnalysisFeatureSnapshot {
        val weeks = (0 until 12).map { START.plusWeeks(it.toLong()) }
        val descriptors = buildMap {
            put(X, descriptor(X, TrendMetricId.BADMINTON_PRACTICE_LOAD, "Training load", AnalysisFeatureFamily.TRAINING_FLOW))
            put(Y, descriptor(Y, TrendMetricId.FATIGUE_COMPOSITE, "Fatigue", AnalysisFeatureFamily.RECOVERY_CHECK_IN))
            if (includeSparseControl) {
                put(SPARSE_CONTROL, descriptor(SPARSE_CONTROL, TrendMetricId.SLEEP_HOURS, "Sleep", AnalysisFeatureFamily.RECOVERY_CHECK_IN))
                put(COMPLETE_CONTROL, descriptor(COMPLETE_CONTROL, TrendMetricId.STRENGTH_VOLUME, "Strength volume", AnalysisFeatureFamily.TRAINING_FLOW))
            }
        }
        val cells = descriptors.keys.associateWith { feature ->
            weeks.mapIndexed { index, week ->
                if (feature == SPARSE_CONTROL && index !in setOf(3, 8)) {
                    WeeklyFeatureCell(feature, week, WeeklyCellState.MISSING, null, "fixture")
                } else {
                    val base = when (feature) {
                        X -> listOf(2.0, 5.0, 3.0, 7.0, 4.0, 8.0, 6.0, 9.0, 5.5, 10.0, 7.5, 11.0)[index]
                        Y -> listOf(9.0, 7.0, 8.0, 5.0, 6.0, 4.0, 5.5, 3.0, 4.5, 2.0, 3.5, 1.0)[index]
                        SPARSE_CONTROL -> index.toDouble()
                        else -> (index % 4).toDouble() + index * 0.2
                    }
                    WeeklyFeatureCell(feature, week, WeeklyCellState.OBSERVED, base + valueShift, "fixture")
                }
            }
        }
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

    private fun descriptor(
        feature: AnalysisFeatureKey,
        metric: TrendMetricId,
        displayName: String,
        family: AnalysisFeatureFamily
    ) = AnalysisFeatureDescriptor(feature, AnalysisSourceKey.metric(metric), displayName, family)

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 1, 5)
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val SPARSE_CONTROL: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.SLEEP_HOURS)
        val COMPLETE_CONTROL: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.STRENGTH_VOLUME)
    }
}
