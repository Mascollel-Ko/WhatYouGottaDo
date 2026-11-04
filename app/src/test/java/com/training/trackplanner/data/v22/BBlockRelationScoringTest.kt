package com.training.trackplanner.data.v22

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BBlockRelationScoringTest {
    private fun score(relation: BBlockRelation, prior: Boolean = false, future: Double = 0.0) =
        BBlockRelationScoring.score(1.0, 2.0, future, prior, true, relation)

    @Test fun complementPlannedGapsGivesLargerDeficiencyBonus() {
        assertTrue(score(BBlockRelation.COMPLEMENT_PLANNED_GAPS) >
            score(BBlockRelation.INDEPENDENT))
    }

    @Test fun developingPriorBlockRewardsContinuityWithoutForcingIt() {
        assertTrue(score(BBlockRelation.DEVELOP_PREVIOUS, true) >
            score(BBlockRelation.DEVELOP_PREVIOUS, false))
    }

    @Test fun preparationLooksAheadWhileIndependentDoesNot() {
        assertTrue(score(BBlockRelation.PREPARE_NEXT, future = 2.0) >
            score(BBlockRelation.PREPARE_NEXT, future = 0.0))
        assertEquals(score(BBlockRelation.INDEPENDENT, future = 2.0),
            score(BBlockRelation.INDEPENDENT, future = 0.0), 0.0001)
    }
}
