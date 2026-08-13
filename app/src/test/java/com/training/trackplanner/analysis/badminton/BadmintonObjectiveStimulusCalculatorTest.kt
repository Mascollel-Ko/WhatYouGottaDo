package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonObjectiveStimulusCalculatorTest {
    private val exercise = Exercise("drill", "Drill", "기능성운동")
    private val relations = listOf(
        relation("footwork", BadmintonObjective.FOOTWORK, BadmintonObjectiveTransferLevel.DIRECT),
        relation("deceleration", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.DIRECT),
        relation("reaction", BadmintonObjective.REACTION, BadmintonObjectiveTransferLevel.SUPPORTIVE)
    )
    private val catalog = CanonicalBadmintonObjectiveCatalog.of(
        relations,
        historySourceByStableKey = mapOf("history_drill" to "drill")
    )
    private val calculator = BadmintonObjectiveStimulusCalculator(catalog)

    @Test
    fun confirmedSetStimulusIgnoresKgRepsSecondsAndDoesNotDivideMultiLabels() {
        val first = calculate(exercise, List(4) { set(8.0, 10.0, 2, 0) })
        val changedDose = calculate(exercise, List(4) { set(8.0, 200.0, 100, 900) })

        assertEquals(4.20, first.getValue("FOOTWORK"), 0.0001)
        assertEquals(4.20, first.getValue("DECELERATION"), 0.0001)
        assertEquals(2.52, first.getValue("REACTION"), 0.0001)
        assertEquals(first, changedDose)
    }

    @Test
    fun allNineObjectivesRemainRepresentableAtZero() {
        val result = calculator.calculate(emptyList(), emptyMap())

        assertEquals(BadmintonObjective.entries.map { it.name }, result.keys.toList())
        assertTrue(result.values.all { it == 0.0 })
        assertEquals(0.0, result.getValue("ANTI_ROTATION"), 0.0)
    }

    @Test
    fun genericSportPracticeDoesNotExplodeAcrossObjectives() {
        val sport = exercise.copy(activityKind = "SPORT_SESSION")
        val result = calculate(sport, List(4) { set(10.0, 0.0, 0, 900) })

        assertTrue(result.values.all { it == 0.0 })
    }

    @Test
    fun historyIdentityUsesCanonicalSemanticsWithoutChangingStoredKey() {
        val historical = exercise.copy(stableKey = "history_drill")
        val result = calculate(historical, listOf(set(8.0, 0.0, 0, 0)))

        assertEquals(1.05, result.getValue("FOOTWORK"), 0.0001)
        assertEquals("history_drill", historical.stableKey)
    }

    private fun calculate(exercise: Exercise, sets: List<WorkoutSet>) = calculator.calculate(
        listOf(
            WorkoutEntryWithSets(
                WorkoutEntry(
                    date = "2026-08-01",
                    exerciseStableKey = exercise.stableKey,
                    exerciseName = exercise.name,
                    category = exercise.category
                ),
                sets
            )
        ),
        mapOf(exercise.stableKey to exercise)
    )

    private fun relation(
        id: String,
        objective: BadmintonObjective,
        level: BadmintonObjectiveTransferLevel
    ) = CanonicalBadmintonObjectiveRelation(id, "drill", objective, level, "TEST", setOf(id), "Test fixture")

    private fun set(rpe: Double, weight: Double, reps: Int, seconds: Int) =
        WorkoutSet(
            entryId = 0,
            setIndex = 0,
            reps = reps,
            weightKg = weight,
            seconds = seconds,
            confirmed = true,
            rpe = rpe
        )
}
