package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.data.InitialUserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueBaselineCalculatorTest {
    @Test
    fun recentFourteenDaysReceiveHigherWeight() {
        val recent = List(14) { FatigueAxisValues().map { 80.0 } }
        val previous = List(14) { FatigueAxisValues().map { 20.0 } }
        val seed = FatigueBaselineSeed(FatigueAxisValues().map { 50.0 }, 50.0)

        val baseline = FatigueBaselineCalculator.effectiveBaseline(recent, previous, seed, 28)

        assertEquals(59.0, baseline.axes.highForceNeural, 0.0001)
        assertEquals(0.0, baseline.seedWeight, 0.0001)
    }

    @Test
    fun profileSeedPreventsMissingBaselineAndFadesOut() {
        val seed = InitialProfileBaselineSeeder.seed(
            InitialUserProfile(strengthSessionsPerWeek = 4.0, badmintonSessionsPerWeek = 2.0)
        )
        val empty = FatigueBaselineCalculator.effectiveBaseline(emptyList(), emptyList(), seed, 0)
        val observed = List(28) { FatigueAxisValues().map { 70.0 } }
        val mature = FatigueBaselineCalculator.effectiveBaseline(observed.takeLast(14), observed.take(14), seed, 28)

        assertTrue(empty.axes.highForceNeural > 0.0)
        assertEquals(1.0, empty.seedWeight, 0.0001)
        assertEquals(0.0, mature.seedWeight, 0.0001)
        assertEquals(70.0, mature.axes.highForceNeural, 0.0001)
    }
}
