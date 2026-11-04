package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BBlockPurposePrescriptionTest {
    private fun item(key: String, reps: Int?, sets: Int = 2, seconds: Int? = null) =
        V22ExercisePlan(
            exercise = key, stableKey = key, category = "근력운동", role = "TEST",
            setCount = sets,
            sets = List(sets) { V22ObservedSet(120.0, reps, seconds, 8.5, "2026-10-01", "REFERENCE") },
            historicalSets28d = 0, historicalSets56d = 0
        )

    @Test fun goalChangesBasePrescriptionWhileNeverInventingLoads() {
        val original = listOf(V22Session(1, listOf(item("squat", 10), item("curl", 5)), 4))
        val exercises = mapOf("squat" to Exercise("squat", "squat", "근력운동"),
            "curl" to Exercise("curl", "curl", "근력운동"))
        val strength = BBlockPurposePrescription.prepare(original,
            BBlockDefinition(goal = BBlockGoal.STRENGTH), setOf("squat"), exercises)
        val hypertrophy = BBlockPurposePrescription.prepare(original,
            BBlockDefinition(goal = BBlockGoal.HYPERTROPHY), setOf("squat"), exercises)
        assertEquals(6, strength.single().items.first().sets.first().reps)
        assertEquals(10, hypertrophy.single().items.first().sets.first().reps)
        assertEquals(8, hypertrophy.single().items.last().sets.first().reps)
        assertTrue(strength.single().items.flatMap { it.sets }.all { it.weightKg == null && it.observedRpe == null })
    }

    @Test fun zeroHistoryReceivesUsableRepsAndSets() {
        val cold = item("squat", null, 1).copy(source = "COLD_START")
        val shaped = BBlockPurposePrescription.prepare(
            listOf(V22Session(1, listOf(cold), 1)),
            BBlockDefinition(goal = BBlockGoal.HYPERTROPHY), setOf("squat"), emptyMap()
        ).single().items.single()
        assertEquals(3, shaped.setCount)
        assertEquals(8, shaped.sets.first().reps)
        assertTrue(shaped.sets.all { it.weightKg == null })
    }

    @Test fun durationDrivenDrillsRetainSecondsWithoutFakeReps() {
        val interval = item("court_drill", null, 2, seconds = 30)
        val shaped = BBlockPurposePrescription.prepare(
            listOf(V22Session(1, listOf(interval), 2)),
            BBlockDefinition(goal = BBlockGoal.SPORT_SUPPORT), emptySet(), emptyMap()
        ).single().items.single()
        assertNull(shaped.sets.first().reps)
        assertEquals(30, shaped.sets.first().seconds)
    }

    @Test fun metadataTimeUnitWithNoHistoryNeverReceivesFictitiousRepetitions() {
        val exercise = Exercise("timed", "Timed drill", "기능성운동", progressMetricType = "TIME_OR_DISTANCE")
        val cold = item("timed", null, 1).copy(source = "COLD_START")
        val proposed = BBlockPurposePrescription.prepare(
            listOf(V22Session(1, listOf(cold), 1)),
            BBlockDefinition(goal = BBlockGoal.SPORT_SUPPORT), emptySet(), mapOf("timed" to exercise)
        ).single().items.single()
        assertEquals(2, proposed.setCount)
        assertNull(proposed.sets.first().reps)
        assertTrue(proposed.plannedProgression.orEmpty().contains("DURATION_OR_DISTANCE_USER_INPUT_REQUIRED"))
    }

    @Test fun finalPeakWeekUsesOneRepOneSetOfMainExercise() {
        val base = listOf(V22Session(1, listOf(item("squat", 3, 4), item("curl", 8, 3)), 7))
        val prepared = BBlockPurposePrescription.prepare(base,
            BBlockDefinition(goal = BBlockGoal.PEAK_EVALUATION), setOf("squat"), emptyMap())
        val peaked = BBlockPurposePrescription.finishWeek(prepared.single(),
            BBlockDefinition(goal = BBlockGoal.PEAK_EVALUATION), true, setOf("squat"))
        assertEquals(listOf(1, 2), peaked.items.map { it.setCount })
        assertEquals(1, peaked.items.first().sets.first().reps)
    }
}
