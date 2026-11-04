package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BBlockPrescriptionApplicatorTest {
    private fun session(): V22Session {
        fun item(key: String) = V22ExercisePlan(
            exercise = key, stableKey = key, category = "근력운동", role = "OTHER",
            setCount = 3, sets = List(3) { V22ObservedSet(null, 5, null, null, "", "USER_CALIBRATION_REQUIRED") },
            historicalSets28d = 0, historicalSets56d = 0
        )
        return V22Session(1, listOf(item("squat"), item("deadlift"), item("curl")), 9)
    }

    @Test fun setsChangeOnePerMainExerciseNotOncePerSession() {
        val next = BBlockPrescriptionApplicator.apply(
            session(), 1, BBalancedGoal.STRENGTH,
            BBlockDefinition(adjustment = BVolumeAdjustment.SETS_FIRST),
            setOf("squat", "deadlift")
        )
        assertEquals(listOf(4, 4, 3), next.items.map { it.setCount })
        assertEquals(11, next.nominalSets)
        val down = BBlockPrescriptionApplicator.apply(
            session(), -1, BBalancedGoal.STRENGTH, BBlockDefinition(), setOf("squat", "deadlift")
        )
        assertEquals(listOf(2, 2, 3), down.items.map { it.setCount })
    }

    @Test fun hypertrophyProgressesOneRepetitionPerSetAndPreservesIdentity() {
        val changed = BBlockPrescriptionApplicator.apply(
            session(), 2, BBalancedGoal.HYPERTROPHY, BBlockDefinition(), setOf("squat", "deadlift")
        )
        assertEquals(listOf("squat", "deadlift", "curl"), changed.items.map { it.stableKey })
        assertTrue(changed.items.all { item -> item.sets.all { it.reps == 7 } })
        assertTrue(changed.items.all { item -> item.sets.all { it.weightKg == null } })
    }

    @Test fun strengthLoadFirstDoesNotInventKgBeforeCalibration() {
        val changed = BBlockPrescriptionApplicator.apply(
            session(), 2, BBalancedGoal.STRENGTH, BBlockDefinition(), setOf("squat")
        )
        // Equipment without metadata cannot safely inherit a paired barbell plate.
        // Continue to the next feasible lever: sets for a main exercise, then reps.
        assertEquals(5, changed.items.first().setCount)
        assertTrue(changed.items.flatMap { it.sets }.all { it.weightKg == null })
        assertTrue(changed.items.first().plannedProgression!!.contains("LOAD_STEPS=0"))
        assertTrue(changed.items.first().plannedProgression!!.contains("START_LOAD_USER_REQUIRED"))
    }
}
