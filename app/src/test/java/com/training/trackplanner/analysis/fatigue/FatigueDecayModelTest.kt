package com.training.trackplanner.analysis.fatigue

import org.junit.Assert.assertEquals
import org.junit.Test

class FatigueDecayModelTest {
    @Test
    fun decayTablesMatchConfiguredDurations() {
        assertEquals(0.35, FatigueDecayModel.factor("SHORT", 1), 0.0001)
        assertEquals(0.35, FatigueDecayModel.factor("MEDIUM", 2), 0.0001)
        assertEquals(0.35, FatigueDecayModel.factor("LONG", 3), 0.0001)
        assertEquals(0.40, FatigueDecayModel.factor("VERY_LONG", 4), 0.0001)
    }

    @Test
    fun highJointStressCannotDecayFasterThanMedium() {
        val duration = FatigueDecayModel.atLeast("SHORT", "MEDIUM")

        assertEquals("MEDIUM", duration)
        assertEquals(0.60, FatigueDecayModel.factor(duration, 1), 0.0001)
    }
}
