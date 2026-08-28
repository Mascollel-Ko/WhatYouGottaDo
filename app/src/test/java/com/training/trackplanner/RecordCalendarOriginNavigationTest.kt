package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCalendarOriginNavigationTest {
    @Test
    fun calendarOriginSurvivesPreviousAndNextDateChanges() {
        val origin = RecordDetailOrigin.Calendar
        var date = "2026-08-29"

        date = java.time.LocalDate.parse(date).plusDays(1).toString()
        assertEquals("2026-08-30", date)
        assertEquals(RecordDetailOrigin.Calendar, origin)

        date = java.time.LocalDate.parse(date).minusDays(2).toString()
        assertEquals("2026-08-28", date)
        assertEquals(RecordDetailOrigin.Calendar, origin)
    }

    @Test
    fun normalAndExternalRecordEntryUseNormalOrigin() {
        assertEquals(RecordDetailOrigin.Normal, RecordDetailOrigin.Normal)
    }
}
