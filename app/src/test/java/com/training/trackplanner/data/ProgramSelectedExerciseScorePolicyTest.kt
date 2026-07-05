package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramSelectedExerciseScorePolicyTest {
    private val policy = ProgramSelectedExerciseScorePolicy()

    @Test
    fun exactSelectedMainExercisesReceiveFortyPercentFinalScoreBoost() {
        val boostTargets = listOf(
            "barbell_back_squat" to "Back squat",
            "barbell_deadlift" to "Deadlift",
            "pull_up" to "Pull-up",
            "ex_32219f7a" to "Overhead press",
            "ex_8e1b313e" to "Half-kneeling one-arm press"
        )

        boostTargets.forEachIndexed { index, (stableKey, name) ->
            val result = policy.adjust(10.0, candidate(index + 1, stableKey, name))

            assertEquals("$stableKey should receive exact selected-main boost", 14.0, result.score, 0.0001)
            assertTrue(result.selectedMainBoostApplied)
            assertFalse(result.captainChairPenaltyApplied)
        }
    }

    @Test
    fun broadMovementFamiliesDoNotReceiveSelectedMainBoost() {
        val excludedBroadTargets = listOf(
            "dumbbell_rdl" to "Dumbbell RDL",
            "single_leg_rdl" to "Single-leg RDL",
            "cable_row" to "Cable row",
            "lat_pulldown" to "Lat pulldown",
            "barbell_bench_press" to "Bench press",
            "landmine_press" to "Landmine press",
            "leg_press" to "Leg press",
            "bulgarian_split_squat" to "Bulgarian split squat",
            "goblet_squat" to "Goblet squat",
            "box_squat" to "Box squat",
            "front_squat" to "Front squat",
            "trap_bar_deadlift" to "Trap-bar deadlift",
            "dead_bug" to "Dead bug",
            "pallof_press" to "Pallof press",
            "side_plank" to "Side plank"
        )

        excludedBroadTargets.forEachIndexed { index, (stableKey, name) ->
            val result = policy.adjust(10.0, candidate(index + 100, stableKey, name))

            assertEquals("$stableKey should not receive broad-category boost", 10.0, result.score, 0.0001)
            assertFalse(result.selectedMainBoostApplied)
            assertFalse(result.captainChairPenaltyApplied)
        }
    }

    @Test
    fun onlyCaptainChairLegRaiseReceivesThirtyPercentPenalty() {
        val captainChair = policy.adjust(10.0, candidate(201, "ex_a345e30b", "Captain chair leg raise"))

        assertEquals(7.0, captainChair.score, 0.0001)
        assertFalse(captainChair.selectedMainBoostApplied)
        assertTrue(captainChair.captainChairPenaltyApplied)

        val nonTargets = listOf(
            "ex_b3f447be" to "Hanging leg raise",
            "dead_bug" to "Dead bug",
            "pallof_press" to "Pallof press",
            "side_plank" to "Side plank",
            "crunch" to "Crunch"
        )
        nonTargets.forEachIndexed { index, (stableKey, name) ->
            val result = policy.adjust(10.0, candidate(index + 300, stableKey, name))

            assertEquals("$stableKey should not receive captain-chair penalty", 10.0, result.score, 0.0001)
            assertFalse(result.selectedMainBoostApplied)
            assertFalse(result.captainChairPenaltyApplied)
        }
    }

    private fun candidate(id: Int, stableKey: String, name: String): ProgramCandidate {
        val exercise = Exercise(
            id = id.toLong(),
            name = name,
            category = "strength",
            stableKey = stableKey,
            equipment = "BARBELL",
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        )
        return ProgramCandidate(
            exercise = exercise,
            metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                stableKey = stableKey,
                exerciseName = name,
                planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
            ),
            canonical = true
        )
    }
}
