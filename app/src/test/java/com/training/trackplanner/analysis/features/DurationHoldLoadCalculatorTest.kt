package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationHoldLoadCalculatorTest {
    @Test
    fun plankLoadScalesWithSecondsAndRpe() {
        val plank = exercise("plank", "front_plank")

        val short = DurationHoldLoadCalculator.holdLoad(plank, set(seconds = 20, rpe = 7.0), null) ?: 0.0
        val long = DurationHoldLoadCalculator.holdLoad(plank, set(seconds = 40, rpe = 7.0), null) ?: 0.0
        val easy = DurationHoldLoadCalculator.holdLoad(plank, set(seconds = 40, rpe = 6.0), null) ?: 0.0
        val hard = DurationHoldLoadCalculator.holdLoad(plank, set(seconds = 40, rpe = 8.0), null) ?: 0.0

        assertTrue(long > short)
        assertTrue(hard > easy)
        assertEquals(40.0, long, 0.001)
        assertEquals(34.0, easy, 0.001)
        assertEquals(46.0, hard, 0.001)
    }

    @Test
    fun plankDurationDoesNotUseBodyweightVolumePath() {
        val plank = exercise("plank", "front_plank")
        val durationLoad = DurationHoldLoadCalculator.holdLoad(plank, set(seconds = 40, reps = 0), null) ?: 0.0

        assertEquals(40.0, durationLoad, 0.001)
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(plank, set(seconds = 40, reps = 10), 80.0), 0.001)
    }

    @Test
    fun sidePlankUsesSeparateHoldPolicy() {
        assertEquals(DurationHoldPolicy.SIDE_PLANK, DurationHoldLoadCalculator.policyFor("side_plank", "side plank"))
        assertEquals(DurationHoldPolicy.PLANK, DurationHoldLoadCalculator.policyFor("front_plank", "plank"))
    }

    @Test
    fun excludesDrillsAndNonHoldExercises() {
        assertNull(DurationHoldLoadCalculator.policyFor("six_corner_shadow", "six corner shadow footwork"))
        assertNull(DurationHoldLoadCalculator.policyFor("shuttle_beep", "shuttle beep drill"))
        assertNull(DurationHoldLoadCalculator.policyFor("hanging_leg_raise", "hanging leg raise"))
        assertNull(DurationHoldLoadCalculator.policyFor("captain_chair_leg_raise", "captain chair leg raise"))
        assertNull(DurationHoldLoadCalculator.policyFor("walking_lunge", "walking lunge"))
        assertNull(DurationHoldLoadCalculator.policyFor("standing_calf_raise", "standing calf raise"))
    }

    private fun exercise(name: String, stableKey: String): Exercise =
        Exercise(
            id = 1,
            name = name,
            category = "strength",
            stableKey = stableKey
        )

    private fun set(
        seconds: Int,
        reps: Int = 0,
        rpe: Double? = null,
        weightKg: Double = 0.0
    ): WorkoutSet =
        WorkoutSet(
            entryId = 1,
            setIndex = 1,
            reps = reps,
            weightKg = weightKg,
            seconds = seconds,
            confirmed = true,
            rpe = rpe
        )
}
