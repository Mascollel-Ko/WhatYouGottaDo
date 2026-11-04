package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BBlockConfidenceReviewTest {
    private fun plan(sets56: Int): BBalanced12WeekPlan {
        val item = V22ExercisePlan(
            "Squat", "barbell_back_squat", "근력운동", "MAIN", 3,
            List(3) { V22ObservedSet(null, 5, null, null, "", "UNSET") },
            historicalSets28d = sets56, historicalSets56d = sets56
        )
        val session = V22Session(1, listOf(item), 3)
        val goal = BBalancedGoalTarget(
            goal = BBalancedGoal.STRENGTH, unit = "SETS", importance = 3,
            importanceSource = "AUTO", researchReference = null, researchReferenceSource = null,
            recentHistory = null, personalResponseCandidate = null,
            confidence = BBalancedConfidence(.6, 0.0, emptyList()),
            developmentTarget = null, maintenanceFloor = null, maintenanceSource = null,
            numericAuthority = BBalancedNumericAuthority.DIRECTION_ONLY, reasons = emptyList()
        )
        return BBalanced12WeekPlan(
            "B_BALANCED_BLOCK_PROGRAM_V1",
            V22PlanInput("", "2026-11-04", 3, 1, "B", null, 60),
            listOf(goal), listOf(BBalancedBlock(1, 1, 3, BBalancedGoal.STRENGTH,
                emptyList(), listOf(session), emptyList())),
            listOf(V22Week(1, 1, listOf(session), 3)),
            BBalancedSearchStats(1, 1, 0, 0.005, "TEST"),
            "TEST", emptyList(), "{}"
        )
    }
    @Test fun incompleteHistoryTriggersReviewForPeriodizedBlock() {
        val choice = BBlockProgramDefinition(blocks = listOf(
            BBlockDefinition(3, pattern = BVolumePattern.PROGRESSIVE)
        ))
        val result = BBlockConfidenceReview.evaluate(plan(3), choice)
        assertTrue(result.requiresApproval)
        assertEquals(setOf("barbell_back_squat"), result.unreliableExerciseKeys)
        assertFalse(BBlockConfidenceReview.evaluate(plan(10), choice).requiresApproval)
    }
    @Test fun steadyBlockDoesNotDemandPeriodizationApproval() {
        assertFalse(BBlockConfidenceReview.evaluate(plan(0), BBlockProgramDefinition()).requiresApproval)
    }
}
