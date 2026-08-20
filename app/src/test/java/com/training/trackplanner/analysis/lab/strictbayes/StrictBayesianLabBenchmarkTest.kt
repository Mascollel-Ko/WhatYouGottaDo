package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.BvarDesignMatrixMaterializer
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
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictBayesianLabBenchmarkTest {
    @Test
    fun `benchmark weekly preparation phase a and phase b separately`() {
        lateinit var snapshot: WeeklyAnalysisFeatureSnapshot
        val weeklyNanos = measureNanoTime { snapshot = snapshot() }

        lateinit var planned: StrictBvarPlanningResult.Success
        val phaseANanos = measureNanoTime {
            val bundle = WeeklySnapshotPhaseAAdapter.adapt(
                snapshot,
                StrictFeatureSelection(X, listOf(Y), emptyList(), requestedHorizon = 3)
            )
            val result = StrictBvarV07PlanningAuthority.plan(bundle, requestedPmax = 3)
            assertTrue(result is StrictBvarPlanningResult.Success)
            planned = result as StrictBvarPlanningResult.Success
        }

        lateinit var outcome: StrictBayesianV07Outcome
        val phaseBNanos = measureNanoTime {
            val design = BvarDesignMatrixMaterializer.materialize(planned.context, planned.input)
            outcome = StrictBayesianV07Sampler(
                design,
                StrictSamplingPolicy.testing(stabilization = 20, production = 40)
            ).sample()
        }

        assertTrue(outcome is StrictBayesianV07Outcome.Success)
        assertTrue(weeklyNanos > 0L && phaseANanos > 0L && phaseBNanos > 0L)
        println(
            "STRICT_BAYES_BENCHMARK desktop-jvm " +
                "weeklyMs=${weeklyNanos / 1_000_000.0} " +
                "phaseAMs=${phaseANanos / 1_000_000.0} " +
                "phaseBMs=${phaseBNanos / 1_000_000.0}"
        )
    }

    private fun snapshot(): WeeklyAnalysisFeatureSnapshot {
        val weeks = (0 until 24).map { START.plusWeeks(it.toLong()) }
        val descriptors = mapOf(
            X to AnalysisFeatureDescriptor(
                X,
                AnalysisSourceKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD),
                "Badminton practice load",
                AnalysisFeatureFamily.TRAINING_FLOW
            ),
            Y to AnalysisFeatureDescriptor(
                Y,
                AnalysisSourceKey.metric(TrendMetricId.FATIGUE_COMPOSITE),
                "Fatigue",
                AnalysisFeatureFamily.RECOVERY_CHECK_IN
            )
        )
        val cells = mapOf(
            X to weeks.mapIndexed { index, week ->
                WeeklyFeatureCell(X, week, WeeklyCellState.OBSERVED, sin(index * 0.43) + index % 3, "benchmark")
            },
            Y to weeks.mapIndexed { index, week ->
                WeeklyFeatureCell(Y, week, WeeklyCellState.OBSERVED, cos(index * 0.29) + sin(index * 0.13), "benchmark")
            }
        )
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks,
            weeks.associateWith { AnalysisWeekState.CLOSED },
            descriptors,
            cells,
            emptyList(),
            1L,
            "benchmark-metadata-v1",
            setOf("benchmark-calculator-v1")
        )
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2025, 1, 6)
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
    }
}
