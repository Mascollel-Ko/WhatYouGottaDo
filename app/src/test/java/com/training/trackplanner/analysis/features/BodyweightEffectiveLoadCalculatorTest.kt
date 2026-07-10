package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyweightEffectiveLoadCalculatorTest {
    @Test
    fun calculatesCalisthenicsEffectiveVolumeFromBodyweight() {
        assertVolume("pull-up", "pull_up", 0.0, 800.0)
        assertVolume("pull-up", "pull_up", 10.0, 900.0)
        assertVolume("chin-up", "chin_up", 0.0, 800.0)
        assertVolume("neutral grip pull-up", "neutral_grip_pull_up", 0.0, 800.0)
        assertVolume("assisted pull-up", "assisted_pull_up", 30.0, 500.0)
        assertVolume("assisted pull-up", "assisted_pull_up", 100.0, 0.0)
        assertVolume("dip", "dip", 0.0, 800.0)
        assertVolume("dip", "dip", 10.0, 900.0)
        assertVolume("push-up", "push_up", 0.0, 520.0)
        assertVolume("push-up", "push_up", 10.0, 590.0)
        assertVolume("pike push-up", "pike_push_up", 0.0, 560.0)
        assertVolume("decline push-up", "decline_push_up", 0.0, 640.0)
        assertVolume("incline push-up", "incline_push_up", 0.0, 440.0)
        assertVolume("inverted row", "inverted_row", 0.0, 480.0)
    }

    @Test
    fun resolvesBodyweightByDateThenPreviousMetricThenInitialProfile() {
        val metrics = listOf(
            DailyMetric(date = "2026-07-09", bodyWeightKg = 78.0),
            DailyMetric(date = "2026-07-10", bodyWeightKg = 80.0)
        )
        val profile = InitialUserProfile(bodyWeightKg = 75.0)

        assertEquals(80.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-10", metrics, profile) ?: 0.0, 0.001)
        assertEquals(80.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-11", metrics, profile) ?: 0.0, 0.001)
        assertEquals(75.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-08", metrics, profile) ?: 0.0, 0.001)
        assertNull(BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-08", emptyList(), null))
    }

    @Test
    fun leavesExcludedBodyweightAndDrillExercisesOnRawVolumePath() {
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise("lunge", "lunge"), set(), 80.0), 0.001)
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise("hanging leg raise", "hanging_leg_raise"), set(), 80.0), 0.001)
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise("plank", "plank"), set(), 80.0), 0.001)
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise("six corner shadow footwork", "six_corner_shadow"), set(), 80.0), 0.001)
    }

    @Test
    fun keepsRawPathWhenBodyweightIsUnavailable() {
        val pullUp = exercise("pull-up", "pull_up")

        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(pullUp, set(weightKg = 0.0), null), 0.001)
        assertEquals(100.0, BodyweightEffectiveLoadCalculator.volumeLoad(pullUp, set(weightKg = 10.0), null), 0.001)
    }

    private fun assertVolume(name: String, stableKey: String, weightKg: Double, expected: Double) {
        assertEquals(
            expected,
            BodyweightEffectiveLoadCalculator.volumeLoad(exercise(name, stableKey), set(weightKg = weightKg), 80.0),
            0.001
        )
    }

    private fun exercise(name: String, stableKey: String): Exercise =
        Exercise(
            id = 1,
            name = name,
            category = "strength",
            stableKey = stableKey
        )

    private fun set(weightKg: Double = 0.0): WorkoutSet =
        WorkoutSet(
            entryId = 1,
            setIndex = 1,
            reps = 10,
            weightKg = weightKg,
            confirmed = true
        )
}
