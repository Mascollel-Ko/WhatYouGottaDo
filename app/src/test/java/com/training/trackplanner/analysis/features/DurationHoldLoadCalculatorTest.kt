package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationHoldLoadCalculatorTest {
    @Test
    fun canonicalPlankLoadScalesWithSecondsAndRpe() {
        val plank = exercise("arbitrary label", "ex_a44ae2ca")

        val short = DurationHoldLoadCalculator.holdLoad(plank, set(20, 7.0), null) ?: 0.0
        val long = DurationHoldLoadCalculator.holdLoad(plank, set(40, 7.0), null) ?: 0.0
        val easy = DurationHoldLoadCalculator.holdLoad(plank, set(40, 6.0), null) ?: 0.0
        val hard = DurationHoldLoadCalculator.holdLoad(plank, set(40, 8.0), null) ?: 0.0

        assertTrue(long > short)
        assertTrue(hard > easy)
        assertEquals(40.0, long, 0.001)
        assertEquals(34.0, easy, 0.001)
        assertEquals(46.0, hard, 0.001)
    }

    @Test
    fun exactCanonicalPoliciesIncludeCopenhagenParity() {
        assertEquals(
            setOf("ex_a44ae2ca", "ex_a8385c4a", "ex_f6d43398"),
            DurationHoldProfileAuthority.supportedStableKeys()
        )
        assertEquals(DurationHoldPolicy.PLANK, DurationHoldLoadCalculator.policyFor("ex_a44ae2ca"))
        assertEquals(DurationHoldPolicy.PLANK, DurationHoldLoadCalculator.policyFor("ex_a8385c4a"))
        assertEquals(DurationHoldPolicy.SIDE_PLANK, DurationHoldLoadCalculator.policyFor("ex_f6d43398"))
    }

    @Test
    fun arbitraryNamesCannotGrantHoldSemantics() {
        listOf("Side Plank", "Copenhagen Plank", "\uC0AC\uC774\uB4DC \uD50C\uB7AD\uD06C", "\uD50C\uB7AD\uD06C").forEach { name ->
            assertNull(DurationHoldLoadCalculator.holdLoad(exercise(name, "unknown-$name"), set(40), null))
        }
    }

    @Test
    fun durationHoldDoesNotUseBodyweightVolumePath() {
        val plank = exercise("renamed", "ex_a44ae2ca")
        assertEquals(40.0, DurationHoldLoadCalculator.holdLoad(plank, set(40), null) ?: 0.0, 0.001)
        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(plank, set(40, reps = 10), 80.0), 0.001)
    }

    private fun exercise(name: String, stableKey: String): Exercise =
        Exercise(name = name, category = "strength", stableKey = stableKey)

    private fun set(
        seconds: Int,
        rpe: Double? = null,
        reps: Int = 0,
        weightKg: Double = 0.0
    ): WorkoutSet = WorkoutSet(
        entryId = 1,
        setIndex = 1,
        reps = reps,
        weightKg = weightKg,
        seconds = seconds,
        confirmed = true,
        rpe = rpe
    )
}
