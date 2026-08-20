package com.training.trackplanner.analysis.lab.weekly

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.WeeklyAnalysisAggregator
import com.training.trackplanner.analysis.trends.WeeklyAnalysisWindow
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyAnalysisFeatureSnapshotTest {
    @Test
    fun `full history window is not limited by dashboard week count`() {
        val today = LocalDate.of(2026, 8, 20)
        val old = record(today.minusWeeks(39), reps = 5, weight = 100.0, rpe = 8.0)

        val dashboard = WeeklyAnalysisAggregator().aggregate(today, listOf(old), emptyList())
        val full = WeeklyAnalysisAggregator(WeeklyAnalysisWindow.FULL_HISTORY)
            .aggregate(today, listOf(old), emptyList())

        assertEquals(12, dashboard.size)
        assertEquals(40, full.size)
        assertEquals(LocalDate.of(2025, 11, 17), full.first().weekStart)
    }

    @Test
    fun `snapshot preserves closed open missing zero and conditional rpe semantics`() {
        val today = LocalDate.of(2026, 8, 20)
        val closedWeek = LocalDate.of(2026, 8, 10)
        val exposed = record(closedWeek, reps = 5, weight = 100.0, rpe = null)
        val metricSeries = mapOf(
            TrendMetricId.BADMINTON_PRACTICE_LOAD to listOf(TrendDataPoint(closedWeek, 30.0)),
            TrendMetricId.SQUAT_E1RM to listOf(TrendDataPoint(closedWeek, 150.0))
        )

        val snapshot = WeeklyAnalysisFeatureSnapshotBuilder.build(
            today = today,
            metricSeries = metricSeries,
            entriesWithSets = listOf(exposed),
            exercises = listOf(EXERCISE),
            dailyMetrics = emptyList(),
            initialProfile = null,
            muscleRelations = listOf(muscleRelation()),
            sourceRevision = 7L
        )

        assertEquals(AnalysisWeekState.CLOSED, snapshot.weekStateByStart[closedWeek])
        assertEquals(AnalysisWeekState.OPEN, snapshot.weekStateByStart[LocalDate.of(2026, 8, 17)])
        assertEquals(
            WeeklyCellState.STRUCTURAL_ZERO,
            snapshot.cell(AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD), LocalDate.of(2026, 8, 17))?.state
        )
        assertEquals(
            WeeklyCellState.MISSING,
            snapshot.cell(AnalysisFeatureKey.metric(TrendMetricId.SQUAT_E1RM), LocalDate.of(2026, 8, 17))?.state
        )
        val rpe = snapshot.cell(AnalysisFeatureKey.exercise(EXERCISE.stableKey, "mean_rpe"), closedWeek)
        assertEquals(WeeklyCellState.MISSING, rpe?.state)
        assertNull(rpe?.value)
        val noExposureRpe = snapshot.cell(
            AnalysisFeatureKey.exercise(EXERCISE.stableKey, "mean_rpe"),
            LocalDate.of(2026, 8, 17)
        )
        assertEquals(WeeklyCellState.NOT_APPLICABLE, noExposureRpe?.state)
    }

    @Test
    fun `base dose uses canonical effective load without an rpe multiplier and anatomy uses reviewed relations`() {
        val week = LocalDate.of(2026, 8, 10)
        val snapshot = WeeklyAnalysisFeatureSnapshotBuilder.build(
            today = LocalDate.of(2026, 8, 16),
            metricSeries = mapOf(TrendMetricId.STRENGTH_VOLUME to listOf(TrendDataPoint(week, 500.0))),
            entriesWithSets = listOf(record(week, reps = 5, weight = 100.0, rpe = 10.0)),
            exercises = listOf(EXERCISE),
            dailyMetrics = emptyList(),
            initialProfile = null,
            muscleRelations = listOf(muscleRelation()),
            sourceRevision = 9L
        )

        val aggregate = snapshot.exerciseAggregates.single()
        assertEquals(500.0, aggregate.baseDose, 1e-9)
        assertEquals(100.0, aggregate.averageEffectiveLoadKg ?: Double.NaN, 1e-9)
        val anatomy = snapshot.cell(AnalysisFeatureKey.anatomy("QUADS"), week)
        assertEquals(WeeklyCellState.OBSERVED, anatomy?.state)
        assertEquals(300.0, anatomy?.value ?: 0.0, 1e-9)
        assertTrue(snapshot.fingerprint.isNotBlank())
    }

    private fun record(date: LocalDate, reps: Int, weight: Double, rpe: Double?): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = 1,
                date = date.toString(),
                exerciseStableKey = EXERCISE.stableKey,
                exerciseName = EXERCISE.name,
                category = EXERCISE.category,
                rpe = rpe
            ),
            sets = listOf(
                WorkoutSet(
                    id = 1,
                    entryId = 1,
                    setIndex = 0,
                    reps = reps,
                    weightKg = weight,
                    confirmed = true
                )
            )
        )

    private fun muscleRelation() = CanonicalMetadataRelation(
        domain = CanonicalRelationDomain.MUSCLE,
        relationKey = "fixture",
        exerciseStableKey = EXERCISE.stableKey,
        relationType = "PRIMARY",
        relationValue = "QUADS",
        coefficient = 0.6,
        sourceStableKey = EXERCISE.stableKey,
        status = "APPROVED",
        provenance = "fixture"
    )

    private companion object {
        val EXERCISE = Exercise(stableKey = "barbell_back_squat", name = "Back squat", category = "strength")
    }
}
