package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRuleTablesTest {
    @Test
    fun threeWeekTableMatchesRule() {
        val table = LegacyAutoRuleTables.intensityTable(3)

        assertEquals(3, table.size)
        assertEquals(LegacyAutoIntensityLabel.HIGH_LOW, table[0][LegacyAutoMainArea.LOWER_ANTERIOR])
        assertEquals(LegacyAutoIntensityLabel.LOW_HIGH, table[0][LegacyAutoMainArea.SHOULDER])
        assertEquals(LegacyAutoIntensityLabel.HIGH_LOW, table[1][LegacyAutoMainArea.BACK])
        assertEquals(LegacyAutoIntensityLabel.HIGH_LOW, table[2][LegacyAutoMainArea.CHEST])
    }

    @Test
    fun fourWeekTableHasFinalDeload() {
        val table = LegacyAutoRuleTables.intensityTable(4)

        assertEquals(4, table.size)
        assertTrue(table[3].values.all { it == LegacyAutoIntensityLabel.DELOAD })
    }

    @Test
    fun fiveWeekTableIsExplicitlyUnfolded() {
        val table = LegacyAutoRuleTables.intensityTable(5)

        assertEquals(5, table.size)
        assertEquals(LegacyAutoIntensityLabel.LOW_HIGH, table[0][LegacyAutoMainArea.SHOULDER])
        assertEquals(LegacyAutoIntensityLabel.LOW_HIGH, table[1][LegacyAutoMainArea.SHOULDER])
        assertTrue(table[2].values.all { it == LegacyAutoIntensityLabel.DELOAD })
        assertEquals(LegacyAutoIntensityLabel.LOW_HIGH, table[3][LegacyAutoMainArea.LOWER_ANTERIOR])
        assertEquals(LegacyAutoIntensityLabel.HIGH_LOW, table[3][LegacyAutoMainArea.SHOULDER])
        assertEquals(LegacyAutoIntensityLabel.MEDIUM_LOW, table[3][LegacyAutoMainArea.CHEST])
        assertEquals(LegacyAutoIntensityLabel.MEDIUM_MEDIUM, table[3][LegacyAutoMainArea.LOWER_POSTERIOR])
        assertEquals(LegacyAutoIntensityLabel.MEDIUM_MEDIUM, table[3][LegacyAutoMainArea.BACK])
    }

    @Test
    fun sixSevenAndEightWeekTablesFollowDurationRules() {
        val six = LegacyAutoRuleTables.intensityTable(6)
        val seven = LegacyAutoRuleTables.intensityTable(7)
        val eight = LegacyAutoRuleTables.intensityTable(8)

        assertEquals(LegacyAutoIntensityLabel.MEDIUM_LOW, six[3][LegacyAutoMainArea.LOWER_POSTERIOR])
        assertEquals(LegacyAutoIntensityLabel.MEDIUM_LOW, six[4][LegacyAutoMainArea.LOWER_POSTERIOR])
        assertEquals(LegacyAutoIntensityLabel.MEDIUM_LOW, six[5][LegacyAutoMainArea.LOWER_POSTERIOR])
        assertTrue(seven[6].values.all { it == LegacyAutoIntensityLabel.DELOAD })
        assertEquals(eight[0], eight[4])
        assertEquals(eight[1], eight[5])
        assertTrue(eight[3].values.all { it == LegacyAutoIntensityLabel.DELOAD })
        assertTrue(eight[7].values.all { it == LegacyAutoIntensityLabel.DELOAD })
    }

    @Test
    fun slotCapsMatchSupportedDurations() {
        assertEquals(3, LegacyAutoRuleTables.slotCaps(30).totalSlots)
        assertEquals(4, LegacyAutoRuleTables.slotCaps(45).totalSlots)
        assertEquals(5, LegacyAutoRuleTables.slotCaps(60).totalSlots)
        assertEquals(2, LegacyAutoRuleTables.slotCaps(60).mainCap)
    }
}
