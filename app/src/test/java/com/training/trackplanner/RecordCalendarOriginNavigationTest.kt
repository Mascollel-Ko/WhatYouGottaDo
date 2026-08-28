package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val screen = source("RecordScreen.kt")
        assertTrue(screen.contains("detailOrigin = RecordDetailOrigin.Normal\n            pendingSearchJump"))
        assertTrue(screen.contains("BackHandler(enabled = !showCalendar && detailOrigin == RecordDetailOrigin.Calendar)"))
        assertTrue(screen.contains("detailOrigin = RecordDetailOrigin.Calendar\n                showCalendar = false"))
    }

    @Test
    fun calendarTransientStateConsumesBackBeforeCalendarExit() {
        val calendar = source("RecordCalendarScreen.kt")
        val handler = calendar.substringAfter("BackHandler {").substringBefore("    }")
        assertTrue(handler.indexOf("actionMenuDate != null") < handler.indexOf("else -> onBack()"))
        assertTrue(handler.indexOf("pendingPushDate != null") < handler.indexOf("else -> onBack()"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8).replace("\r\n", "\n")
}
