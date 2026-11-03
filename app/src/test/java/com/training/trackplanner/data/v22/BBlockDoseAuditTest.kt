package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Test

class BBlockDoseAuditTest {
    private fun week(n: Int, sets: Int, reps: Int, loadSteps: Int): V22Week {
        val item = V22ExercisePlan(
            exercise = "Squat", stableKey = "barbell_squat", category = "근력운동",
            role = "TEST", setCount = sets,
            sets = List(sets) { V22ObservedSet(null, reps, null, null, "", "UNSET") },
            historicalSets28d = 0, historicalSets56d = 0,
            plannedProgression = "LOAD_STEPS=" + loadSteps
        )
        return V22Week(n, 1, listOf(V22Session(1, listOf(item), sets)), sets)
    }

    @Test fun pendingKilogramsAreNotClaimedAsVerifiedVolumeIncreases() {
        val block = BBalancedBlock(1, 1, 3, BBalancedGoal.STRENGTH,
            emptyList(), emptyList(), emptyList())
        val spec = BBlockProgramDefinition(listOf(BBlockDefinition(3, pattern = BVolumePattern.WAVE)))
        val audit = BBlockDoseAudit.compute(listOf(
            week(1, 3, 5, 0),
            week(2, 3, 5, 2),
            week(3, 3, 5, 1)
        ), listOf(block), spec)
        assertEquals("LOAD_CALIBRATION_REQUIRED", audit.getJSONObject(1).getString("status"))
        assertEquals(2, audit.getJSONObject(1).getInt("expectedStage"))
    }

    @Test fun noFeasibleChangeIsVisibleAsReviewNotManufacturedIncrease() {
        val block = BBalancedBlock(1, 1, 2, BBalancedGoal.HYPERTROPHY,
            emptyList(), emptyList(), emptyList())
        val spec = BBlockProgramDefinition(listOf(BBlockDefinition(2, pattern = BVolumePattern.PROGRESSIVE)))
        val audit = BBlockDoseAudit.compute(listOf(week(1, 3, 12, 0), week(2, 3, 12, 0)),
            listOf(block), spec)
        assertEquals("PATTERN_NOT_REALIZED_USER_REVIEW", audit.getJSONObject(1).getString("status"))
    }
}
