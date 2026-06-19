package com.training.trackplanner.analysis.fatigue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverallFatigueIndexCalculatorTest {
    @Test
    fun ofiUsesMeanMaxAndHighAxisPenalty() {
        val score = OverallFatigueIndexCalculator.calculate(listOf(80, 80, 60, 60, 60, 60))

        assertEquals(74, score)
    }

    @Test
    fun sixtyIsNormalAndDoesNotRecommendRest() {
        assertEquals(FatigueReadinessLabel.NORMAL, FatigueLabelResolver.label(60))
        assertFalse(FatigueLabelResolver.shouldRecommendRest(60, List(6) { 60 }, false))
    }
}
