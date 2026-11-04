package com.training.trackplanner.data.v22

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BBlockRelationScoringTest {
    private fun score(
        relation: BBlockRelation,
        prior: Boolean = false,
        deficits: Double = 2.0,
        muscleGap: Double = 0.5,
        primary: Double = 1.0,
        known: Boolean = true
    ) = BBlockRelationScoring.score(
        primaryGoal = primary, deficits = deficits,
        inPreviousBlock = prior, isKnownEquipment = known,
        relation = relation, muscleGap = muscleGap
    )

    @Test fun independentNeverReceivesAnyPlannedGapBonus() {
        assertEquals(score(BBlockRelation.INDEPENDENT, deficits = 0.0, muscleGap = 0.0),
            score(BBlockRelation.INDEPENDENT, deficits = 10.0, muscleGap = 1.0), 0.000001)
        assertEquals(score(BBlockRelation.INDEPENDENT, prior = false),
            score(BBlockRelation.INDEPENDENT, prior = true), 0.000001)
    }

    @Test fun complementOnlyRewardsGoalAndMuscleDeficitsWhenSelected() {
        val withoutGap = score(BBlockRelation.COMPLEMENT_PLANNED_GAPS,
            deficits = 0.0, muscleGap = 0.0)
        val withGap = score(BBlockRelation.COMPLEMENT_PLANNED_GAPS,
            deficits = 2.0, muscleGap = 0.9)
        assertTrue(withGap > withoutGap)
        assertEquals(score(BBlockRelation.INDEPENDENT, deficits = 0.0, muscleGap = 0.0),
            withoutGap, 0.000001)
    }

    @Test fun developmentOnlyRewardsPreviousExerciseContinuity() {
        assertTrue(score(BBlockRelation.DEVELOP_PREVIOUS, prior = true) >
            score(BBlockRelation.DEVELOP_PREVIOUS, prior = false))
        assertEquals(score(BBlockRelation.DEVELOP_PREVIOUS, prior = true, deficits = 0.0, muscleGap = 0.0),
            score(BBlockRelation.DEVELOP_PREVIOUS, prior = true, deficits = 9.0, muscleGap = 1.0),
            0.000001)
    }

    @Test fun historicalPrepareNextIsIdenticalToIndependent() {
        assertEquals(score(BBlockRelation.INDEPENDENT, deficits = 4.0, muscleGap = 0.8),
            score(BBlockRelation.PREPARE_NEXT, deficits = 4.0, muscleGap = 0.8), 0.000001)
        assertEquals(score(BBlockRelation.INDEPENDENT, primary = 0.5),
            score(BBlockRelation.PREPARE_NEXT, primary = 0.5), 0.000001)
    }

    @Test fun currentGoalAndPersonalExerciseHistoryRemainRelevantAcrossAllThreeModes() {
        val modes = listOf(BBlockRelation.INDEPENDENT,
            BBlockRelation.COMPLEMENT_PLANNED_GAPS, BBlockRelation.DEVELOP_PREVIOUS)
        modes.forEach { relation ->
            assertTrue(score(relation, primary = 1.0) > score(relation, primary = 0.0))
            assertTrue(score(relation, known = true) > score(relation, known = false))
        }
    }
}
