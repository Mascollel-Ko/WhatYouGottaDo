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

    @Test
    fun fatigueLabelsUseRelaxedWarningThresholds() {
        assertEquals(FatigueReadinessLabel.NORMAL, FatigueLabelResolver.label(74))
        assertEquals(FatigueReadinessLabel.ELEVATED, FatigueLabelResolver.label(75))
        assertEquals(FatigueReadinessLabel.ELEVATED, FatigueLabelResolver.label(86))
        assertEquals(FatigueReadinessLabel.CAUTION, FatigueLabelResolver.label(87))
        assertEquals(FatigueReadinessLabel.CAUTION, FatigueLabelResolver.label(97))
        assertEquals(FatigueReadinessLabel.HIGH_FATIGUE, FatigueLabelResolver.label(98))
    }

    @Test
    fun restRecommendationUsesRelaxedHighThresholds() {
        assertFalse(FatigueLabelResolver.shouldRecommendRest(97, List(6) { 70 }, false))
        assertFalse(FatigueLabelResolver.shouldRecommendRest(70, List(6) { 91 }, false))
        assertEquals(true, FatigueLabelResolver.shouldRecommendRest(98, List(6) { 70 }, false))
        assertEquals(true, FatigueLabelResolver.shouldRecommendRest(70, List(6) { 92 }, false))
    }
}
