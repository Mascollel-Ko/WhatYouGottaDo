package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRuleTablesTest {
    @Test
    fun threeWeekTableMatchesRule() {
        val table = ProgramRuleTables.intensityTable(3)

        assertEquals(3, table.size)
        assertEquals(ProgramIntensityLabel.HIGH_LOW, table[0][ProgramMainArea.LOWER_ANTERIOR])
        assertEquals(ProgramIntensityLabel.LOW_HIGH, table[0][ProgramMainArea.SHOULDER])
        assertEquals(ProgramIntensityLabel.HIGH_LOW, table[1][ProgramMainArea.BACK])
        assertEquals(ProgramIntensityLabel.HIGH_LOW, table[2][ProgramMainArea.CHEST])
    }

    @Test
    fun fourWeekTableHasFinalDeload() {
        val table = ProgramRuleTables.intensityTable(4)

        assertEquals(4, table.size)
        assertTrue(table[3].values.all { it == ProgramIntensityLabel.DELOAD })
    }

    @Test
    fun fiveWeekTableIsExplicitlyUnfolded() {
        val table = ProgramRuleTables.intensityTable(5)

        assertEquals(5, table.size)
        assertEquals(ProgramIntensityLabel.LOW_HIGH, table[0][ProgramMainArea.SHOULDER])
        assertEquals(ProgramIntensityLabel.LOW_HIGH, table[1][ProgramMainArea.SHOULDER])
        assertTrue(table[2].values.all { it == ProgramIntensityLabel.DELOAD })
        assertEquals(ProgramIntensityLabel.LOW_HIGH, table[3][ProgramMainArea.LOWER_ANTERIOR])
        assertEquals(ProgramIntensityLabel.HIGH_LOW, table[3][ProgramMainArea.SHOULDER])
        assertEquals(ProgramIntensityLabel.MEDIUM_LOW, table[3][ProgramMainArea.CHEST])
        assertEquals(ProgramIntensityLabel.MEDIUM_MEDIUM, table[3][ProgramMainArea.LOWER_POSTERIOR])
        assertEquals(ProgramIntensityLabel.MEDIUM_MEDIUM, table[3][ProgramMainArea.BACK])
    }

    @Test
    fun sixSevenAndEightWeekTablesFollowDurationRules() {
        val six = ProgramRuleTables.intensityTable(6)
        val seven = ProgramRuleTables.intensityTable(7)
        val eight = ProgramRuleTables.intensityTable(8)

        assertEquals(ProgramIntensityLabel.MEDIUM_LOW, six[3][ProgramMainArea.LOWER_POSTERIOR])
        assertEquals(ProgramIntensityLabel.MEDIUM_LOW, six[4][ProgramMainArea.LOWER_POSTERIOR])
        assertEquals(ProgramIntensityLabel.MEDIUM_LOW, six[5][ProgramMainArea.LOWER_POSTERIOR])
        assertTrue(seven[6].values.all { it == ProgramIntensityLabel.DELOAD })
        assertEquals(eight[0], eight[4])
        assertEquals(eight[1], eight[5])
        assertTrue(eight[3].values.all { it == ProgramIntensityLabel.DELOAD })
        assertTrue(eight[7].values.all { it == ProgramIntensityLabel.DELOAD })
    }

    @Test
    fun slotCapsMatchSupportedDurations() {
        assertEquals(3, ProgramRuleTables.slotCaps(30).totalSlots)
        assertEquals(4, ProgramRuleTables.slotCaps(45).totalSlots)
        assertEquals(5, ProgramRuleTables.slotCaps(60).totalSlots)
        assertEquals(2, ProgramRuleTables.slotCaps(60).mainCap)
    }
}
