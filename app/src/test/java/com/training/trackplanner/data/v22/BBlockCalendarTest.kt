package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Test

class BBlockCalendarTest {
    @Test fun fullWeekRecoveryInsertsEmptyCalendarWeek() {
        val p = BBlockProgramDefinition(listOf(BBlockDefinition(4), BBlockDefinition(4)), listOf(7))
        val cal = BBlockCalendar(p)
        assertEquals(63, cal.durationDays)
        assertEquals(9, cal.calendarWeeks)
        assertEquals(BCalendarSlot(1, 1), cal.slot(0, 0, 1))
        assertEquals(BCalendarSlot(6, 1), cal.slot(1, 0, 1))
    }

    @Test fun threeDayRecoveryMovesSecondBlockToThursdayNotMonday() {
        val cal = BBlockCalendar(BBlockProgramDefinition(
            listOf(BBlockDefinition(3), BBlockDefinition(3)), listOf(3)
        ))
        assertEquals(45, cal.durationDays)
        assertEquals(7, cal.calendarWeeks)
        assertEquals(BCalendarSlot(4, 4), cal.slot(1, 0, 1))
        assertEquals(BCalendarSlot(4, 7), cal.slot(1, 0, 4))
        assertEquals(BCalendarSlot(5, 1), cal.slot(1, 0, 5))
    }
}
